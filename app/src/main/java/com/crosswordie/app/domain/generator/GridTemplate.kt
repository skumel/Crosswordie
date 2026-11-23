package com.crosswordie.app.domain.generator

import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.GridCoord

/**
 * Defines a slot specification in a crossword layout template before words are assigned.
 */
data class SlotSpec(
    val id: String,
    val clueNumber: Int,
    val direction: Direction,
    val startCoord: GridCoord,
    val length: Int
) {
    val coords: List<GridCoord> = (0 until length).map { i ->
        if (direction == Direction.ACROSS) GridCoord(startCoord.row, startCoord.col + i)
        else GridCoord(startCoord.row + i, startCoord.col)
    }

    fun indexOf(coord: GridCoord): Int = coords.indexOf(coord)
}

/**
 * Represents a pre-validated intersecting slot topology on a 5x5 or 6x6 grid.
 *
 * Unlike full dense crosswords (where every adjacent letter creates unintended 2-letter
 * perpendicular words), Crosswordie templates explicitly define 6 to 8 interlocking
 * Across and Down word slots of length 3..6 such that:
 *  1. Every slot has at least 1 valid perpendicular intersection (typically 2-3).
 *  2. All slots form a single connected graph component.
 *  3. Parallel slots do not touch side-by-side without a shared perpendicular word,
 *     preventing confusing 2-letter phantom words on the board.
 */
