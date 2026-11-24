package com.example.unimate.ui.studyplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.SaveStudyPlannerSettingsRequest
import com.example.unimate.data.model.SaveStudySessionRequest
import com.example.unimate.data.model.StudyPlannerCalculator
import com.example.unimate.data.model.StudyPlannerSettings
import com.example.unimate.data.model.StudyProductivityStats
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.model.StudySessionMode
import com.example.unimate.data.repository.NotesAndStudyPlannerRepository
import com.example.unimate.data.repository.PersistedPomodoroState
import com.example.unimate.data.repository.StudyPlannerLocalCache
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface StudyPlannerUiState {
    data object Loading : StudyPlannerUiState
    data class Empty(
        val settings: StudyPlannerSettings,
        val stats: StudyProductivityStats,
    ) : StudyPlannerUiState

    data class Success(
        val allSessions: List<StudySession>,
        val plannedSessions: List<StudySession>,
        val completedSessions: List<StudySession>,
        val subjects: List<String>,
        val selectedSubjectFilter: String?,
        val settings: StudyPlannerSettings,
        val stats: StudyProductivityStats,
    ) : StudyPlannerUiState

    data class Error(val message: String) : StudyPlannerUiState
}

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val selectedSubject: String = "General Study",
    val sessionTitle: String = "",
    val linkedSessionId: String? = null,
    val focusMinutes: Long = 25L,
    val breakMinutes: Long = 5L,
) {
    fun progressFraction(): Float {
        if (totalSeconds <= 0) return 0f
        return (1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())).coerceIn(0f, 1f)
    }

    fun formattedClock(): String {
        val safe = remainingSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }
}

