package com.example.unimate

import com.example.unimate.data.model.NoteAttachment
import com.example.unimate.data.model.NotesAndPlannerMappers
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.SaveNoteRequest
import com.example.unimate.data.model.SaveStudyPlannerSettingsRequest
import com.example.unimate.data.model.SaveStudySessionRequest
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.StudyPlannerCalculator
import com.example.unimate.data.model.StudyPlannerSettings
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.model.StudySessionMode
import com.example.unimate.data.repository.NotesAndStudyPlannerRepository
import com.example.unimate.data.repository.PersistedPomodoroState
import com.example.unimate.data.repository.StudyPlannerLocalCache
import com.example.unimate.ui.notes.NotesQuickFilter
import com.example.unimate.ui.notes.NotesUiState
import com.example.unimate.ui.notes.NotesViewModel
import com.example.unimate.ui.studyplanner.StudyPlannerUiState
import com.example.unimate.ui.studyplanner.StudyPlannerViewModel
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class Phase11NotesAndStudyPlannerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun utcDateMillis(year: Int, month: Int, day: Int, hour: Int = 14): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
    }

    @Test
    fun `study planner calculator computes streaks weekly bars and subject breakdown`() {
        val tz = TimeZone.getTimeZone("UTC")
        // Wednesday 2026-11-25
        val nowMillis = utcDateMillis(2026, 11, 25, 16)
        val settings = StudyPlannerSettings(
            uid = "u1",
            dailyGoalMinutes = 120L,
            weeklyGoalMinutes = 600L,
            pomodoroFocusMinutes = 25L,
            pomodoroBreakMinutes = 5L,
        )

        val sessions = listOf(
            // Streak 1 in the past: Nov 10, 11, 12, 13 (longest = 4 days)
            StudySession(id = "p1", subject = "CS-301", durationMinutes = 30L, completed = true, studyDateKey = "2026-11-10"),
            StudySession(id = "p2", subject = "CS-301", durationMinutes = 30L, completed = true, studyDateKey = "2026-11-11"),
            StudySession(id = "p3", subject = "CS-301", durationMinutes = 30L, completed = true, studyDateKey = "2026-11-12"),
            StudySession(id = "p4", subject = "CS-301", durationMinutes = 30L, completed = true, studyDateKey = "2026-11-13"),
            // Current week Mon 23 + Tue 24 + Wed 25 (current streak = 3 days)
            StudySession(id = "c1", subject = "Algorithms", durationMinutes = 60L, completed = true, studyDateKey = "2026-11-23"),
            StudySession(id = "c2", subject = "Operating Systems", durationMinutes = 45L, completed = true, studyDateKey = "2026-11-24"),
            StudySession(id = "c3", subject = "Algorithms", durationMinutes = 50L, completed = true, studyDateKey = "2026-11-25"),
            StudySession(id = "c4", subject = "Algorithms", durationMinutes = 25L, completed = true, studyDateKey = "2026-11-25"),
            // Planned session should NOT count toward streaks or completed totals
            StudySession(id = "planned1", subject = "Databases", durationMinutes = 90L, completed = false, studyDateKey = "2026-11-25"),
        )

        val stats = StudyPlannerCalculator.computeStats(
            sessions = sessions,
            settings = settings,
            nowMillis = nowMillis,
            timeZone = tz,
        )

        assertEquals(75L, stats.todayMinutes)
        assertEquals(62, stats.dailyGoalPercent)
        assertEquals(180L, stats.weeklyMinutes)
        assertEquals(30, stats.weeklyGoalPercent)
        assertEquals(8, stats.completedSessionsCount)
        assertEquals(1, stats.plannedSessionsCount)
        assertEquals(300L, stats.totalCompletedMinutes)
        assertEquals(3, stats.currentStreakDays)
        assertEquals(4, stats.longestStreakDays)

        assertEquals(7, stats.weeklyBars.size)
        assertEquals("Mon", stats.weeklyBars[0].dayLabel)
        assertEquals(60L, stats.weeklyBars[0].minutes)
        assertEquals("Wed", stats.weeklyBars[2].dayLabel)
        assertEquals(75L, stats.weeklyBars[2].minutes)
        assertTrue(stats.weeklyBars[2].isToday)

        assertEquals("Algorithms", stats.subjectBreakdown[0].subject)
        assertEquals(135L, stats.subjectBreakdown[0].totalMinutes)
    }

    @Test
    fun `streak stays alive if studied yesterday and resets if two days missed`() {
        val tz = TimeZone.getTimeZone("UTC")
        val wednesday = utcDateMillis(2026, 11, 25, 10)

        val yesterdayOnly = listOf(
            StudySession(id = "1", durationMinutes = 25L, completed = true, studyDateKey = "2026-11-23"),
            StudySession(id = "2", durationMinutes = 25L, completed = true, studyDateKey = "2026-11-24"),
        )
        assertEquals(2, StudyPlannerCalculator.computeCurrentStreakDays(yesterdayOnly, wednesday, tz))

        val brokenGap = listOf(
            StudySession(id = "1", durationMinutes = 25L, completed = true, studyDateKey = "2026-11-22"),
            StudySession(id = "2", durationMinutes = 25L, completed = true, studyDateKey = "2026-11-23"),
        )
        assertEquals(0, StudyPlannerCalculator.computeCurrentStreakDays(brokenGap, wednesday, tz))
        assertEquals(2, StudyPlannerCalculator.computeLongestStreakDays(brokenGap, tz))
    }

    @Test
    fun `mappers produce exact firestore schema payloads without id or is-prefixed booleans`() {
        val notePayload = NotesAndPlannerMappers.toCreateNotePayload(
            uid = "user-1",
            request = SaveNoteRequest(
                title = "  Trees & Heaps  ",
                courseName = " CS-301 ",
                content = " Binary heap build takes O(n) ",
                pinned = true,
                favorite = false,
                attachments = listOf(
                    NoteAttachment(
                        name = "heap_notes.pdf",
                        uri = "content://docs/42",
                        mimeType = "application/pdf",
                        sizeBytes = 20480L,
                    ),
                ),
            ),
            nowMillis = 123456L,
        )
        assertEquals("user-1", notePayload["uid"])
        assertEquals("Trees & Heaps", notePayload["title"])
        assertEquals(true, notePayload["pinned"])
        assertEquals(false, notePayload["favorite"])
        assertFalse(notePayload.containsKey("isPinned"))
        assertFalse(notePayload.containsKey("isFavorite"))
        assertFalse(notePayload.containsKey("id"))
        assertTrue(notePayload["createdAt"] is FieldValue)
        assertTrue(notePayload["updatedAt"] is FieldValue)

        val sessionPayload = NotesAndPlannerMappers.toCreateStudySessionPayload(
            uid = "user-1",
            request = SaveStudySessionRequest(
                title = "DP Practice",
                subject = "CS-301",
                durationMinutes = 45L,
                sessionMode = StudySessionMode.POMODORO,
                completed = true,
                scheduledAtMillis = 1000L,
                completedAtMillis = 2000L,
                studyDateKey = "2026-11-24",
                notes = "Memoization",
            ),
        )
        assertEquals("POMODORO", sessionPayload["sessionMode"])
        assertEquals(true, sessionPayload["completed"])
        assertEquals("2026-11-24", sessionPayload["studyDateKey"])
        assertFalse(sessionPayload.containsKey("isCompleted"))
    }

    @Test
    fun `notes viewmodel supports full CRUD search subject filter pin favorite and attachment rules`() = runTest(testDispatcher) {
        val repo = FakeNotesAndStudyPlannerRepository()
        val vm = NotesViewModel(uid = "uid-1", repository = repo)

        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value is NotesUiState.Empty)

        // Create note with attachment and link validation
        vm.openCreateDialog()
        vm.onTitleChanged("OS Deadlocks")
        vm.onSubjectChanged("CS-320")
        vm.onContentChanged("Coffman conditions: mutual exclusion, hold & wait, no preemption, circular wait.")
        vm.onEditorPinnedChanged(true)
        vm.onEditorFavoriteChanged(true)
        vm.addPickedAttachment("os_lecture.pdf", "content://media/os_lecture.pdf", "application/pdf", 51200L)

        // Invalid link rejected
        vm.onLinkDraftUriChanged("ftp://invalid")
        vm.addLinkAttachmentFromDraft()
        assertNotNull(vm.editorState.value.errorMessage)

        // Valid link added
        vm.onLinkDraftUriChanged("https://university.edu/os.pdf")
        vm.onLinkDraftNameChanged("Syllabus PDF")
        vm.addLinkAttachmentFromDraft()
        assertNull(vm.editorState.value.errorMessage)
        assertEquals(2, vm.editorState.value.attachments.size)

        vm.saveNote()
        advanceUntilIdle()

        val stateAfterCreate = vm.uiState.value as NotesUiState.Success
        assertEquals(1, stateAfterCreate.allNotes.size)
        val created = stateAfterCreate.allNotes.first()
        assertEquals("OS Deadlocks", created.title)
        assertTrue(created.pinned)
        assertTrue(created.favorite)
        assertEquals(2, created.attachments.size)

        // Add second note under different subject
        vm.openCreateDialog()
        vm.onTitleChanged("Normalization")
        vm.onSubjectChanged("CS-310")
        vm.onContentChanged("1NF, 2NF, 3NF, BCNF")
        vm.saveNote()
        advanceUntilIdle()

        val twoNotesState = vm.uiState.value as NotesUiState.Success
        assertEquals(2, twoNotesState.allNotes.size)
        assertEquals(listOf("CS-310", "CS-320"), twoNotesState.subjects)

        // Search filter
        vm.onSearchQueryChanged("Coffman")
        advanceUntilIdle()
        val searchState = vm.uiState.value as NotesUiState.Success
        assertEquals(1, searchState.filteredNotes.size)
        assertEquals("OS Deadlocks", searchState.filteredNotes.first().title)
        vm.onSearchQueryChanged("")

        // Subject filter + quick filter
        vm.selectSubjectFilter("CS-310")
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as NotesUiState.Success).filteredNotes.size)
        vm.selectSubjectFilter(null)

        vm.selectQuickFilter(NotesQuickFilter.PINNED)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as NotesUiState.Success).filteredNotes.size)
        vm.selectQuickFilter(NotesQuickFilter.ALL)

        // Update note
        vm.openEditDialog(created)
        vm.onTitleChanged("OS Deadlocks Updated")
        vm.saveNote()
        advanceUntilIdle()
        val updated = (vm.uiState.value as NotesUiState.Success).allNotes.first { it.id == created.id }
        assertEquals("OS Deadlocks Updated", updated.title)

        // Toggle pin & favorite
        vm.togglePinned(updated)
        vm.toggleFavorite(updated)
        advanceUntilIdle()
        val toggled = (vm.uiState.value as NotesUiState.Success).allNotes.first { it.id == created.id }
        assertFalse(toggled.pinned)
        assertFalse(toggled.favorite)

        // Delete both notes -> Empty state
        vm.deleteNote(created.id)
        val remainingId = (vm.uiState.value as NotesUiState.Success).allNotes.first { it.id != created.id }.id
        vm.deleteNote(remainingId)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is NotesUiState.Empty)

        collectJob.cancel()
    }

    @Test
    fun `notes viewmodel surfaces error state and recovers on retry`() = runTest(testDispatcher) {
        val repo = FakeNotesAndStudyPlannerRepository(failStream = true)
        val vm = NotesViewModel(uid = "uid-1", repository = repo)

        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value is NotesUiState.Error)

        repo.failStream = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is NotesUiState.Empty)

        collectJob.cancel()
    }

    @Test
    fun `study planner viewmodel handles sessions goals CRUD and pomodoro completion`() = runTest(testDispatcher) {
        var fakeClock = utcDateMillis(2026, 11, 25, 12)
        val repo = FakeNotesAndStudyPlannerRepository()
        val localCache = InMemoryStudyPlannerLocalCache()

        val vm = StudyPlannerViewModel(
            uid = "uid-1",
            repository = repo,
            localCache = localCache,
            clockMillis = { fakeClock },
        )

        val collectJob = launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value is StudyPlannerUiState.Empty)

        // Update daily & weekly goals
        vm.openGoalsDialog()
        vm.onDailyGoalMinutesChanged("90")
        vm.onWeeklyGoalMinutesChanged("540")
        vm.onFocusMinutesSettingChanged("30")
        vm.onBreakMinutesSettingChanged("5")
        vm.saveGoalSettings()
        advanceUntilIdle()

        val afterGoals = vm.uiState.value as StudyPlannerUiState.Empty
        assertEquals(90L, afterGoals.settings.dailyGoalMinutes)
        assertEquals(540L, afterGoals.settings.weeklyGoalMinutes)
        assertEquals(30L, vm.pomodoroState.value.focusMinutes)

        // Create planned study session
        vm.openAddSessionDialog(completedByDefault = false)
        vm.onSessionTitleChanged("Graph Traversals")
        vm.onSessionSubjectChanged("CS-301")
        vm.onSessionDurationChanged("45")
        vm.saveStudySession()
        advanceUntilIdle()

        val afterCreate = vm.uiState.value as StudyPlannerUiState.Success
        assertEquals(1, afterCreate.plannedSessions.size)
        assertEquals(0, afterCreate.completedSessions.size)
        val session = afterCreate.plannedSessions.first()

        // Edit session
        vm.openEditSessionDialog(session)
        vm.onSessionTitleChanged("Graph Traversals & Dijkstra")
        vm.saveStudySession()
        advanceUntilIdle()
        assertEquals(
            "Graph Traversals & Dijkstra",
            (vm.uiState.value as StudyPlannerUiState.Success).plannedSessions.first().title,
        )

        // Launch session into Pomodoro and complete it
        vm.launchSessionInPomodoro((vm.uiState.value as StudyPlannerUiState.Success).plannedSessions.first())
        assertTrue(vm.pomodoroState.value.running)
        assertEquals(PomodoroPhase.FOCUS, vm.pomodoroState.value.phase)
        assertNotNull(localCache.loadPomodoroState("uid-1"))

        fakeClock += 10 * 60_000L
        vm.pausePomodoro()
        assertFalse(vm.pomodoroState.value.running)

        vm.completePomodoroNow()
        advanceUntilIdle()

        assertEquals(PomodoroPhase.BREAK, vm.pomodoroState.value.phase)
        val afterComplete = vm.uiState.value as StudyPlannerUiState.Success
        assertEquals(0, afterComplete.plannedSessions.size)
        assertEquals(1, afterComplete.completedSessions.size)
        assertEquals(45L, afterComplete.stats.todayMinutes)
        assertEquals(50, afterComplete.stats.dailyGoalPercent)

        // Complete an ad-hoc Pomodoro cycle as well
        vm.switchPomodoroPhase(PomodoroPhase.FOCUS)
        vm.selectPomodoroSubject("MATH-202")
        vm.onPomodoroTitleChanged("Eigenvalues")
        vm.selectPomodoroDuration(25L)
        vm.completePomodoroNow()
        advanceUntilIdle()

        val afterSecond = vm.uiState.value as StudyPlannerUiState.Success
        assertEquals(2, afterSecond.completedSessions.size)
        assertEquals(70L, afterSecond.stats.todayMinutes)
        assertEquals(1, afterSecond.stats.currentStreakDays)

        // Delete one and toggle the other back to planned, then delete -> Empty
        vm.deleteSession(afterSecond.completedSessions[0].id)
        advanceUntilIdle()
        val remaining = (vm.uiState.value as StudyPlannerUiState.Success).completedSessions.first()
        vm.toggleSessionCompleted(remaining)
        advanceUntilIdle()
        assertEquals(1, (vm.uiState.value as StudyPlannerUiState.Success).plannedSessions.size)

        vm.deleteSession(remaining.id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is StudyPlannerUiState.Empty)

        collectJob.cancel()
    }
}

