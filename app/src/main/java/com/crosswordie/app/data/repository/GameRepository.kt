package com.crosswordie.app.data.repository

import android.content.Context
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.SlotStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class PlayerStats(
    val dailyPlayed: Int = 0,
    val dailyCompleted: Int = 0,
    val currentDailyStreak: Int = 0,
    val bestDailyStreak: Int = 0,
    val lastCompletedDailyDate: String? = null,
    val endlessCurrentLevel: Int = 1,
    val endlessHintBank: Int = 3,
    val endlessBoardsCleared: Int = 0,
    val totalWordsSolved: Int = 0,
    val totalCascadeAutoSolves: Int = 0,
    val perfectBoards: Int = 0,
    val highScore: Int = 0,
    val preferredDifficulty: DifficultyLevel = DifficultyLevel.MEDIUM
)

/**
 * Local offline persistence repository that atomically saves and restores:
 * - Active Daily Puzzle state (`daily_board.json`)
 * - Active Endless Puzzle state (`endless_board.json`)
 * - Player streaks, Endless progression level, preferred difficulty, and lifetime statistics (`player_stats.json`)
 */
class GameRepository(context: Context) {

    private val storageDir = File(context.filesDir, "crosswordie_store").apply {
        if (!exists()) mkdirs()
    }

    private val dailySaveFile = File(storageDir, "daily_board.json")
    private val endlessSaveFile = File(storageDir, "endless_board.json")
    private val statsFile = File(storageDir, "player_stats.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Synchronized
    fun loadSavedBoard(
        mode: GameMode,
        expectedDailyDate: String? = null,
        expectedDifficulty: DifficultyLevel? = null
    ): PuzzleBoardState? {
        val file = if (mode == GameMode.DAILY) dailySaveFile else endlessSaveFile
        if (!file.exists()) return null
        return runCatching {
            val board = json.decodeFromString<PuzzleBoardState>(file.readText())
            val dateMatches = mode != GameMode.DAILY ||
                expectedDailyDate == null ||
                board.dailyDateString == expectedDailyDate
            val diffMatches = expectedDifficulty == null || board.difficulty == expectedDifficulty
            if (dateMatches && diffMatches) board else null
        }.getOrNull()
    }

    @Synchronized
    fun saveBoard(board: PuzzleBoardState) {
        val file = if (board.gameMode == GameMode.DAILY) dailySaveFile else endlessSaveFile
        runCatching {
            file.writeText(json.encodeToString(board))
        }
    }

    @Synchronized
    fun loadStats(): PlayerStats {
        if (!statsFile.exists()) return PlayerStats()
        return runCatching {
            json.decodeFromString<PlayerStats>(statsFile.readText())
        }.getOrDefault(PlayerStats())
    }

    @Synchronized
    fun saveStats(stats: PlayerStats) {
        runCatching {
            statsFile.writeText(json.encodeToString(stats))
        }
    }

    /**
     * Records a newly completed board into [PlayerStats], updating daily streaks,
     * endless level progression, total words solved, and high score.
     */
    @Synchronized
    fun recordBoardCompleted(
        board: PuzzleBoardState,
        cascadedCountOnFinalMove: Int
    ): PlayerStats {
        val current = loadStats()
        val wordsSolvedCount = board.slots.count { it.status == SlotStatus.SOLVED }
        val cascadeSolvesCount = board.slots.count { it.autoSolvedByCascade }
        val isPerfect = board.strikes == 0

        val updated = when (board.gameMode) {
            GameMode.DAILY -> {
                val todayStr = board.dailyDateString ?: LocalDate.now().toString()
                if (current.lastCompletedDailyDate == todayStr) {
                    current.copy(
                        totalWordsSolved = current.totalWordsSolved + wordsSolvedCount,
                        totalCascadeAutoSolves = current.totalCascadeAutoSolves + cascadeSolvesCount,
                        perfectBoards = current.perfectBoards + (if (isPerfect) 1 else 0),
                        highScore = maxOf(current.highScore, board.score)
                    )
                } else {
                    val isConsecutiveDay = current.lastCompletedDailyDate?.let { lastStr ->
                        runCatching {
                            val lastDate = LocalDate.parse(lastStr)
                            val todayDate = LocalDate.parse(todayStr)
                            lastDate.plusDays(1) == todayDate
                        }.getOrDefault(false)
                    } ?: false

                    val nextStreak = if (isConsecutiveDay) current.currentDailyStreak + 1 else 1
                    current.copy(
                        dailyPlayed = current.dailyPlayed + 1,
                        dailyCompleted = current.dailyCompleted + 1,
                        currentDailyStreak = nextStreak,
                        bestDailyStreak = maxOf(current.bestDailyStreak, nextStreak),
                        lastCompletedDailyDate = todayStr,
                        totalWordsSolved = current.totalWordsSolved + wordsSolvedCount,
                        totalCascadeAutoSolves = current.totalCascadeAutoSolves + cascadeSolvesCount,
                        perfectBoards = current.perfectBoards + (if (isPerfect) 1 else 0),
                        highScore = maxOf(current.highScore, board.score)
                    )
                }
            }

            GameMode.ENDLESS -> {
                val nextLevel = maxOf(current.endlessCurrentLevel, board.endlessLevel + 1)
                current.copy(
                    endlessCurrentLevel = nextLevel,
                    endlessHintBank = board.hintsRemaining.coerceIn(1, 6),
                    endlessBoardsCleared = current.endlessBoardsCleared + 1,
                    totalWordsSolved = current.totalWordsSolved + wordsSolvedCount,
                    totalCascadeAutoSolves = current.totalCascadeAutoSolves + cascadeSolvesCount,
                    perfectBoards = current.perfectBoards + (if (isPerfect) 1 else 0),
                    highScore = maxOf(current.highScore, board.score)
                )
            }
        }

        saveStats(updated)
        return updated
    }
}
