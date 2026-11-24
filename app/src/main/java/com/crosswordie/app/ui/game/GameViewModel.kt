package com.crosswordie.app.ui.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crosswordie.app.data.dictionary.AssetWordRepository
import com.crosswordie.app.data.repository.GameRepository
import com.crosswordie.app.domain.engine.CascadeEngine
import com.crosswordie.app.domain.engine.ShareSummaryBuilder
import com.crosswordie.app.domain.engine.WordleEvaluator
import com.crosswordie.app.domain.generator.CrosswordGenerator
import com.crosswordie.app.domain.generator.WordIndex
import com.crosswordie.app.domain.model.DifficultyLevel
import com.crosswordie.app.domain.model.Direction
import com.crosswordie.app.domain.model.GameMode
import com.crosswordie.app.domain.model.GridCoord
import com.crosswordie.app.domain.model.PuzzleBoardState
import com.crosswordie.app.domain.model.WordSlot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Unidirectional MVVM ViewModel managing the Opening Menu, difficulty levels, active Crosswordie board,
 * Wordle guess buffer, cascade propagation, mode switching (Daily Seeded vs. Endless), and pause/quit flows.
 */
class GameViewModel(
    private val wordRepository: AssetWordRepository,
    private val gameRepository: GameRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState(showOpeningMenu = true, isLoading = true))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var wordIndex: WordIndex? = null
    private var generator: CrosswordGenerator? = null

    init {
        loadOpeningMenuState()
    }

    private fun loadOpeningMenuState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val (stats, savedDaily, savedEndless) = withContext(ioDispatcher) {
                val idx = wordIndex ?: wordRepository.getWordIndex().also {
                    wordIndex = it
                    generator = CrosswordGenerator(it)
                }
                if (generator == null) generator = CrosswordGenerator(idx)

                val currentStats = gameRepository.loadStats()
                val todayStr = LocalDate.now().toString()
                val daily = gameRepository.loadSavedBoard(GameMode.DAILY, expectedDailyDate = todayStr)
                val endless = gameRepository.loadSavedBoard(GameMode.ENDLESS)
                Triple(currentStats, daily, endless)
            }

            val previewBoard = _uiState.value.board ?: savedDaily ?: savedEndless
            val activePreviewSlot = previewBoard?.activeSlot
            val syncedInputMap = if (activePreviewSlot != null) {
                HashMap(activePreviewSlot.lockedIndices)
            } else {
                emptyMap()
            }
            val syncedCursor = if (activePreviewSlot != null) {
                findFirstUnlockedIndex(activePreviewSlot, 0)
            } else {
                0
            }
            val syncedKeyboard = if (activePreviewSlot != null) {
                WordleEvaluator.computeKeyboardState(
                    activePreviewSlot.guesses,
                    activePreviewSlot.lockedIndices
                )
            } else {
                emptyMap()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stats = stats,
                    selectedDifficulty = stats.preferredDifficulty,
                    board = previewBoard,
                    currentInputMap = if (it.currentInputMap.isNotEmpty() && it.board?.puzzleId == previewBoard?.puzzleId) {
                        HashMap(it.currentInputMap).apply { putAll(syncedInputMap) }
                    } else {
                        syncedInputMap
                    },
                    cursorIndex = if (it.board?.puzzleId == previewBoard?.puzzleId &&
                        activePreviewSlot != null &&
                        !activePreviewSlot.lockedIndices.containsKey(it.cursorIndex)
                    ) {
                        it.cursorIndex
                    } else {
                        syncedCursor
                    },
                    keyboardState = syncedKeyboard,
                    hasSavedDailyInProgress = savedDaily != null && !savedDaily.isBoardComplete,
                    hasSavedEndlessInProgress = savedEndless != null && !savedEndless.isBoardComplete
                )
            }
        }
    }

    fun selectDifficulty(difficulty: DifficultyLevel) {
        _uiState.update { it.copy(selectedDifficulty = difficulty) }
        viewModelScope.launch(ioDispatcher) {
            val currentStats = gameRepository.loadStats()
            gameRepository.saveStats(currentStats.copy(preferredDifficulty = difficulty))
        }
    }

    /**
     * Launches or resumes a game from the Opening Menu with the chosen [mode] and currently selected [DifficultyLevel].
     */
    fun startGameFromMenu(mode: GameMode, forceNewGame: Boolean = false) {
        val chosenDifficulty = _uiState.value.selectedDifficulty
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showOpeningMenu = false, showPauseDialog = false) }
            val (loadedBoard, stats) = withContext(ioDispatcher) {
                val idx = wordIndex ?: wordRepository.getWordIndex().also {
                    wordIndex = it
                    generator = CrosswordGenerator(it)
                }
                val gen = generator ?: CrosswordGenerator(idx).also { generator = it }
                val currentStats = gameRepository.loadStats().copy(preferredDifficulty = chosenDifficulty)
                gameRepository.saveStats(currentStats)

                val todayStr = LocalDate.now().toString()
                val saved = if (!forceNewGame) {
                    gameRepository.loadSavedBoard(
                        mode = mode,
                        expectedDailyDate = if (mode == GameMode.DAILY) todayStr else null,
                        expectedDifficulty = chosenDifficulty
                    )?.takeIf { !it.isBoardComplete }
                } else null

                val useFreshSalt = forceNewGame ||
                    (mode == GameMode.DAILY && gameRepository.loadSavedBoard(
                        mode = GameMode.DAILY,
                        expectedDailyDate = todayStr,
                        expectedDifficulty = chosenDifficulty
                    )?.isBoardComplete == true)

                val board = saved ?: createFreshBoard(
                    generator = gen,
                    mode = mode,
                    difficulty = chosenDifficulty,
                    endlessLevel = currentStats.endlessCurrentLevel,
                    salt = if (useFreshSalt) System.currentTimeMillis() else 0L
                ).also { gameRepository.saveBoard(it) }

                board to currentStats
            }

            applyBoardToUiState(
                board = loadedBoard,
                stats = stats,
                bannerMessage = "${loadedBoard.difficulty.displayName} • ${loadedBoard.gridSize}x${loadedBoard.gridSize} Grid",
                showEndDialog = loadedBoard.isBoardComplete
            )
        }
    }

    /**
     * Resumes whichever board is currently active (when closing the Pause dialog or tapping Resume on Menu).
     */
    fun resumeCurrentGame() {
        val currentBoard = _uiState.value.board
        if (currentBoard != null) {
            val slot = currentBoard.activeSlot
            val mergedInput = HashMap(_uiState.value.currentInputMap)
            if (slot != null) {
                mergedInput.putAll(slot.lockedIndices)
            }
            val safeCursor = if (slot != null &&
                (slot.lockedIndices.containsKey(_uiState.value.cursorIndex) ||
                    _uiState.value.cursorIndex !in 0 until slot.length)
            ) {
                findFirstUnlockedIndex(slot, 0)
            } else {
                _uiState.value.cursorIndex
            }
            val syncedKeyboard = if (slot != null) {
                WordleEvaluator.computeKeyboardState(slot.guesses, slot.lockedIndices)
            } else {
                _uiState.value.keyboardState
            }

            _uiState.update {
                it.copy(
                    showOpeningMenu = false,
                    showPauseDialog = false,
                    currentInputMap = mergedInput,
                    cursorIndex = safeCursor,
                    keyboardState = syncedKeyboard
                )
            }
        } else {
            startGameFromMenu(GameMode.DAILY, forceNewGame = false)
        }
    }

    /**
     * Restarts the current mode & difficulty with a fresh board from the Pause Menu.
     */
    fun restartCurrentPuzzle() {
        val currentMode = _uiState.value.board?.gameMode ?: GameMode.ENDLESS
        startGameFromMenu(mode = currentMode, forceNewGame = true)
    }

    /**
     * Pauses and returns to the Opening Menu, saving current puzzle state automatically.
     */
    fun returnToOpeningMenu() {
        val currentBoard = _uiState.value.board
        if (currentBoard != null) {
            persistBoardAsync(currentBoard)
        }
        _uiState.update {
            it.copy(
                showPauseDialog = false,
                showEndGameDialog = false,
                showOpeningMenu = true
            )
        }
        loadOpeningMenuState()
    }

    fun togglePauseDialog(show: Boolean) {
        _uiState.update { it.copy(showPauseDialog = show) }
    }

    fun switchMode(mode: GameMode) {
        if (_uiState.value.board?.gameMode == mode && !_uiState.value.isLoading) return
        startGameFromMenu(mode = mode, forceNewGame = false)
    }

    fun startNextEndlessPuzzle() {
        val difficulty = _uiState.value.selectedDifficulty
        viewModelScope.launch {
            val gen = generator ?: return@launch
            val currentStats = withContext(ioDispatcher) { gameRepository.loadStats() }
            val nextLevel = currentStats.endlessCurrentLevel.coerceAtLeast(1)

            val freshBoard = withContext(ioDispatcher) {
                createFreshBoard(
                    generator = gen,
                    mode = GameMode.ENDLESS,
                    difficulty = difficulty,
                    endlessLevel = nextLevel,
                    salt = System.currentTimeMillis()
                ).also { gameRepository.saveBoard(it) }
            }

            applyBoardToUiState(
                board = freshBoard,
                stats = currentStats,
                bannerMessage = "Level $nextLevel (${difficulty.displayName}) • ${freshBoard.gridSize}x${freshBoard.gridSize} Grid",
                showEndDialog = false
            )
        }
    }

    /**
     * Handles tapping a cell on the top crossword grid.
     */
    fun onGridCellTapped(coord: GridCoord) {
        val board = _uiState.value.board ?: return
        val cell = board.cellAt(coord) ?: return
        if (!cell.isPlayable) return

        val currentSlot = board.activeSlot
        val acrossId = cell.acrossSlotId
        val downId = cell.downSlotId

        val targetSlotId = when {
            acrossId != null && downId != null -> {
                when (currentSlot?.id) {
                    acrossId -> downId
                    downId -> acrossId
                    else -> {
                        val acrossSlot = board.slotById(acrossId)
                        val downSlot = board.slotById(downId)
                        when {
                            acrossSlot != null && !acrossSlot.isCompleted && (downSlot == null || downSlot.isCompleted) -> acrossId
                            downSlot != null && !downSlot.isCompleted && (acrossSlot == null || acrossSlot.isCompleted) -> downId
                            currentSlot?.direction == Direction.DOWN -> downId
                            else -> acrossId
                        }
                    }
                }
            }
            acrossId != null -> acrossId
            downId != null -> downId
            else -> return
        }

        selectWordSlot(targetSlotId)
    }

    fun selectWordSlot(slotId: String) {
        val board = _uiState.value.board ?: return
        if (board.slotById(slotId) == null) return

        val updatedBoard = board.copy(activeSlotId = slotId)
        persistBoardAsync(updatedBoard)

        val slot = updatedBoard.activeSlot ?: return
        val prefilledInput = HashMap(slot.lockedIndices)
        val firstEditableCursor = findFirstUnlockedIndex(slot, 0)

        _uiState.update { state ->
            state.copy(
                board = updatedBoard,
                currentInputMap = prefilledInput,
                cursorIndex = firstEditableCursor,
                keyboardState = WordleEvaluator.computeKeyboardState(slot.guesses, slot.lockedIndices),
                bannerMessage = null,
                newlyUnlockedCoords = emptySet(),
                lastRevealedRowIndex = -1
            )
        }
    }

    fun cycleWordSlot(forward: Boolean = true) {
        val board = _uiState.value.board ?: return
        val slots = board.slots
        if (slots.isEmpty()) return

        val currentIdx = slots.indexOfFirst { it.id == board.activeSlotId }.coerceAtLeast(0)
        val hasUnsolved = slots.any { !it.isCompleted }
        for (step in 1..slots.size) {
            val offset = if (forward) step else (slots.size - step)
            val candidate = slots[(currentIdx + offset) % slots.size]
            if (!hasUnsolved || !candidate.isCompleted) {
                selectWordSlot(candidate.id)
                return
            }
        }
    }

    fun onGuessTileTapped(columnIndex: Int) {
        val slot = _uiState.value.activeSlot ?: return
        if (slot.isCompleted) return
        if (columnIndex in 0 until slot.length && !slot.lockedIndices.containsKey(columnIndex)) {
            _uiState.update { it.copy(cursorIndex = columnIndex, bannerMessage = null) }
        }
    }

    fun onLetterTyped(char: Char) {
        val state = _uiState.value
        val slot = state.activeSlot ?: return
        if (slot.isCompleted || state.board?.isBoardComplete == true) return

        val upper = char.uppercaseChar()
        val locked = slot.lockedIndices

        val targetIndex = if (!locked.containsKey(state.cursorIndex) && state.cursorIndex in 0 until slot.length) {
            state.cursorIndex
        } else {
            findFirstEmptyUnlockedIndex(slot, state.currentInputMap)
                ?: findFirstUnlockedIndex(slot, 0)
        }

        if (locked.containsKey(targetIndex) || targetIndex !in 0 until slot.length) return

        val newInput = HashMap(state.currentInputMap)
        newInput.putAll(locked)
        newInput[targetIndex] = upper

        val nextCursor = findNextUnlockedIndexAfter(slot, newInput, targetIndex)

        _uiState.update {
            it.copy(
                currentInputMap = newInput,
                cursorIndex = nextCursor,
                bannerMessage = null
            )
        }
    }

    fun onBackspacePressed() {
        val state = _uiState.value
        val slot = state.activeSlot ?: return
        if (slot.isCompleted || state.board?.isBoardComplete == true) return

        val locked = slot.lockedIndices
        val newInput = HashMap(state.currentInputMap)
        newInput.putAll(locked)

        if (!locked.containsKey(state.cursorIndex) && newInput.containsKey(state.cursorIndex)) {
            newInput.remove(state.cursorIndex)
            _uiState.update {
                it.copy(
                    currentInputMap = newInput,
                    bannerMessage = null
                )
            }
            return
        }

        var prevIdx = (state.cursorIndex - 1).coerceAtMost(slot.length - 1)
        while (prevIdx >= 0 && locked.containsKey(prevIdx)) {
            prevIdx--
        }

        if (prevIdx >= 0) {
            newInput.remove(prevIdx)
            _uiState.update {
                it.copy(
                    currentInputMap = newInput,
                    cursorIndex = prevIdx,
                    bannerMessage = null
                )
            }
        }
    }

    fun onSubmitGuess() {
        val state = _uiState.value
        val board = state.board ?: return
        val slot = state.activeSlot ?: return
        if (slot.isCompleted || board.isBoardComplete) return

        val effectiveInput = HashMap(state.currentInputMap).apply {
            putAll(slot.lockedIndices)
        }

        if (effectiveInput.size < slot.length) {
            _uiState.update {
                it.copy(
                    currentInputMap = effectiveInput,
                    bannerMessage = "Complete all ${slot.length} letters first",
                    invalidGuessShakeTrigger = it.invalidGuessShakeTrigger + 1
                )
            }
            return
        }

        val guessWord = (0 until slot.length)
            .map { idx -> effectiveInput.getValue(idx) }
            .joinToString("")

        val idx = wordIndex
        if (idx != null && !idx.isValidGuess(guessWord) && guessWord != slot.targetWord) {
            _uiState.update {
                it.copy(
                    bannerMessage = "\"$guessWord\" is not in word list",
                    invalidGuessShakeTrigger = it.invalidGuessShakeTrigger + 1
                )
            }
            return
        }

        val submission = CascadeEngine.submitGuess(board, guessWord)
        val updatedBoard = submission.updatedBoard
        persistBoardAsync(updatedBoard)

        val nextSlot = updatedBoard.activeSlot ?: slot
        val nextInput = HashMap(nextSlot.lockedIndices)
        val nextCursor = findFirstUnlockedIndex(nextSlot, 0)
        val nextKeyboard = WordleEvaluator.computeKeyboardState(nextSlot.guesses, nextSlot.lockedIndices)

        val statusBanner = when (submission.outcome) {
            CascadeEngine.GuessOutcome.CONTINUE_SLOT -> null
            CascadeEngine.GuessOutcome.SLOT_SOLVED -> {
                if (submission.cascadedAutoSolvedSlotIds.isNotEmpty()) {
                    val cascadedStr = submission.cascadedAutoSolvedSlotIds.joinToString(", ")
                    "Solved ${slot.id}! Cascade auto-solved $cascadedStr! (+${submission.pointsEarned} pts)"
                } else {
                    "Solved ${slot.id} (${slot.targetWord})! Intersections unlocked (+${submission.pointsEarned} pts)"
                }
            }
            CascadeEngine.GuessOutcome.SLOT_REVEALED_FAILED -> {
                "${slot.id} revealed: ${slot.targetWord} (0 pts) • Intersections unlocked!"
            }
            CascadeEngine.GuessOutcome.BOARD_COMPLETED -> {
                "Crossword Complete! Score: ${updatedBoard.score}"
            }
        }

        if (submission.outcome == CascadeEngine.GuessOutcome.BOARD_COMPLETED) {
            viewModelScope.launch {
                val updatedStats = withContext(ioDispatcher) {
                    gameRepository.recordBoardCompleted(
                        board = updatedBoard,
                        cascadedCountOnFinalMove = submission.cascadedAutoSolvedSlotIds.size
                    )
                }
                val shareText = ShareSummaryBuilder.buildShareText(updatedBoard)
                _uiState.update {
                    it.copy(
                        board = updatedBoard,
                        currentInputMap = nextInput,
                        cursorIndex = nextCursor,
                        keyboardState = nextKeyboard,
                        bannerMessage = statusBanner,
                        newlyUnlockedCoords = submission.newlyUnlockedCoords,
                        stats = updatedStats,
                        showEndGameDialog = true,
                        shareSummaryText = shareText
                    )
                }
            }
        } else {
            val revealedRowIdx = if (submission.outcome == CascadeEngine.GuessOutcome.CONTINUE_SLOT) {
                nextSlot.guesses.size - 1
            } else {
                -1
            }

            _uiState.update {
                it.copy(
                    board = updatedBoard,
                    currentInputMap = nextInput,
                    cursorIndex = nextCursor,
                    keyboardState = nextKeyboard,
                    bannerMessage = statusBanner,
                    lastRevealedRowIndex = revealedRowIdx,
                    newlyUnlockedCoords = submission.newlyUnlockedCoords
                )
            }
        }
    }

    fun toggleStatsSheet(show: Boolean) {
        _uiState.update { it.copy(showStatsSheet = show) }
    }

    fun toggleHowToPlayDialog(show: Boolean) {
        _uiState.update { it.copy(showHowToPlayDialog = show) }
    }

    fun dismissEndGameDialog() {
        _uiState.update { it.copy(showEndGameDialog = false) }
    }

    fun showEndGameSummary() {
        val board = _uiState.value.board ?: return
        val shareText = ShareSummaryBuilder.buildShareText(board)
        _uiState.update {
            it.copy(
                showEndGameDialog = true,
                shareSummaryText = shareText
            )
        }
    }

    private fun applyBoardToUiState(
        board: PuzzleBoardState,
        stats: com.crosswordie.app.data.repository.PlayerStats,
        bannerMessage: String?,
        showEndDialog: Boolean
    ) {
        val slot = board.activeSlot
        val prefilled = slot?.lockedIndices?.let { HashMap(it) } ?: emptyMap()
        val firstCursor = if (slot != null) findFirstUnlockedIndex(slot, 0) else 0
        val keyboard = if (slot != null) {
            WordleEvaluator.computeKeyboardState(slot.guesses, slot.lockedIndices)
        } else {
            emptyMap()
        }
        val shareText = if (board.isBoardComplete) {
            ShareSummaryBuilder.buildShareText(board)
        } else {
            ""
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                showOpeningMenu = false,
                showPauseDialog = false,
                selectedDifficulty = board.difficulty,
                board = board,
                currentInputMap = prefilled,
                cursorIndex = firstCursor,
                keyboardState = keyboard,
                bannerMessage = bannerMessage,
                newlyUnlockedCoords = emptySet(),
                stats = stats,
                showEndGameDialog = showEndDialog,
                shareSummaryText = shareText
            )
        }
    }

    private fun persistBoardAsync(board: PuzzleBoardState) {
        viewModelScope.launch(ioDispatcher) {
            gameRepository.saveBoard(board)
        }
    }

    private fun createFreshBoard(
        generator: CrosswordGenerator,
        mode: GameMode,
        difficulty: DifficultyLevel,
        endlessLevel: Int,
        salt: Long = 0L
    ): PuzzleBoardState {
        return when (mode) {
            GameMode.DAILY -> {
                val today = LocalDate.now()
                val dailySeed = (today.toEpochDay() * GOLDEN_GAMMA) xor
                    (difficulty.ordinal.toLong() * 0x517CC1B727220A95L) xor salt
                generator.generate(
                    seed = dailySeed,
                    gameMode = GameMode.DAILY,
                    difficulty = difficulty,
                    endlessLevel = 1,
                    dailyDateString = today.toString(),
                    prefer5x5 = difficulty.prefer5x5
                )
            }
            GameMode.ENDLESS -> {
                val levelSeed = (endlessLevel.toLong() * GOLDEN_GAMMA) xor
                    (difficulty.ordinal.toLong() * 0x2545F4914F6CDD1DL) xor salt
                generator.generate(
                    seed = levelSeed,
                    gameMode = GameMode.ENDLESS,
                    difficulty = difficulty,
                    endlessLevel = endlessLevel,
                    dailyDateString = null,
                    prefer5x5 = difficulty.prefer5x5
                )
            }
        }
    }

    private fun findFirstUnlockedIndex(slot: WordSlot, startFrom: Int): Int {
        for (i in startFrom until slot.length) {
            if (!slot.lockedIndices.containsKey(i)) return i
        }
        for (i in 0 until startFrom.coerceAtMost(slot.length)) {
            if (!slot.lockedIndices.containsKey(i)) return i
        }
        return 0
    }

    private fun findFirstEmptyUnlockedIndex(slot: WordSlot, inputMap: Map<Int, Char>): Int? {
        for (i in 0 until slot.length) {
            if (!slot.lockedIndices.containsKey(i) && !inputMap.containsKey(i)) {
                return i
            }
        }
        return null
    }

    private fun findNextUnlockedIndexAfter(
        slot: WordSlot,
        inputMap: Map<Int, Char>,
        justTypedIndex: Int
    ): Int {
        for (i in (justTypedIndex + 1) until slot.length) {
            if (!slot.lockedIndices.containsKey(i) && !inputMap.containsKey(i)) {
                return i
            }
        }
        for (i in 0..justTypedIndex) {
            if (!slot.lockedIndices.containsKey(i) && !inputMap.containsKey(i)) {
                return i
            }
        }
        for (i in (slot.length - 1) downTo 0) {
            if (!slot.lockedIndices.containsKey(i)) return i
        }
        return justTypedIndex
    }

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15L

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GameViewModel(
                        wordRepository = AssetWordRepository(appContext),
                        gameRepository = GameRepository(appContext)
                    ) as T
                }
            }
        }
    }
}