private class InMemoryStudyPlannerLocalCache : StudyPlannerLocalCache {
    private val pomodoro = mutableMapOf<String, PersistedPomodoroState>()
    private val notes = mutableMapOf<String, List<StudyNote>>()
    private val sessions = mutableMapOf<String, List<StudySession>>()
    private val settings = mutableMapOf<String, StudyPlannerSettings>()

    override suspend fun savePomodoroState(uid: String, state: PersistedPomodoroState) {
        pomodoro[uid] = state
    }

    override suspend fun loadPomodoroState(uid: String): PersistedPomodoroState? = pomodoro[uid]

    override suspend fun clearPomodoroState(uid: String) {
        pomodoro.remove(uid)
    }

    override suspend fun cacheNotes(uid: String, notes: List<StudyNote>) {
        this.notes[uid] = notes
    }

    override suspend fun loadCachedNotes(uid: String): List<StudyNote> = notes[uid].orEmpty()

    override suspend fun cacheSessions(uid: String, sessions: List<StudySession>) {
        this.sessions[uid] = sessions
    }

    override suspend fun loadCachedSessions(uid: String): List<StudySession> = sessions[uid].orEmpty()

    override suspend fun cacheSettings(uid: String, settings: StudyPlannerSettings) {
        this.settings[uid] = settings
    }

