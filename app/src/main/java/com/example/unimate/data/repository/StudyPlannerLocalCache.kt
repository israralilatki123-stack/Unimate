package com.example.unimate.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.unimate.data.local.unimateDataStore
import com.example.unimate.data.model.NoteAttachment
import com.example.unimate.data.model.PomodoroPhase
import com.example.unimate.data.model.StudyNote
import com.example.unimate.data.model.StudyPlannerSettings
import com.example.unimate.data.model.StudySession
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class PersistedPomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val targetEndEpochMillis: Long = 0L,
    val selectedSubject: String = "General Study",
    val sessionTitle: String = "",
    val linkedSessionId: String? = null,
    val focusMinutes: Long = 25L,
    val breakMinutes: Long = 5L,
)

interface StudyPlannerLocalCache {
    suspend fun savePomodoroState(uid: String, state: PersistedPomodoroState)
    suspend fun loadPomodoroState(uid: String): PersistedPomodoroState?
    suspend fun clearPomodoroState(uid: String)

    suspend fun cacheNotes(uid: String, notes: List<StudyNote>)
    suspend fun loadCachedNotes(uid: String): List<StudyNote>

    suspend fun cacheSessions(uid: String, sessions: List<StudySession>)
    suspend fun loadCachedSessions(uid: String): List<StudySession>

    suspend fun cacheSettings(uid: String, settings: StudyPlannerSettings)
    suspend fun loadCachedSettings(uid: String): StudyPlannerSettings?
}

