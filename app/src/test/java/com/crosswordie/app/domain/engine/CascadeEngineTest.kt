package com.crosswordie.app.domain.engine

import com.crosswordie.app.domain.generator.CrosswordGenerator
import com.crosswordie.app.domain.generator.WordIndex
import com.crosswordie.app.domain.model.LetterState
import com.crosswordie.app.domain.model.SlotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CascadeEngineTest {

    companion object {
        private lateinit var wordIndex: WordIndex
        private lateinit var generator: CrosswordGenerator

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val assetFile = File("src/main/assets/words_target.txt").let {
                if (it.exists()) it else File("app/src/main/assets/words_target.txt")
            }
            val words = assetFile.readLines()
            wordIndex = WordIndex(words, words)
            generator = CrosswordGenerator(wordIndex)
        }
    }

    @Test
    fun `wordleEvaluator handles duplicate letter frequencies and locked hints accurately`() {
        // Target has one 'E' at index 4 (CRANE). Guess is EERIE (three 'E's).
        // Index 4 is exact match -> CORRECT; index 0 and 1 'E' should be ABSENT!
        val row1 = WordleEvaluator.evaluateGuess(
            guess = "EERIE",
            targetWord = "CRANE"
        )
        assertEquals(LetterState.ABSENT, row1.tiles[0].state)
        assertEquals(LetterState.ABSENT, row1.tiles[1].state)
        assertEquals(LetterState.PRESENT, row1.tiles[2].state) // 'R' is in CRANE at pos 1
        assertEquals(LetterState.ABSENT, row1.tiles[3].state)
        assertEquals(LetterState.CORRECT, row1.tiles[4].state) // 'E' at pos 4

        // Target has two 'E's (SPEED). Guess is EERIE.
        // None of EERIE's 'E's are at pos 2 or 3, so the first two 'E's in EERIE get PRESENT, and the third gets ABSENT.
        val row2 = WordleEvaluator.evaluateGuess(
            guess = "EERIE",
            targetWord = "SPEED"
        )
        assertEquals(LetterState.PRESENT, row2.tiles[0].state)
        assertEquals(LetterState.PRESENT, row2.tiles[1].state)
        assertEquals(LetterState.ABSENT, row2.tiles[4].state)

        // Pre-filled locked hint at index 0 renders as LOCKED_HINT when matched
        val row3 = WordleEvaluator.evaluateGuess(
            guess = "SMILE",
            targetWord = "STONE",
            lockedIndices = mapOf(0 to 'S')
        )
        assertEquals(LetterState.LOCKED_HINT, row3.tiles[0].state)
        assertEquals(LetterState.CORRECT, row3.tiles[4].state)
    }

    @Test
    fun `solving a word locks cells and cascades pre-filled green hints to intersecting words`() {
        val board = generator.generate(seed = 42L)
        val firstSlot = board.activeSlot!!
        val firstCoords = firstSlot.coords.toSet()

        // Find a perpendicular slot that intersects with firstSlot
        val intersectingSlot = board.slots.first { other ->
            other.id != firstSlot.id && other.coords.any { it in firstCoords }
        }
        val sharedCoord = intersectingSlot.coords.first { it in firstCoords }
        val expectedChar = firstSlot.targetWord[firstSlot.indexOfCoord(sharedCoord)]
        val indexInIntersecting = intersectingSlot.indexOfCoord(sharedCoord)

        // Solve firstSlot on guess 1
        val result = CascadeEngine.submitGuess(board, firstSlot.targetWord)
        assertEquals(CascadeEngine.GuessOutcome.SLOT_SOLVED, result.outcome)

        val updatedBoard = result.updatedBoard
        val updatedFirst = updatedBoard.slotById(firstSlot.id)!!
        assertEquals(SlotStatus.SOLVED, updatedFirst.status)

        // Verify the crossword cell is locked
        val lockedCell = updatedBoard.cellAt(sharedCoord)!!
        assertEquals(expectedChar, lockedCell.lockedChar)
        assertFalse(lockedCell.isRevealedByFailure)

        // Verify the intersecting perpendicular slot received the pre-filled green lockedIndex!
        val updatedIntersecting = updatedBoard.slotById(intersectingSlot.id)!!
        assertEquals(
            "Intersecting slot ${intersectingSlot.id} should have index $indexInIntersecting locked to $expectedChar",
            expectedChar,
            updatedIntersecting.lockedIndices[indexInIntersecting]
        )
    }

    @Test
    fun `failing 5 guesses reveals word with strike and still cascades intersections to prevent soft-lock`() {
        var board = generator.generate(seed = 1001L)
        val firstSlot = board.activeSlot!!
        val firstCoords = firstSlot.coords.toSet()
        val intersectingSlot = board.slots.first { other ->
            other.id != firstSlot.id && other.coords.any { it in firstCoords }
        }
        val sharedCoord = intersectingSlot.coords.first { it in firstCoords }
        val expectedChar = firstSlot.targetWord[firstSlot.indexOfCoord(sharedCoord)]
        val indexInIntersecting = intersectingSlot.indexOfCoord(sharedCoord)

        // Pick a dummy wrong word of the same length
        val wrongWord = "Z".repeat(firstSlot.length).let {
            if (it == firstSlot.targetWord) "Q".repeat(firstSlot.length) else it
        }

        // Submit 4 wrong guesses -> slot stays IN_PROGRESS
        repeat(4) {
            val res = CascadeEngine.submitGuess(board, wrongWord)
            assertEquals(CascadeEngine.GuessOutcome.CONTINUE_SLOT, res.outcome)
            board = res.updatedBoard
        }

        // Submit 5th wrong guess -> triggers SLOT_REVEALED_FAILED
        val fifthResult = CascadeEngine.submitGuess(board, wrongWord)
        assertEquals(CascadeEngine.GuessOutcome.SLOT_REVEALED_FAILED, fifthResult.outcome)
        assertEquals(0, fifthResult.pointsEarned)

        val afterFailBoard = fifthResult.updatedBoard
        assertEquals(1, afterFailBoard.strikes)

        val failedSlot = afterFailBoard.slotById(firstSlot.id)!!
        assertEquals(SlotStatus.REVEALED_FAILED, failedSlot.status)

        // Crucially, intersecting slot STILL receives the locked hint so the board is never soft-locked
        val updatedIntersecting = afterFailBoard.slotById(intersectingSlot.id)!!
        assertEquals(expectedChar, updatedIntersecting.lockedIndices[indexInIntersecting])
    }
}