    override suspend fun loadCachedSettings(uid: String): StudyPlannerSettings? = settings[uid]
}

private class FakeNotesAndStudyPlannerRepository(
    var failStream: Boolean = false,
) : NotesAndStudyPlannerRepository {

    private val notesFlow = MutableStateFlow<List<StudyNote>>(emptyList())
    private val sessionsFlow = MutableStateFlow<List<StudySession>>(emptyList())
    private val settingsFlow = MutableStateFlow(StudyPlannerSettings(uid = "uid-1"))
    private var nextId = 1

    override fun observeNotes(uid: String): Flow<List<StudyNote>> = flow {
        if (failStream) throw IllegalStateException("Firestore unavailable")
        notesFlow.collect { emit(it) }
    }

    override suspend fun createNote(uid: String, request: SaveNoteRequest): Result<String> {
        val id = "note-${nextId++}"
        notesFlow.value = notesFlow.value + StudyNote(
            id = id,
            uid = uid,
            title = request.title.trim(),
            courseName = request.courseName.trim(),
            content = request.content.trim(),
            pinned = request.pinned,
            favorite = request.favorite,
            attachments = request.attachments,
            clientUpdatedAtMillis = 1000L + nextId,
        )
        return Result.success(id)
    }

    override suspend fun updateNote(uid: String, noteId: String, request: SaveNoteRequest): Result<Unit> {
        notesFlow.value = notesFlow.value.map {
            if (it.id == noteId) {
                it.copy(
                    title = request.title.trim(),
                    courseName = request.courseName.trim(),
                    content = request.content.trim(),
                    pinned = request.pinned,
                    favorite = request.favorite,
                    attachments = request.attachments,
                    clientUpdatedAtMillis = it.clientUpdatedAtMillis + 100L,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun toggleNotePinned(uid: String, noteId: String, pinned: Boolean): Result<Unit> {
        notesFlow.value = notesFlow.value.map { if (it.id == noteId) it.copy(pinned = pinned) else it }
        return Result.success(Unit)
    }

    override suspend fun toggleNoteFavorite(uid: String, noteId: String, favorite: Boolean): Result<Unit> {
        notesFlow.value = notesFlow.value.map { if (it.id == noteId) it.copy(favorite = favorite) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteNote(uid: String, noteId: String): Result<Unit> {
        notesFlow.value = notesFlow.value.filterNot { it.id == noteId }
        return Result.success(Unit)
    }

    override fun observeStudySessions(uid: String): Flow<List<StudySession>> = flow {
        if (failStream) throw IllegalStateException("Firestore unavailable")
        sessionsFlow.collect { emit(it) }
    }

    override suspend fun createStudySession(uid: String, request: SaveStudySessionRequest): Result<String> {
        val id = "session-${nextId++}"
        sessionsFlow.value = sessionsFlow.value + StudySession(
            id = id,
            uid = uid,
            title = request.title.trim(),
            subject = request.subject.trim(),
            durationMinutes = request.durationMinutes,
            sessionMode = request.sessionMode.wireValue,
            completed = request.completed,
            scheduledAtMillis = request.scheduledAtMillis,
            completedAtMillis = request.completedAtMillis,
            studyDateKey = request.studyDateKey,
            notes = request.notes.trim(),
        )
        return Result.success(id)
    }

    override suspend fun updateStudySession(uid: String, sessionId: String, request: SaveStudySessionRequest): Result<Unit> {
        sessionsFlow.value = sessionsFlow.value.map {
            if (it.id == sessionId) {
                it.copy(
                    title = request.title.trim(),
                    subject = request.subject.trim(),
                    durationMinutes = request.durationMinutes,
                    sessionMode = request.sessionMode.wireValue,
                    completed = request.completed,
                    scheduledAtMillis = request.scheduledAtMillis,
                    completedAtMillis = request.completedAtMillis,
                    studyDateKey = request.studyDateKey,
                    notes = request.notes.trim(),
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun markStudySessionCompleted(
        uid: String,
        sessionId: String,
        completed: Boolean,
        completedAtMillis: Long,
    ): Result<Unit> {
        sessionsFlow.value = sessionsFlow.value.map {
            if (it.id == sessionId) {
                it.copy(
                    completed = completed,
                    completedAtMillis = if (completed) completedAtMillis else 0L,
                    studyDateKey = StudyPlannerCalculator.toDateKey(completedAtMillis),
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteStudySession(uid: String, sessionId: String): Result<Unit> {
        sessionsFlow.value = sessionsFlow.value.filterNot { it.id == sessionId }
        return Result.success(Unit)
    }

    override fun observePlannerSettings(uid: String): Flow<StudyPlannerSettings> = flow {
        if (failStream) throw IllegalStateException("Firestore unavailable")
        settingsFlow.collect { emit(it) }
    }

    override suspend fun savePlannerSettings(uid: String, request: SaveStudyPlannerSettingsRequest): Result<Unit> {
        settingsFlow.value = StudyPlannerSettings(
            uid = uid,
            dailyGoalMinutes = request.dailyGoalMinutes,
            weeklyGoalMinutes = request.weeklyGoalMinutes,
            pomodoroFocusMinutes = request.pomodoroFocusMinutes,
            pomodoroBreakMinutes = request.pomodoroBreakMinutes,
        )
        return Result.success(Unit)
    }
}