class DataStoreStudyPlannerLocalCache(
    private val context: Context,
) : StudyPlannerLocalCache {

    private suspend fun readString(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        return context.unimateDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()[key]
    }

    override suspend fun savePomodoroState(uid: String, state: PersistedPomodoroState) {
        if (uid.isBlank()) return
        val json = JSONObject().apply {
            put("phase", state.phase.wireValue)
            put("running", state.running)
            put("remainingSeconds", state.remainingSeconds)
            put("totalSeconds", state.totalSeconds)
            put("targetEndEpochMillis", state.targetEndEpochMillis)
            put("selectedSubject", state.selectedSubject)
            put("sessionTitle", state.sessionTitle)
            put("linkedSessionId", state.linkedSessionId.orEmpty())
            put("focusMinutes", state.focusMinutes)
            put("breakMinutes", state.breakMinutes)
        }.toString()
        context.unimateDataStore.edit { prefs ->
            prefs[pomodoroKey(uid)] = json
        }
    }

    override suspend fun loadPomodoroState(uid: String): PersistedPomodoroState? {
        if (uid.isBlank()) return null
        val raw = readString(pomodoroKey(uid)) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            PersistedPomodoroState(
                phase = PomodoroPhase.fromWire(obj.optString("phase")),
                running = obj.optBoolean("running", false),
                remainingSeconds = obj.optInt("remainingSeconds", 25 * 60).coerceAtLeast(0),
                totalSeconds = obj.optInt("totalSeconds", 25 * 60).coerceAtLeast(60),
                targetEndEpochMillis = obj.optLong("targetEndEpochMillis", 0L),
                selectedSubject = obj.optString("selectedSubject", "General Study").ifBlank { "General Study" },
                sessionTitle = obj.optString("sessionTitle", ""),
                linkedSessionId = obj.optString("linkedSessionId", "").takeIf { it.isNotBlank() },
                focusMinutes = obj.optLong("focusMinutes", 25L).coerceIn(5L, 120L),
                breakMinutes = obj.optLong("breakMinutes", 5L).coerceIn(1L, 60L),
            )
        }.getOrNull()
    }

    override suspend fun clearPomodoroState(uid: String) {
        if (uid.isBlank()) return
        context.unimateDataStore.edit { prefs ->
            prefs.remove(pomodoroKey(uid))
        }
    }

    override suspend fun cacheNotes(uid: String, notes: List<StudyNote>) {
        if (uid.isBlank()) return
        val array = JSONArray()
        notes.take(150).forEach { note ->
            val attachmentsJson = JSONArray().apply {
                note.attachments.forEach { att ->
                    put(
                        JSONObject().apply {
                            put("name", att.name)
                            put("uri", att.uri)
                            put("mimeType", att.mimeType)
                            put("sizeBytes", att.sizeBytes)
                        },
                    )
                }
            }
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("uid", note.uid)
                    put("title", note.title)
                    put("courseName", note.courseName)
                    put("content", note.content)
                    put("pinned", note.pinned)
                    put("favorite", note.favorite)
                    put("attachments", attachmentsJson)
                    put("clientUpdatedAtMillis", note.effectiveUpdatedAtMillis())
                },
            )
        }
        context.unimateDataStore.edit { prefs ->
            prefs[notesKey(uid)] = array.toString()
        }
    }

    override suspend fun loadCachedNotes(uid: String): List<StudyNote> {
        if (uid.isBlank()) return emptyList()
        val raw = readString(notesKey(uid)) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val attArray = obj.optJSONArray("attachments") ?: JSONArray()
                    val attachments = buildList(attArray.length()) {
                        for (j in 0 until attArray.length()) {
                            val attObj = attArray.getJSONObject(j)
                            add(
                                NoteAttachment(
                                    name = attObj.optString("name", ""),
                                    uri = attObj.optString("uri", ""),
                                    mimeType = attObj.optString("mimeType", "application/octet-stream"),
                                    sizeBytes = attObj.optLong("sizeBytes", 0L),
                                ),
                            )
                        }
                    }
                    add(
                        StudyNote(
                            id = obj.optString("id", ""),
                            uid = obj.optString("uid", uid),
                            title = obj.optString("title", ""),
                            courseName = obj.optString("courseName", ""),
                            content = obj.optString("content", ""),
                            pinned = obj.optBoolean("pinned", false),
                            favorite = obj.optBoolean("favorite", false),
                            attachments = attachments,
                            clientUpdatedAtMillis = obj.optLong("clientUpdatedAtMillis", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun cacheSessions(uid: String, sessions: List<StudySession>) {
        if (uid.isBlank()) return
        val array = JSONArray()
        sessions.take(250).forEach { session ->
            array.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("uid", session.uid)
                    put("title", session.title)
                    put("subject", session.subject)
                    put("durationMinutes", session.durationMinutes)
                    put("sessionMode", session.sessionMode)
                    put("completed", session.completed)
                    put("scheduledAtMillis", session.scheduledAtMillis)
                    put("completedAtMillis", session.completedAtMillis)
                    put("studyDateKey", session.studyDateKey)
                    put("notes", session.notes)
                },
            )
        }
        context.unimateDataStore.edit { prefs ->
            prefs[sessionsKey(uid)] = array.toString()
        }
    }

    override suspend fun loadCachedSessions(uid: String): List<StudySession> {
        if (uid.isBlank()) return emptyList()
        val raw = readString(sessionsKey(uid)) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        StudySession(
                            id = obj.optString("id", ""),
                            uid = obj.optString("uid", uid),
                            title = obj.optString("title", ""),
                            subject = obj.optString("subject", ""),
                            durationMinutes = obj.optLong("durationMinutes", 25L),
                            sessionMode = obj.optString("sessionMode", "PLANNED"),
                            completed = obj.optBoolean("completed", false),
                            scheduledAtMillis = obj.optLong("scheduledAtMillis", 0L),
                            completedAtMillis = obj.optLong("completedAtMillis", 0L),
                            studyDateKey = obj.optString("studyDateKey", ""),
                            notes = obj.optString("notes", ""),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun cacheSettings(uid: String, settings: StudyPlannerSettings) {
        if (uid.isBlank()) return
        val json = JSONObject().apply {
            put("uid", settings.uid.ifBlank { uid })
            put("dailyGoalMinutes", settings.dailyGoalMinutes)
            put("weeklyGoalMinutes", settings.weeklyGoalMinutes)
            put("pomodoroFocusMinutes", settings.pomodoroFocusMinutes)
            put("pomodoroBreakMinutes", settings.pomodoroBreakMinutes)
        }.toString()
        context.unimateDataStore.edit { prefs ->
            prefs[settingsKey(uid)] = json
        }
    }

    override suspend fun loadCachedSettings(uid: String): StudyPlannerSettings? {
        if (uid.isBlank()) return null
        val raw = readString(settingsKey(uid)) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            StudyPlannerSettings(
                uid = obj.optString("uid", uid),
                dailyGoalMinutes = obj.optLong("dailyGoalMinutes", 120L).coerceIn(15L, 720L),
                weeklyGoalMinutes = obj.optLong("weeklyGoalMinutes", 840L).coerceIn(30L, 5040L),
                pomodoroFocusMinutes = obj.optLong("pomodoroFocusMinutes", 25L).coerceIn(5L, 120L),
                pomodoroBreakMinutes = obj.optLong("pomodoroBreakMinutes", 5L).coerceIn(1L, 60L),
            )
        }.getOrNull()
    }

    private companion object {
        fun pomodoroKey(uid: String) = stringPreferencesKey("pomodoro_state_$uid")
        fun notesKey(uid: String) = stringPreferencesKey("cached_notes_$uid")
        fun sessionsKey(uid: String) = stringPreferencesKey("cached_study_sessions_$uid")
        fun settingsKey(uid: String) = stringPreferencesKey("cached_planner_settings_$uid")
    }
}
