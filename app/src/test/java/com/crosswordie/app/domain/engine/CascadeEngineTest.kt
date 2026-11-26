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

    @Test
    fun `easy difficulty starter hint persists across multiple guesses on the same slot`() {
        val easyBoard = generator.generate(
            seed = 777L,
            difficulty = com.crosswordie.app.domain.model.DifficultyLevel.EASY
        )
        val firstSlot = easyBoard.activeSlot!!
        assertTrue("Easy mode first slot should start with 1 locked hint", firstSlot.lockedIndices.isNotEmpty())
        val (lockedIdx, lockedChar) = firstSlot.lockedIndices.entries.first()

        // Build a wrong guess that keeps the lockedChar at lockedIdx
        val chars = CharArray(firstSlot.length) { i ->
            if (i == lockedIdx) lockedChar else 'Z'
        }
        var wrongGuess = String(chars)
        if (wrongGuess == firstSlot.targetWord) {
            val altChars = CharArray(firstSlot.length) { i ->
                if (i == lockedIdx) lockedChar else 'Q'
            }
            wrongGuess = String(altChars)
        }

        // Submit 2 wrong guesses (reaching row 3, just like in the user screenshot)
        val res1 = CascadeEngine.submitGuess(easyBoard, wrongGuess)
        val res2 = CascadeEngine.submitGuess(res1.updatedBoard, wrongGuess)
        val slotAfter2Guesses = res2.updatedBoard.activeSlot!!

        assertEquals(2, slotAfter2Guesses.guesses.size)
        assertEquals(lockedChar, slotAfter2Guesses.lockedIndices[lockedIdx])
    }

    @Test
    fun `fewer guesses award significantly higher points and 1st try in Endless earns bonus hint`() {
        val diff = com.crosswordie.app.domain.model.DifficultyLevel.MEDIUM
        val pts1 = CascadeEngine.calculateSolvePoints(1, 5, diff)
        val pts2 = CascadeEngine.calculateSolvePoints(2, 5, diff)
        val pts3 = CascadeEngine.calculateSolvePoints(3, 5, diff)
        val pts4 = CascadeEngine.calculateSolvePoints(4, 5, diff)
        val pts5 = CascadeEngine.calculateSolvePoints(5, 5, diff)

        assertTrue("1st try ($pts1) > 2nd try ($pts2)", pts1 > pts2)
        assertTrue("2nd try ($pts2) > 3rd try ($pts3)", pts2 > pts3)
        assertTrue("3rd try ($pts3) > 4th try ($pts4)", pts3 > pts4)
        assertTrue("4th try ($pts4) > 5th try ($pts5)", pts4 > pts5)
        assertTrue("1st try should earn over 8x 5th try points", pts1 >= pts5 * 8)

        // Verify 1st-try solve in Endless Mode earns +1 Hint
        val endlessBoard = generator.generate(
            seed = 888L,
            gameMode = com.crosswordie.app.domain.model.GameMode.ENDLESS,
            difficulty = diff
        )
        val startHints = endlessBoard.hintsRemaining
        val firstWord = endlessBoard.activeSlot!!.targetWord
        val res = CascadeEngine.submitGuess(endlessBoard, firstWord)
        assertTrue("1st try solve in Endless Mode should earn at least +1 Hint", res.bonusHintsEarned >= 1)
        assertTrue(res.updatedBoard.hintsRemaining > startHints)
    }

    @Test
    fun `three word hint includes target word, free letter unlocks char, and give up cascades`() {
        val board = generator.generate(seed = 999L)
        val slot = board.activeSlot!!

        // 1. 3-Word Hint
        val threeWords = CascadeEngine.generateThreeWordHintOptions(
            slot = slot,
            wordIndex = wordIndex,
            boardTargetWords = board.slots.map { it.targetWord }.toSet(),
            seed = board.seed
        )
        assertEquals(3, threeWords.size)
        assertTrue("3-Word Hint must include the exact targetWord", slot.targetWord in threeWords)
        assertEquals("All 3 candidate words must be distinct", 3, threeWords.distinct().size)

        // 2. Free Letter Hint skips already-known Green letters from previous guesses!
        // Simulate a previous guess where index 0 (an intersection) was ALREADY guessed Green by the player
        val firstCharKnownGuess = CharArray(slot.length) { i ->
            if (i == 0) slot.targetWord[0] else (if (slot.targetWord[i] != 'Z') 'Z' else 'Q')
        }.concatToString()
        val afterPartialGreenGuess = CascadeEngine.submitGuess(board, firstCharKnownGuess).updatedBoard
        val slotBeforeFreeLetter = afterPartialGreenGuess.slotById(slot.id)!!
        assertFalse("Index 0 was guessed Green, not pre-locked yet", slotBeforeFreeLetter.lockedIndices.containsKey(0))

        val freeLetterRes = CascadeEngine.useFreeLetterHint(afterPartialGreenGuess)!!
        assertEquals(afterPartialGreenGuess.hintsRemaining - 1, freeLetterRes.updatedBoard.hintsRemaining)
        val updatedSlot = freeLetterRes.updatedBoard.slotById(slot.id)!!
        val newlyLockedIndex = updatedSlot.lockedIndices.keys.first { it !in slotBeforeFreeLetter.lockedIndices.keys }
        assertTrue(
            "Free Letter must NOT waste a hint on index 0 which the player already found Green",
            newlyLockedIndex != 0
        )

        // 3. Give Up Word
        val giveUpRes = CascadeEngine.giveUpActiveWord(freeLetterRes.updatedBoard)
        assertEquals(CascadeEngine.GuessOutcome.SLOT_REVEALED_FAILED, giveUpRes.outcome)
        assertEquals(0, giveUpRes.pointsEarned)
        assertEquals(1, giveUpRes.updatedBoard.strikes)
        assertEquals(SlotStatus.REVEALED_FAILED, giveUpRes.updatedBoard.slotById(slot.id)!!.status)
    }

    @Test
    fun `expanded vocabulary includes names of people, places, and things`() {
        assertTrue(wordIndex.isValidGuess("PARIS"))
        assertTrue(wordIndex.isValidGuess("TOKYO"))
        assertTrue(wordIndex.isValidGuess("ALICE"))
        assertTrue(wordIndex.isValidGuess("DAVID"))
        assertTrue(wordIndex.isValidGuess("GUITAR"))
        assertTrue(wordIndex.isValidGuess("PIZZA"))
    }
}
