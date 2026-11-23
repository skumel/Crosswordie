package com.crosswordie.app.domain.generator

import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class CrosswordGeneratorTest {

    companion object {
        private lateinit var wordIndex: WordIndex
        private lateinit var generator: CrosswordGenerator

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val assetFile = File("src/main/assets/words_target.txt").let {
                if (it.exists()) it else File("app/src/main/assets/words_target.txt")
            }
            assertTrue("words_target.txt must exist at ${assetFile.absolutePath}", assetFile.exists())
            val words = assetFile.readLines()
            wordIndex = WordIndex(words, words)
            generator = CrosswordGenerator(wordIndex)
        }
    }

    @Test
    fun `wordIndex loads 3 to 6 letter words and supports positional lookup`() {
        for (len in 3..6) {
            val list = wordIndex.wordsByLength[len]
            assertNotNull("Words of length $len should exist", list)
            assertTrue("Words of length $len should have at least 300 entries", list!!.size >= 300)
        }

        val candidates = wordIndex.getCandidates(
            length = 5,
            constraints = mapOf(0 to 'S', 4 to 'E')
        )
        assertTrue("Should find 5-letter words starting with S and ending with E", candidates.isNotEmpty())
        assertTrue(candidates.all { it.length == 5 && it[0] == 'S' && it[4] == 'E' })

        // Verify singular and plural forms (e.g., MAKE / MAKES, CAT / CATS, BOX / BOXES, BABY / BABIES)
        assertTrue(wordIndex.isValidGuess("MAKE"))
        assertTrue(wordIndex.isValidGuess("MAKES"))
        assertTrue(wordIndex.isValidGuess("CAT"))
        assertTrue(wordIndex.isValidGuess("CATS"))
        assertTrue(wordIndex.isValidGuess("BOX"))
        assertTrue(wordIndex.isValidGuess("BOXES"))
        assertTrue(wordIndex.isValidGuess("BABY"))
        assertTrue(wordIndex.isValidGuess("BABIES"))
    }

    @Test
    fun `all templates pass structural connectivity and intersection invariants`() {
        val allTemplates = GridTemplate.TEMPLATES_5X5 + GridTemplate.TEMPLATES_6X6
        for (template in allTemplates) {
            assertTrue(
                "Template ${template.name} must be structurally valid",
                generator.validateTemplateTopology(template)
            )
            assertTrue(
                "Template ${template.name} must have 6..8 slots",
                template.slotSpecs.size in 6..8
            )
        }
    }

    @Test
    fun `deterministic seed produces identical crossword board`() {
        val seed = 20261122L
        val board1 = generator.generate(seed = seed, gameMode = GameMode.DAILY, dailyDateString = "2026-11-22")
        val board2 = generator.generate(seed = seed, gameMode = GameMode.DAILY, dailyDateString = "2026-11-22")

        assertEquals(board1.gridSize, board2.gridSize)
        assertEquals(board1.slots.map { it.id to it.targetWord }, board2.slots.map { it.id to it.targetWord })
        assertEquals(board1.cells, board2.cells)
    }

    @Test
    fun `generates 50 distinct seeded boards with valid intersections and unique words`() {
        for (i in 1..50) {
            val seed = i * 987654321L
            val prefer5x5 = (i <= 15)
            val board = generator.generate(
                seed = seed,
                gameMode = if (prefer5x5) GameMode.ENDLESS else GameMode.DAILY,
                endlessLevel = i,
                prefer5x5 = prefer5x5
            )

            assertTrue("Grid size must be 5 or 6", board.gridSize in 5..6)
            assertTrue("Word count must be 6..8", board.slots.size in 6..8)

            // Verify all target words on a single board are unique and valid
            val targetWords = board.slots.map { it.targetWord }
            assertEquals("All words on board must be unique", targetWords.size, targetWords.distinct().size)
            for (slot in board.slots) {
                assertEquals(slot.length, slot.targetWord.length)
                assertTrue("Target word ${slot.targetWord} must be valid", wordIndex.isValidGuess(slot.targetWord))
            }

            // Verify every intersection cell shares the exact same character between Across and Down slots
            for (cell in board.cells) {
                if (!cell.isPlayable) continue
                assertNotNull(cell.solutionChar)

                cell.acrossSlotId?.let { acrossId ->
                    val acrossSlot = board.slotById(acrossId)!!
                    val idx = acrossSlot.indexOfCoord(cell.coord)
                    assertTrue(idx >= 0)
                    assertEquals(cell.solutionChar, acrossSlot.targetWord[idx])
                }

                cell.downSlotId?.let { downId ->
                    val downSlot = board.slotById(downId)!!
                    val idx = downSlot.indexOfCoord(cell.coord)
                    assertTrue(idx >= 0)
                    assertEquals(cell.solutionChar, downSlot.targetWord[idx])
                }
            }

            // Verify every slot intersects at least one perpendicular slot
            for (slot in board.slots) {
                val hasIntersection = slot.coords.any { coord ->
                    val cell = board.cellAt(coord)!!
                    if (slot.direction == Direction.ACROSS) cell.downSlotId != null
                    else cell.acrossSlotId != null
                }
                assertTrue("Slot ${slot.id} on seed $seed must have at least one intersection", hasIntersection)
            }
        }
    }
}