data class StudySessionEditorState(
    val isOpen: Boolean = false,
    val editingSessionId: String? = null,
    val draftSessionId: String = "",
    val title: String = "",
    val subject: String = "",
    val durationMinutesText: String = "45",
    val sessionMode: StudySessionMode = StudySessionMode.PLANNED,
    val completed: Boolean = false,
    val scheduledAtMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class PlannerGoalsEditorState(
    val isOpen: Boolean = false,
    val dailyGoalMinutesText: String = "120",
    val weeklyGoalMinutesText: String = "840",
    val pomodoroFocusMinutesText: String = "25",
    val pomodoroBreakMinutesText: String = "5",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StudyPlannerViewModel(
    private val uid: String,
    private val repository: NotesAndStudyPlannerRepository,
    private val localCache: StudyPlannerLocalCache? = null,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val subjectFilter = MutableStateFlow<String?>(null)

    private val _pomodoroState = MutableStateFlow(PomodoroUiState())
    val pomodoroState: StateFlow<PomodoroUiState> = _pomodoroState.asStateFlow()

    private val _sessionEditorState = MutableStateFlow(StudySessionEditorState())
    val sessionEditorState: StateFlow<StudySessionEditorState> = _sessionEditorState.asStateFlow()

    private val _goalsEditorState = MutableStateFlow(PlannerGoalsEditorState())
    val goalsEditorState: StateFlow<PlannerGoalsEditorState> = _goalsEditorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var tickerJob: Job? = null
    private var targetEndEpochMillis: Long = 0L

    private val rawDataFlow = retryTrigger.flatMapLatest {
        combine(
            repository.observeStudySessions(uid),
            repository.observePlannerSettings(uid),
        ) { sessions, settings ->
            Result.success(sessions to settings)
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(Result.failure(firestoreError ?: error))
        }
    }

    val uiState: StateFlow<StudyPlannerUiState> = combine(
        rawDataFlow,
        subjectFilter,
    ) { result, selectedSubject ->
        result.fold(
            onFailure = { error ->
                StudyPlannerUiState.Error(error.localizedMessage ?: "Failed to load study planner.")
            },
            onSuccess = { (allSessions, settings) ->
                val stats = StudyPlannerCalculator.computeStats(
                    sessions = allSessions,
                    settings = settings,
                    nowMillis = clockMillis(),
                )
                if (allSessions.isEmpty()) {
                    StudyPlannerUiState.Empty(
                        settings = settings,
                        stats = stats,
                    )
                } else {
                    val subjects = allSessions
                        .map { it.subject.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                    val activeFilter = selectedSubject?.takeIf { subjects.contains(it) }
                    val filtered = if (activeFilter == null) {
                        allSessions
                    } else {
                        allSessions.filter { it.subject.equals(activeFilter, ignoreCase = true) }
                    }

                    val planned = filtered
                        .filter { !it.completed }
                        .sortedBy { it.scheduledAtMillis }
                    val completed = filtered
                        .filter { it.completed }
                        .sortedByDescending { it.effectiveTimestampMillis() }

                    StudyPlannerUiState.Success(
                        allSessions = allSessions,
                        plannedSessions = planned,
                        completedSessions = completed,
                        subjects = subjects,
                        selectedSubjectFilter = activeFilter,
                        settings = settings,
                        stats = stats,
                    )
                }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = StudyPlannerUiState.Loading,
    )

    init {
        restorePomodoroFromCache()
    }

    private fun restorePomodoroFromCache() {
        val cache = localCache ?: return
        viewModelScope.launch {
            val saved = cache.loadPomodoroState(uid) ?: return@launch
            if (saved.running && saved.targetEndEpochMillis > 0L) {
                val now = clockMillis()
                val remaining = ((saved.targetEndEpochMillis - now) / 1000L).toInt().coerceAtLeast(0)
                if (remaining == 0) {
                    if (saved.phase == PomodoroPhase.FOCUS) {
                        logCompletedPomodoroSession(
                            subject = saved.selectedSubject,
                            title = saved.sessionTitle,
                            durationMinutes = saved.focusMinutes,
                            linkedSessionId = saved.linkedSessionId,
                            completedAtMillis = saved.targetEndEpochMillis,
                        )
                        val breakTotal = (saved.breakMinutes * 60L).toInt().coerceAtLeast(60)
                        _pomodoroState.value = PomodoroUiState(
                            phase = PomodoroPhase.BREAK,
                            running = false,
                            remainingSeconds = breakTotal,
                            totalSeconds = breakTotal,
                            selectedSubject = saved.selectedSubject,
                            sessionTitle = saved.sessionTitle,
                            linkedSessionId = null,
                            focusMinutes = saved.focusMinutes,
                            breakMinutes = saved.breakMinutes,
                        )
                    } else {
                        val focusTotal = (saved.focusMinutes * 60L).toInt().coerceAtLeast(60)
                        _pomodoroState.value = PomodoroUiState(
                            phase = PomodoroPhase.FOCUS,
                            running = false,
                            remainingSeconds = focusTotal,
                            totalSeconds = focusTotal,
                            selectedSubject = saved.selectedSubject,
                            sessionTitle = saved.sessionTitle,
                            linkedSessionId = null,
                            focusMinutes = saved.focusMinutes,
                            breakMinutes = saved.breakMinutes,
                        )
                    }
                    persistPomodoroState()
                } else {
                    targetEndEpochMillis = saved.targetEndEpochMillis
                    _pomodoroState.value = PomodoroUiState(
                        phase = saved.phase,
                        running = true,
                        remainingSeconds = remaining,
                        totalSeconds = saved.totalSeconds,
                        selectedSubject = saved.selectedSubject,
                        sessionTitle = saved.sessionTitle,
                        linkedSessionId = saved.linkedSessionId,
                        focusMinutes = saved.focusMinutes,
                        breakMinutes = saved.breakMinutes,
                    )
                    launchTicker()
                }
            } else {
                _pomodoroState.value = PomodoroUiState(
                    phase = saved.phase,
                    running = false,
                    remainingSeconds = saved.remainingSeconds.coerceAtLeast(1),
                    totalSeconds = saved.totalSeconds.coerceAtLeast(60),
                    selectedSubject = saved.selectedSubject,
                    sessionTitle = saved.sessionTitle,
                    linkedSessionId = saved.linkedSessionId,
                    focusMinutes = saved.focusMinutes,
                    breakMinutes = saved.breakMinutes,
                )
            }
        }
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun selectSubjectFilter(subject: String?) {
        subjectFilter.value = subject
    }

    // region Pomodoro Timer Controls
    fun selectPomodoroSubject(subject: String) {
        val clean = subject.trim().ifBlank { "General Study" }
        _pomodoroState.update { it.copy(selectedSubject = clean) }
        persistPomodoroAsync()
    }

    fun onPomodoroTitleChanged(title: String) {
        _pomodoroState.update { it.copy(sessionTitle = title) }
        persistPomodoroAsync()
    }

    fun selectPomodoroDuration(focusMinutes: Long, breakMinutes: Long = _pomodoroState.value.breakMinutes) {
        val safeFocus = focusMinutes.coerceIn(5L, 120L)
        val safeBreak = breakMinutes.coerceIn(1L, 60L)
        val state = _pomodoroState.value
        if (state.running) return
        val newTotal = if (state.phase == PomodoroPhase.FOCUS) {
            (safeFocus * 60L).toInt()
        } else {
            (safeBreak * 60L).toInt()
        }
        _pomodoroState.update {
            it.copy(
                focusMinutes = safeFocus,
                breakMinutes = safeBreak,
                remainingSeconds = newTotal,
                totalSeconds = newTotal,
            )
        }
        persistPomodoroAsync()
    }

    fun launchSessionInPomodoro(session: StudySession) {
        tickerJob?.cancel()
        targetEndEpochMillis = 0L
        val focusMins = session.durationMinutes.coerceIn(5L, 120L)
        val totalSecs = (focusMins * 60L).toInt()
        _pomodoroState.update {
            it.copy(
                phase = PomodoroPhase.FOCUS,
                running = true,
                remainingSeconds = totalSecs,
                totalSeconds = totalSecs,
                selectedSubject = session.subject.ifBlank { "General Study" },
                sessionTitle = session.title,
                linkedSessionId = session.id,
                focusMinutes = focusMins,
            )
        }
        targetEndEpochMillis = clockMillis() + (totalSecs * 1000L)
        persistPomodoroAsync()
        launchTicker()
        _actionMessage.value = "Pomodoro started for ${session.subject}."
    }

    fun startPomodoro() {
        val current = _pomodoroState.value
        if (current.running) return
        val secs = current.remainingSeconds.coerceAtLeast(1)
        targetEndEpochMillis = clockMillis() + (secs * 1000L)
        _pomodoroState.update { it.copy(running = true, remainingSeconds = secs) }
        persistPomodoroAsync()
        launchTicker()
    }

    fun pausePomodoro() {
        tickerJob?.cancel()
        val now = clockMillis()
        val remaining = if (targetEndEpochMillis > now) {
            ((targetEndEpochMillis - now) / 1000L).toInt().coerceAtLeast(1)
        } else {
            _pomodoroState.value.remainingSeconds
        }
        targetEndEpochMillis = 0L
        _pomodoroState.update { it.copy(running = false, remainingSeconds = remaining) }
        persistPomodoroAsync()
    }

    fun resetPomodoro() {
        tickerJob?.cancel()
        targetEndEpochMillis = 0L
        val current = _pomodoroState.value
        val fullSeconds = if (current.phase == PomodoroPhase.FOCUS) {
            (current.focusMinutes * 60L).toInt()
        } else {
            (current.breakMinutes * 60L).toInt()
        }
        _pomodoroState.update {
            it.copy(
                running = false,
                remainingSeconds = fullSeconds,
                totalSeconds = fullSeconds,
            )
        }
        persistPomodoroAsync()
    }

    fun switchPomodoroPhase(phase: PomodoroPhase) {
        tickerJob?.cancel()
        targetEndEpochMillis = 0L
        val current = _pomodoroState.value
        val totalSecs = if (phase == PomodoroPhase.FOCUS) {
            (current.focusMinutes * 60L).toInt()
        } else {
            (current.breakMinutes * 60L).toInt()
        }
        _pomodoroState.update {
            it.copy(
                phase = phase,
                running = false,
                remainingSeconds = totalSecs,
                totalSeconds = totalSecs,
            )
        }
        persistPomodoroAsync()
    }

    fun completePomodoroNow() {
        tickerJob?.cancel()
        targetEndEpochMillis = 0L
        val current = _pomodoroState.value
        val now = clockMillis()
        if (current.phase == PomodoroPhase.FOCUS) {
            val elapsedSeconds = (current.totalSeconds - current.remainingSeconds).coerceAtLeast(0)
            val loggedMinutes = if (elapsedSeconds >= 60) {
                (elapsedSeconds / 60L).coerceIn(1L, 600L)
            } else {
                current.focusMinutes.coerceIn(1L, 600L)
            }
            viewModelScope.launch {
                logCompletedPomodoroSession(
                    subject = current.selectedSubject,
                    title = current.sessionTitle,
                    durationMinutes = loggedMinutes,
                    linkedSessionId = current.linkedSessionId,
                    completedAtMillis = now,
                )
            }
            val breakTotal = (current.breakMinutes * 60L).toInt().coerceAtLeast(60)
            _pomodoroState.update {
                it.copy(
                    phase = PomodoroPhase.BREAK,
                    running = false,
                    remainingSeconds = breakTotal,
                    totalSeconds = breakTotal,
                    linkedSessionId = null,
                )
            }
            _actionMessage.value = syncCoordinator.formatActionMessage(
                "Logged ${loggedMinutes}m Pomodoro session for ${current.selectedSubject}!",
            )
        } else {
            val focusTotal = (current.focusMinutes * 60L).toInt().coerceAtLeast(60)
            _pomodoroState.update {
                it.copy(
                    phase = PomodoroPhase.FOCUS,
                    running = false,
                    remainingSeconds = focusTotal,
                    totalSeconds = focusTotal,
                )
            }
            _actionMessage.value = "Break finished. Ready for next focus session!"
        }
        persistPomodoroAsync()
    }

    private fun launchTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                val now = clockMillis()
                val remaining = ((targetEndEpochMillis - now) / 1000L).toInt()
                if (remaining <= 0) {
                    onPomodoroTimerFinishedNaturally()
                    break
                } else {
                    _pomodoroState.update { it.copy(remainingSeconds = remaining) }
                }
            }
        }
    }

    private suspend fun onPomodoroTimerFinishedNaturally() {
        targetEndEpochMillis = 0L
        val current = _pomodoroState.value
        val now = clockMillis()
        if (current.phase == PomodoroPhase.FOCUS) {
            logCompletedPomodoroSession(
                subject = current.selectedSubject,
                title = current.sessionTitle,
                durationMinutes = current.focusMinutes,
                linkedSessionId = current.linkedSessionId,
                completedAtMillis = now,
            )
            val breakTotal = (current.breakMinutes * 60L).toInt().coerceAtLeast(60)
            _pomodoroState.update {
                it.copy(
                    phase = PomodoroPhase.BREAK,
                    running = false,
                    remainingSeconds = breakTotal,
                    totalSeconds = breakTotal,
                    linkedSessionId = null,
                )
            }
            _actionMessage.value = syncCoordinator.formatActionMessage(
                "Focus cycle complete! Logged ${current.focusMinutes}m for ${current.selectedSubject}.",
            )
        } else {
            val focusTotal = (current.focusMinutes * 60L).toInt().coerceAtLeast(60)
            _pomodoroState.update {
                it.copy(
                    phase = PomodoroPhase.FOCUS,
                    running = false,
                    remainingSeconds = focusTotal,
                    totalSeconds = focusTotal,
                )
            }
            _actionMessage.value = "Break finished! Start another Pomodoro focus session."
        }
        persistPomodoroState()
    }

    private suspend fun logCompletedPomodoroSession(
        subject: String,
        title: String,
        durationMinutes: Long,
        linkedSessionId: String?,
        completedAtMillis: Long,
    ) {
        val cleanSubject = subject.trim().ifBlank { "General Study" }
        val cleanTitle = title.trim().ifBlank { "$cleanSubject Focus Session" }
        if (!linkedSessionId.isNullOrBlank()) {
            repository.markStudySessionCompleted(
                uid = uid,
                sessionId = linkedSessionId,
                completed = true,
                completedAtMillis = completedAtMillis,
            )
        } else {
            val deterministicId = "pomo_${completedAtMillis}_${cleanSubject.hashCode().toUInt()}"
            repository.createStudySessionWithId(
                uid = uid,
                sessionId = deterministicId,
                request = SaveStudySessionRequest(
                    title = cleanTitle,
                    subject = cleanSubject,
                    durationMinutes = durationMinutes.coerceIn(1L, 600L),
                    sessionMode = StudySessionMode.POMODORO,
                    completed = true,
                    scheduledAtMillis = completedAtMillis,
                    completedAtMillis = completedAtMillis,
                    studyDateKey = StudyPlannerCalculator.toDateKey(completedAtMillis),
                    notes = "Completed via UniMate Pomodoro Timer",
                ),
            )
        }
    }

    private fun persistPomodoroAsync() {
        viewModelScope.launch {
            persistPomodoroState()
        }
    }

    private suspend fun persistPomodoroState() {
        val cache = localCache ?: return
        val current = _pomodoroState.value
        cache.savePomodoroState(
            uid = uid,
            state = PersistedPomodoroState(
                phase = current.phase,
                running = current.running,
                remainingSeconds = current.remainingSeconds,
                totalSeconds = current.totalSeconds,
                targetEndEpochMillis = if (current.running) targetEndEpochMillis else 0L,
                selectedSubject = current.selectedSubject,
                sessionTitle = current.sessionTitle,
                linkedSessionId = current.linkedSessionId,
                focusMinutes = current.focusMinutes,
                breakMinutes = current.breakMinutes,
            ),
        )
    }
    // endregion

    // region Study Session CRUD
    fun openAddSessionDialog(completedByDefault: Boolean = false, defaultSubject: String = "") {
        _sessionEditorState.value = StudySessionEditorState(
            isOpen = true,
            draftSessionId = UUID.randomUUID().toString(),
            subject = defaultSubject.trim(),
            completed = completedByDefault,
            sessionMode = if (completedByDefault) StudySessionMode.MANUAL else StudySessionMode.PLANNED,
            scheduledAtMillis = clockMillis(),
        )
    }

    fun openEditSessionDialog(session: StudySession) {
        _sessionEditorState.value = StudySessionEditorState(
            isOpen = true,
            editingSessionId = session.id,
            title = session.title,
            subject = session.subject,
            durationMinutesText = session.durationMinutes.toString(),
            sessionMode = session.resolvedMode(),
            completed = session.completed,
            scheduledAtMillis = session.scheduledAtMillis.takeIf { it > 0L } ?: clockMillis(),
            notes = session.notes,
        )
    }

    fun dismissSessionDialog() {
        _sessionEditorState.value = StudySessionEditorState()
    }

    fun onSessionTitleChanged(value: String) {
        _sessionEditorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onSessionSubjectChanged(value: String) {
        _sessionEditorState.update { it.copy(subject = value, errorMessage = null) }
    }

    fun onSessionDurationChanged(value: String) {
        _sessionEditorState.update {
            it.copy(durationMinutesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onSessionCompletedChanged(completed: Boolean) {
        _sessionEditorState.update {
            it.copy(
                completed = completed,
                sessionMode = if (completed && it.sessionMode == StudySessionMode.PLANNED) {
                    StudySessionMode.MANUAL
                } else if (!completed) {
                    StudySessionMode.PLANNED
                } else {
                    it.sessionMode
                },
            )
        }
    }

    fun onSessionScheduledAtChanged(millis: Long) {
        _sessionEditorState.update { it.copy(scheduledAtMillis = millis, errorMessage = null) }
    }

    fun onSessionNotesChanged(value: String) {
        _sessionEditorState.update { it.copy(notes = value, errorMessage = null) }
    }

    fun saveStudySession() {
        val draft = _sessionEditorState.value
        if (draft.isSaving) return

        val cleanTitle = draft.title.trim()
        val cleanSubject = draft.subject.trim()
        val duration = draft.durationMinutesText.toLongOrNull() ?: 0L

        if (cleanTitle.isEmpty()) {
            _sessionEditorState.update { it.copy(errorMessage = "Enter a study session topic or title.") }
            return
        }
        if (cleanSubject.isEmpty()) {
            _sessionEditorState.update { it.copy(errorMessage = "Select or enter a subject/course.") }
            return
        }
        if (duration !in 1L..600L) {
            _sessionEditorState.update { it.copy(errorMessage = "Session duration must be between 1 and 600 minutes.") }
            return
        }

        val now = clockMillis()
        val targetMillis = draft.scheduledAtMillis.takeIf { it > 0L } ?: now
        val completedAt = if (draft.completed) targetMillis else 0L
        val dateKey = StudyPlannerCalculator.toDateKey(if (draft.completed) completedAt else targetMillis)

        val request = SaveStudySessionRequest(
            title = cleanTitle,
            subject = cleanSubject,
            durationMinutes = duration,
            sessionMode = draft.sessionMode,
            completed = draft.completed,
            scheduledAtMillis = targetMillis,
            completedAtMillis = completedAt,
            studyDateKey = dateKey,
            notes = draft.notes.trim(),
        )

        _sessionEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = if (draft.editingSessionId == null) {
                repository.createStudySessionWithId(
                    uid = uid,
                    sessionId = draft.draftSessionId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                ).map { Unit }
            } else {
                repository.updateStudySession(uid, draft.editingSessionId, request)
            }
            result.fold(
                onSuccess = {
                    _sessionEditorState.value = StudySessionEditorState()
                    val baseMessage = if (draft.editingSessionId == null) {
                        if (draft.completed) "Completed study session logged." else "Study session scheduled."
                    } else {
                        "Study session updated."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { error ->
                    _sessionEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to save study session.",
                        )
                    }
                },
            )
        }
    }

    fun toggleSessionCompleted(session: StudySession) {
        viewModelScope.launch {
            val nextCompleted = !session.completed
            val completionTime = if (nextCompleted) clockMillis() else session.scheduledAtMillis.takeIf { it > 0L } ?: clockMillis()
            repository.markStudySessionCompleted(
                uid = uid,
                sessionId = session.id,
                completed = nextCompleted,
                completedAtMillis = completionTime,
            ).fold(
                onSuccess = {
                    val baseMessage = if (nextCompleted) {
                        "Session marked completed! Added ${session.durationMinutes}m."
                    } else {
                        "Moved back to planned sessions."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not update session."
                },
            )
        }
    }

    fun deleteSession(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            repository.deleteStudySession(uid, sessionId).fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage("Study session removed.")
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Could not remove session."
                },
            )
        }
    }
    // endregion

    // region Study Goals & Pomodoro Settings
    fun openGoalsDialog() {
        val currentSettings = when (val state = uiState.value) {
            is StudyPlannerUiState.Success -> state.settings
            is StudyPlannerUiState.Empty -> state.settings
            else -> StudyPlannerSettings(uid = uid)
        }
        _goalsEditorState.value = PlannerGoalsEditorState(
            isOpen = true,
            dailyGoalMinutesText = currentSettings.dailyGoalMinutes.toString(),
            weeklyGoalMinutesText = currentSettings.weeklyGoalMinutes.toString(),
            pomodoroFocusMinutesText = currentSettings.pomodoroFocusMinutes.toString(),
            pomodoroBreakMinutesText = currentSettings.pomodoroBreakMinutes.toString(),
        )
    }

    fun dismissGoalsDialog() {
        _goalsEditorState.value = PlannerGoalsEditorState()
    }

    fun onDailyGoalMinutesChanged(value: String) {
        _goalsEditorState.update {
            it.copy(dailyGoalMinutesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onWeeklyGoalMinutesChanged(value: String) {
        _goalsEditorState.update {
            it.copy(weeklyGoalMinutesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onFocusMinutesSettingChanged(value: String) {
        _goalsEditorState.update {
            it.copy(pomodoroFocusMinutesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun onBreakMinutesSettingChanged(value: String) {
        _goalsEditorState.update {
            it.copy(pomodoroBreakMinutesText = value.filter { ch -> ch.isDigit() }, errorMessage = null)
        }
    }

    fun saveGoalSettings() {
        val draft = _goalsEditorState.value
        if (draft.isSaving) return

        val daily = draft.dailyGoalMinutesText.toLongOrNull() ?: 0L
        val weekly = draft.weeklyGoalMinutesText.toLongOrNull() ?: 0L
        val focus = draft.pomodoroFocusMinutesText.toLongOrNull() ?: 0L
        val brk = draft.pomodoroBreakMinutesText.toLongOrNull() ?: 0L

        if (daily !in 15L..720L) {
            _goalsEditorState.update { it.copy(errorMessage = "Daily goal must be between 15 and 720 minutes.") }
            return
        }
        if (weekly !in 30L..5040L) {
            _goalsEditorState.update { it.copy(errorMessage = "Weekly goal must be between 30 and 5040 minutes.") }
            return
        }
        if (focus !in 5L..120L) {
            _goalsEditorState.update { it.copy(errorMessage = "Pomodoro focus must be between 5 and 120 minutes.") }
            return
        }
        if (brk !in 1L..60L) {
            _goalsEditorState.update { it.copy(errorMessage = "Pomodoro break must be between 1 and 60 minutes.") }
            return
        }

        _goalsEditorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val request = SaveStudyPlannerSettingsRequest(
                dailyGoalMinutes = daily,
                weeklyGoalMinutes = weekly,
                pomodoroFocusMinutes = focus,
                pomodoroBreakMinutes = brk,
            )
            repository.savePlannerSettings(uid, request).fold(
                onSuccess = {
                    _goalsEditorState.value = PlannerGoalsEditorState()
                    if (!_pomodoroState.value.running) {
                        selectPomodoroDuration(focus, brk)
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        "Study goals & Pomodoro defaults updated.",
                    )
                },
                onFailure = { error ->
                    _goalsEditorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Failed to update study goals.",
                        )
                    }
                },
            )
        }
    }
    // endregion

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(
            uid: String,
            repository: NotesAndStudyPlannerRepository,
            localCache: StudyPlannerLocalCache? = null,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StudyPlannerViewModel(
                    uid = uid,
                    repository = repository,
                    localCache = localCache,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