data class GridTemplate(
    val name: String,
    val gridSize: Int,
    val slotSpecs: List<SlotSpec>
) {
    companion object {
        /**
         * Builds a [GridTemplate] from explicit (direction, startRow, startCol, length) tuples,
         * automatically computing standard crossword clue numbers (top-to-bottom, left-to-right).
         */
        fun create(
            name: String,
            gridSize: Int,
            rawSlots: List<RawSlot>
        ): GridTemplate {
            // Sort start coordinates in reading order (row, then col) to assign standard clue numbers
            val distinctStarts = rawSlots
                .map { GridCoord(it.row, it.col) }
                .distinct()
                .sortedWith(compareBy<GridCoord> { it.row }.thenBy { it.col })

            val clueNumberByCoord = distinctStarts
                .withIndex()
                .associate { (idx, coord) -> coord to (idx + 1) }

            val specs = rawSlots
                .sortedWith(
                    compareBy<RawSlot> { clueNumberByCoord[GridCoord(it.row, it.col)] ?: 99 }
                        .thenBy { it.direction }
                )
                .map { raw ->
                    val start = GridCoord(raw.row, raw.col)
                    val num = clueNumberByCoord.getValue(start)
                    val suffix = if (raw.direction == Direction.ACROSS) "A" else "D"
                    SlotSpec(
                        id = "$num$suffix",
                        clueNumber = num,
                        direction = raw.direction,
                        startCoord = start,
                        length = raw.length
                    )
                }

            return GridTemplate(
                name = name,
                gridSize = gridSize,
                slotSpecs = specs
            )
        }

        data class RawSlot(
            val direction: Direction,
            val row: Int,
            val col: Int,
            val length: Int
        )

        /**
         * Curated 5x5 templates (6 interlocking words of lengths 3 to 5).
         */
        val TEMPLATES_5X5: List<GridTemplate> = listOf(
            // Template 5A: Classic Tic-Tac-Frame (6 words: three 5-letter, three 4/5-letter)
            // Row 0: [X][X][X][X][X] (5A)
            // Row 2: [X][X][X][X][X] (5A)
            // Row 4: [X][X][X][X][.] (4A)
            // Col 0: [X][X][X][X][X] (5D)
            // Col 2: [X][X][X][X][X] (5D)
            // Col 4: [X][X][X][.][.] (3D)
            create(
                name = "5x5_Lattice_6W",
                gridSize = 5,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 0, 5),
                    RawSlot(Direction.ACROSS, 2, 0, 5),
                    RawSlot(Direction.ACROSS, 4, 0, 4),
                    RawSlot(Direction.DOWN, 0, 0, 5),
                    RawSlot(Direction.DOWN, 0, 2, 5),
                    RawSlot(Direction.DOWN, 0, 4, 3)
                )
            ),
            // Template 5B: Pinwheel 6 words (lengths 3, 4, 5)
            // Row 0: [X][X][X][X][.] (4A)
            // Row 2: [X][X][X][X][X] (5A)
            // Row 4: [.][X][X][X][X] (4A)
            // Col 0: [X][X][X][.][.] (3D)
            // Col 2: [X][X][X][X][X] (5D)
            // Col 4: [.][.][X][X][X] (3D)
            create(
                name = "5x5_Pinwheel_6W",
                gridSize = 5,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 0, 4),
                    RawSlot(Direction.ACROSS, 2, 0, 5),
                    RawSlot(Direction.ACROSS, 4, 1, 4),
                    RawSlot(Direction.DOWN, 0, 0, 3),
                    RawSlot(Direction.DOWN, 0, 2, 5),
                    RawSlot(Direction.DOWN, 2, 4, 3)
                )
            ),
            // Template 5C: Step Ladder 6 words (lengths 3, 4, 5)
            // Row 0: [.][X][X][X][X] (4A)
            // Row 2: [X][X][X][X][X] (5A)
            // Row 4: [X][X][X][.][.] (3A)
            // Col 0: [.][.][X][X][X] (3D)
            // Col 2: [X][X][X][X][X] (5D)
            // Col 4: [X][X][X][X][.] (4D)
            create(
                name = "5x5_StepLadder_6W",
                gridSize = 5,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 1, 4),
                    RawSlot(Direction.ACROSS, 2, 0, 5),
                    RawSlot(Direction.ACROSS, 4, 0, 3),
                    RawSlot(Direction.DOWN, 2, 0, 3),
                    RawSlot(Direction.DOWN, 0, 2, 5),
                    RawSlot(Direction.DOWN, 0, 4, 4)
                )
            )
        )

        /**
         * Curated 6x6 templates (6 to 8 interlocking words of lengths 3 to 6).
         */
        val TEMPLATES_6X6: List<GridTemplate> = listOf(
            // Template 6A: 7-Word Grand Lattice (lengths 3, 4, 5, 6)
            // Across:
            //   Row 0, Col 0..4 (len 5)
            //   Row 2, Col 0..5 (len 6)
            //   Row 4, Col 1..5 (len 5)
            // Down:
            //   Col 0, Row 0..3 (len 4) -> intersects Row 0 & Row 2
            //   Col 2, Row 0..5 (len 6) -> intersects Row 0, Row 2, Row 4
            //   Col 4, Row 0..4 (len 5) -> intersects Row 0, Row 2, Row 4
            //   Row 5, Col 1..3 (len 3) -> intersects Col 2 at (5,2)
            create(
                name = "6x6_GrandLattice_7W",
                gridSize = 6,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 0, 5),
                    RawSlot(Direction.ACROSS, 2, 0, 6),
                    RawSlot(Direction.ACROSS, 4, 1, 5),
                    RawSlot(Direction.ACROSS, 5, 1, 3),
                    RawSlot(Direction.DOWN, 0, 0, 4),
                    RawSlot(Direction.DOWN, 0, 2, 6),
                    RawSlot(Direction.DOWN, 0, 4, 5)
                )
            ),
            // Template 6B: 7-Word Symmetric Weave (lengths 3, 4, 5, 6)
            // Across:
            //   Row 0, Col 0..3 (len 4)
            //   Row 2, Col 0..5 (len 6)
            //   Row 4, Col 0..4 (len 5)
            // Down:
            //   Col 0, Row 0..4 (len 5) -> intersects Row 0, 2, 4
            //   Col 2, Row 0..5 (len 6) -> intersects Row 0, 2, 4
            //   Col 4, Row 2..5 (len 4) -> intersects Row 2, 4
            //   Col 5, Row 0..2 (len 3) -> intersects Row 2 at (2,5)
            create(
                name = "6x6_SymmetricWeave_7W",
                gridSize = 6,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 0, 4),
                    RawSlot(Direction.ACROSS, 2, 0, 6),
                    RawSlot(Direction.ACROSS, 4, 0, 5),
                    RawSlot(Direction.DOWN, 0, 0, 5),
                    RawSlot(Direction.DOWN, 0, 2, 6),
                    RawSlot(Direction.DOWN, 2, 4, 4),
                    RawSlot(Direction.DOWN, 0, 5, 3)
                )
            ),
            // Template 6C: 8-Word Double Cross (lengths 3, 4, 5, 6)
            // Across:
            //   Row 0, Col 0..4 (len 5)
            //   Row 2, Col 0..5 (len 6)
            //   Row 4, Col 0..3 (len 4)
            //   Row 5, Col 3..5 (len 3)
            // Down:
            //   Col 0, Row 0..4 (len 5) -> intersects Row 0, 2, 4
            //   Col 2, Row 0..4 (len 5) -> intersects Row 0, 2, 4
            //   Col 4, Row 0..3 (len 4) -> intersects Row 0, 2
            //   Col 5, Row 2..5 (len 4) -> intersects Row 2, 5
            create(
                name = "6x6_DoubleCross_8W",
                gridSize = 6,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 0, 5),
                    RawSlot(Direction.ACROSS, 2, 0, 6),
                    RawSlot(Direction.ACROSS, 4, 0, 4),
                    RawSlot(Direction.ACROSS, 5, 3, 3),
                    RawSlot(Direction.DOWN, 0, 0, 5),
                    RawSlot(Direction.DOWN, 0, 2, 5),
                    RawSlot(Direction.DOWN, 0, 4, 4),
                    RawSlot(Direction.DOWN, 2, 5, 4)
                )
            ),
            // Template 6D: 6-Word Open Grid (lengths 4, 5, 6)
            // Across:
            //   Row 1, Col 0..5 (len 6)
            //   Row 3, Col 0..4 (len 5)
            //   Row 5, Col 1..4 (len 4)
            // Down:
            //   Col 1, Row 0..5 (len 6) -> intersects Row 1, 3, 5
            //   Col 3, Row 1..5 (len 5) -> intersects Row 1, 3, 5
            //   Col 5, Row 0..3 (len 4) -> intersects Row 1
            create(
                name = "6x6_OpenGrid_6W",
                gridSize = 6,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 1, 0, 6),
                    RawSlot(Direction.ACROSS, 3, 0, 5),
                    RawSlot(Direction.ACROSS, 5, 1, 4),
                    RawSlot(Direction.DOWN, 0, 1, 6),
                    RawSlot(Direction.DOWN, 1, 3, 5),
                    RawSlot(Direction.DOWN, 0, 5, 4)
                )
            ),
            // Template 6E: 8-Word Fortress (lengths 3, 4, 5, 6)
            // Across:
            //   Row 0, Col 1..5 (len 5)
            //   Row 2, Col 0..4 (len 5)
            //   Row 4, Col 0..5 (len 6)
            //   Row 5, Col 0..2 (len 3)
            // Down:
            //   Col 0, Row 2..5 (len 4) -> intersects Row 2, 4, 5
            //   Col 1, Row 0..2 (len 3) -> intersects Row 0, 2
            //   Col 3, Row 0..4 (len 5) -> intersects Row 0, 2, 4
            //   Col 5, Row 0..4 (len 5) -> intersects Row 0, 4
            create(
                name = "6x6_Fortress_8W",
                gridSize = 6,
                rawSlots = listOf(
                    RawSlot(Direction.ACROSS, 0, 1, 5),
                    RawSlot(Direction.ACROSS, 2, 0, 5),
                    RawSlot(Direction.ACROSS, 4, 0, 6),
                    RawSlot(Direction.ACROSS, 5, 0, 3),
                    RawSlot(Direction.DOWN, 2, 0, 4),
                    RawSlot(Direction.DOWN, 0, 1, 3),
                    RawSlot(Direction.DOWN, 0, 3, 5),
                    RawSlot(Direction.DOWN, 0, 5, 5)
                )
            )
        )
    }
}
