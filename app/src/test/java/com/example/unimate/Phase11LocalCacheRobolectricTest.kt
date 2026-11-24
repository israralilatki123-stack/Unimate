package com.example.unimate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.unimate.data.model.NoteAttachment
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.StudyPlannerSettings
import com.example.unimate.data.model.StudySession
import com.example.unimate.data.model.StudySessionMode
import com.example.unimate.data.repository.DataStoreStudyPlannerLocalCache
import com.example.unimate.data.repository.PersistedPomodoroState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase11LocalCacheRobolectricTest {

    @Test
    fun `datastore study planner local cache round-trips pomodoro notes sessions and settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = DataStoreStudyPlannerLocalCache(context)
        val uid = "robo-uid-11"

        val pomodoro = PersistedPomodoroState(
            phase = PomodoroPhase.FOCUS,
            running = true,
            remainingSeconds = 1100,
            totalSeconds = 1500,
            targetEndEpochMillis = 1999999999000L,
            selectedSubject = "CS-301",
            sessionTitle = "Dynamic Programming",
            linkedSessionId = "s-42",
            focusMinutes = 25L,
            breakMinutes = 5L,
        )
        cache.savePomodoroState(uid, pomodoro)
        assertEquals(pomodoro, cache.loadPomodoroState(uid))

        val notes = listOf(
            StudyNote(
                id = "n-1",
                uid = uid,
                title = "Exam Formula Sheet",
                courseName = "MATH-202",
                content = "Bayes theorem & variance formulas",
                pinned = true,
                favorite = true,
                attachments = listOf(
                    NoteAttachment(
                        name = "formulas.pdf",
                        uri = "content://downloads/formulas.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 4096L,
                    ),
                ),
                clientUpdatedAtMillis = 987654321L,
            ),
        )
        cache.cacheNotes(uid, notes)
        val loadedNotes = cache.loadCachedNotes(uid)
        assertEquals(1, loadedNotes.size)
        assertEquals("Exam Formula Sheet", loadedNotes[0].title)
        assertTrue(loadedNotes[0].pinned)
        assertTrue(loadedNotes[0].favorite)
        assertEquals(1, loadedNotes[0].attachments.size)
        assertEquals("formulas.pdf", loadedNotes[0].attachments[0].name)

        val sessions = listOf(
            StudySession(
                id = "s-1",
                uid = uid,
                title = "Lab Prep",
                subject = "CS-320",
                durationMinutes = 50L,
                sessionMode = StudySessionMode.POMODORO.wireValue,
                completed = true,
                scheduledAtMillis = 50000L,
                completedAtMillis = 60000L,
                studyDateKey = "2026-11-24",
                notes = "IPC sockets",
            ),
        )
        cache.cacheSessions(uid, sessions)
        val loadedSessions = cache.loadCachedSessions(uid)
        assertEquals(1, loadedSessions.size)
        assertEquals("Lab Prep", loadedSessions[0].title)
        assertEquals(50L, loadedSessions[0].durationMinutes)
        assertTrue(loadedSessions[0].completed)

        val settings = StudyPlannerSettings(
            uid = uid,
            dailyGoalMinutes = 180L,
            weeklyGoalMinutes = 1000L,
            pomodoroFocusMinutes = 45L,
            pomodoroBreakMinutes = 10L,
        )
        cache.cacheSettings(uid, settings)
        val loadedSettings = cache.loadCachedSettings(uid)
        assertEquals(180L, loadedSettings?.dailyGoalMinutes)
        assertEquals(1000L, loadedSettings?.weeklyGoalMinutes)
        assertEquals(45L, loadedSettings?.pomodoroFocusMinutes)
        assertEquals(10L, loadedSettings?.pomodoroBreakMinutes)

        cache.clearPomodoroState(uid)
        assertNull(cache.loadPomodoroState(uid))
    }
}
