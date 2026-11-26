package com.crosswordie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GridCoord(val row: Int, val col: Int) {
    override fun toString(): String = "($row,$col)"
}

@Serializable
enum class Direction {
    ACROSS,
    DOWN;

    fun opposite(): Direction = if (this == ACROSS) DOWN else ACROSS
}

@Serializable
enum class LetterState {
    UNKNOWN,      // Unused on keyboard
    ABSENT,       // Gray: Not in the active word
    PRESENT,      // Yellow: In the active word, wrong position
    CORRECT,      // Green: Confirmed in this position via guess
    LOCKED_HINT   // Green + Lock badge: Pre-filled via an intersecting solved/revealed word
}

@Serializable
enum class SlotStatus {
    IN_PROGRESS,
    SOLVED,
    REVEALED_FAILED
}

@Serializable
enum class DifficultyLevel(
    val displayName: String,
    val description: String,
    val prefer5x5: Boolean,
    val maxWords: Int,
    val starterHints: Int,
    val dailyHintAllowance: Int,
    val scoreMultiplierPercent: Int
) {
    EASY(
        displayName = "Easy",
        description = "5x5 Grid • 6 Words • 1 Starter Letter • 3 Hints",
        prefer5x5 = true,
        maxWords = 6,
        starterHints = 1,
        dailyHintAllowance = 3,
        scoreMultiplierPercent = 100
    ),
    MEDIUM(
        displayName = "Medium",
        description = "6x6 Grid • 6–7 Words • Classic Deduction • 2 Hints",
        prefer5x5 = false,
        maxWords = 7,
        starterHints = 0,
        dailyHintAllowance = 2,
        scoreMultiplierPercent = 125
    ),
    HARD(
        displayName = "Hard",
        description = "6x6 Dense Grid • 7–8 Words • 1.5x Points • 1 Hint",
        prefer5x5 = false,
        maxWords = 8,
        starterHints = 0,
        dailyHintAllowance = 1,
        scoreMultiplierPercent = 150
    )
}

@Serializable
data class EvaluatedTile(
    val char: Char,
    val state: LetterState
)

@Serializable
data class GuessRow(
    val tiles: List<EvaluatedTile>
) {
    val word: String
        get() = tiles.joinToString("") { it.char.toString() }
}

@Serializable
data class GridCell(
    val coord: GridCoord,
    val isPlayable: Boolean,
    val solutionChar: Char? = null,
    val lockedChar: Char? = null,
    val isRevealedByFailure: Boolean = false,
    val clueNumber: Int? = null,
    val acrossSlotId: String? = null,
    val downSlotId: String? = null
)

@Serializable
data class WordSlot(
    val id: String,                        // e.g., "1A", "2D"
    val clueNumber: Int,
    val direction: Direction,
    val startCoord: GridCoord,
    val length: Int,
    val targetWord: String,
    val guesses: List<GuessRow> = emptyList(),
    val lockedIndices: Map<Int, Char> = emptyMap(),
    val hintCandidateWords: List<String> = emptyList(),
    val status: SlotStatus = SlotStatus.IN_PROGRESS,
    val autoSolvedByCascade: Boolean = false
) {
    val coords: List<GridCoord>
        get() = (0 until length).map { index ->
            if (direction == Direction.ACROSS) {
                GridCoord(startCoord.row, startCoord.col + index)
            } else {
                GridCoord(startCoord.row + index, startCoord.col)
            }
        }

    fun indexOfCoord(coord: GridCoord): Int {
        return if (direction == Direction.ACROSS) {
            if (coord.row == startCoord.row && coord.col in startCoord.col until (startCoord.col + length)) {
                coord.col - startCoord.col
            } else -1
        } else {
            if (coord.col == startCoord.col && coord.row in startCoord.row until (startCoord.row + length)) {
                coord.row - startCoord.row
            } else -1
        }
    }

    val isCompleted: Boolean
        get() = status == SlotStatus.SOLVED || status == SlotStatus.REVEALED_FAILED

    val remainingGuesses: Int
        get() = (MAX_GUESSES - guesses.size).coerceAtLeast(0)

    companion object {
        const val MAX_GUESSES = 5
    }
}

@Serializable
enum class GameMode {
    DAILY,
    ENDLESS
}

@Serializable
data class PuzzleBoardState(
    val puzzleId: String,
    val seed: Long,
    val gameMode: GameMode,
    val difficulty: DifficultyLevel = DifficultyLevel.MEDIUM,
    val endlessLevel: Int = 1,
    val dailyDateString: String? = null,
    val gridSize: Int,
    val cells: List<GridCell>,
    val slots: List<WordSlot>,
    val activeSlotId: String,
    val hintsRemaining: Int = 2,
    val strikes: Int = 0,
    val score: Int = 0,
    val isBoardComplete: Boolean = false
) {
    fun cellAt(row: Int, col: Int): GridCell? =
        cells.firstOrNull { it.coord.row == row && it.coord.col == col }

    fun cellAt(coord: GridCoord): GridCell? =
        cells.firstOrNull { it.coord == coord }

    fun slotById(id: String): WordSlot? =
        slots.firstOrNull { it.id == id }

    val activeSlot: WordSlot?
        get() = slotById(activeSlotId)
}
