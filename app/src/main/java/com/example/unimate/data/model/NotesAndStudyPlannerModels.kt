package com.example.unimate.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class StudySessionMode(val wireValue: String, val label: String) {
    PLANNED("PLANNED", "Planned"),
    POMODORO("POMODORO", "Pomodoro"),
    MANUAL("MANUAL", "Logged"),
    ;

    companion object {
        fun fromWire(value: String?): StudySessionMode {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: PLANNED
        }
    }
}

enum class PomodoroPhase(val wireValue: String, val label: String) {
    FOCUS("FOCUS", "Focus Session"),
    BREAK("BREAK", "Short Break"),
    ;

    companion object {
        fun fromWire(value: String?): PomodoroPhase {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: FOCUS
        }
    }
}

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val subject: String = "",
    val durationMinutes: Long = 25L,
    val sessionMode: String = StudySessionMode.PLANNED.wireValue,
    val completed: Boolean = false,
    val scheduledAtMillis: Long = 0L,
    val completedAtMillis: Long = 0L,
    val studyDateKey: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedMode(): StudySessionMode = StudySessionMode.fromWire(sessionMode)

    fun effectiveTimestampMillis(): Long =
        when {
            completed && completedAtMillis > 0L -> completedAtMillis
            scheduledAtMillis > 0L -> scheduledAtMillis
            else -> updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L
        }

    fun formattedDateTime(): String = AcademicDateFormatters.formatDateTime(effectiveTimestampMillis())
}

@Keep
@IgnoreExtraProperties
data class StudyPlannerSettings(
    val uid: String = "",
    val dailyGoalMinutes: Long = 120L,
    val weeklyGoalMinutes: Long = 840L,
    val pomodoroFocusMinutes: Long = 25L,
    val pomodoroBreakMinutes: Long = 5L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class DailyStudyBar(
    val dateKey: String,
    val dayLabel: String,
    val minutes: Long,
    val targetMinutes: Long,
    val isToday: Boolean,
)

data class SubjectStudyBreakdown(
    val subject: String,
    val totalMinutes: Long,
    val sessionCount: Int,
    val fraction: Float,
)

data class StudyProductivityStats(
    val todayMinutes: Long = 0L,
    val dailyGoalMinutes: Long = 120L,
    val dailyGoalProgressFraction: Float = 0f,
    val dailyGoalPercent: Int = 0,
    val weeklyMinutes: Long = 0L,
    val weeklyGoalMinutes: Long = 840L,
    val weeklyGoalProgressFraction: Float = 0f,
    val weeklyGoalPercent: Int = 0,
    val completedSessionsCount: Int = 0,
    val plannedSessionsCount: Int = 0,
    val totalCompletedMinutes: Long = 0L,
    val averageSessionMinutes: Long = 0L,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val weeklyBars: List<DailyStudyBar> = emptyList(),
    val subjectBreakdown: List<SubjectStudyBreakdown> = emptyList(),
)

object StudyPlannerCalculator {
    private const val DAY_MILLIS = 86_400_000L

    fun toDateKey(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val safeMillis = if (millis > 0L) millis else System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return formatter.format(Date(safeMillis))
    }

    fun formatMinutesCompact(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0L)
        val hours = safe / 60L
        val mins = safe % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun parseDateKeyToUtcDayIndex(dateKey: String): Long? {
        val parts = dateKey.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        return cal.timeInMillis / DAY_MILLIS
    }

    fun computeCurrentStreakDays(
        completedSessions: List<StudySession>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val dayIndices = completedSessions
            .asSequence()
            .filter { it.completed && it.durationMinutes > 0L }
            .mapNotNull { session ->
                val key = session.studyDateKey.ifBlank {
                    toDateKey(session.effectiveTimestampMillis(), timeZone)
                }
                parseDateKeyToUtcDayIndex(key)
            }
            .toSortedSet()

        if (dayIndices.isEmpty()) return 0

        val todayKey = toDateKey(nowMillis, timeZone)
        val todayIndex = parseDateKeyToUtcDayIndex(todayKey) ?: return 0

        var cursor = when {
            dayIndices.contains(todayIndex) -> todayIndex
            dayIndices.contains(todayIndex - 1L) -> todayIndex - 1L
            else -> return 0
        }

        var streak = 0
        while (dayIndices.contains(cursor)) {
            streak += 1
            cursor -= 1L
        }
        return streak
    }

    fun computeLongestStreakDays(
        completedSessions: List<StudySession>,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val sortedDays = completedSessions
            .asSequence()
            .filter { it.completed && it.durationMinutes > 0L }
            .mapNotNull { session ->
                val key = session.studyDateKey.ifBlank {
                    toDateKey(session.effectiveTimestampMillis(), timeZone)
                }
                parseDateKeyToUtcDayIndex(key)
            }
            .toSortedSet()
            .toList()

        if (sortedDays.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (i in 1 until sortedDays.size) {
            if (sortedDays[i] == sortedDays[i - 1] + 1L) {
                current += 1
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }
        return longest
    }

    fun buildWeeklyBars(
        completedSessions: List<StudySession>,
        dailyTargetMinutes: Long,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<DailyStudyBar> {
        val cal = Calendar.getInstance(timeZone, Locale.US).apply {
            timeInMillis = nowMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val currentDow = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = when (currentDow) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        }

        val todayKey = toDateKey(nowMillis, timeZone)
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val minutesByDay = completedSessions
            .filter { it.completed && it.durationMinutes > 0L }
            .groupBy { session ->
                session.studyDateKey.ifBlank { toDateKey(session.effectiveTimestampMillis(), timeZone) }
            }
            .mapValues { (_, list) -> list.sumOf { it.durationMinutes } }

        val bars = mutableListOf<DailyStudyBar>()
        for (idx in 0 until 7) {
            val key = toDateKey(cal.timeInMillis, timeZone)
            bars += DailyStudyBar(
                dateKey = key,
                dayLabel = dayLabels[idx],
                minutes = minutesByDay[key] ?: 0L,
                targetMinutes = dailyTargetMinutes.coerceAtLeast(15L),
                isToday = key == todayKey,
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return bars
    }

    fun computeStats(
        sessions: List<StudySession>,
        settings: StudyPlannerSettings,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): StudyProductivityStats {
        val completed = sessions.filter { it.completed }
        val plannedCount = sessions.count { !it.completed }
        val todayKey = toDateKey(nowMillis, timeZone)

        val weeklyBars = buildWeeklyBars(
            completedSessions = completed,
            dailyTargetMinutes = settings.dailyGoalMinutes,
            nowMillis = nowMillis,
            timeZone = timeZone,
        )
        val weekKeys = weeklyBars.mapTo(mutableSetOf()) { it.dateKey }

        val todayMinutes = completed
            .filter { session ->
                val key = session.studyDateKey.ifBlank { toDateKey(session.effectiveTimestampMillis(), timeZone) }
                key == todayKey
            }
            .sumOf { it.durationMinutes.coerceAtLeast(0L) }

        val weeklyMinutes = completed
            .filter { session ->
                val key = session.studyDateKey.ifBlank { toDateKey(session.effectiveTimestampMillis(), timeZone) }
                weekKeys.contains(key)
            }
            .sumOf { it.durationMinutes.coerceAtLeast(0L) }

        val totalCompletedMinutes = completed.sumOf { it.durationMinutes.coerceAtLeast(0L) }
        val avgMinutes = if (completed.isNotEmpty()) {
            totalCompletedMinutes / completed.size.toLong()
        } else {
            0L
        }

        val dailyGoal = settings.dailyGoalMinutes.coerceAtLeast(15L)
        val weeklyGoal = settings.weeklyGoalMinutes.coerceAtLeast(30L)
        val dailyFraction = (todayMinutes.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
        val weeklyFraction = (weeklyMinutes.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)

        val subjectTotals = completed
            .groupBy { it.subject.trim().ifBlank { "General Study" } }
            .map { (subject, list) ->
                val mins = list.sumOf { it.durationMinutes.coerceAtLeast(0L) }
                Triple(subject, mins, list.size)
            }
            .sortedByDescending { it.second }

        val subjectBreakdown = if (totalCompletedMinutes > 0L) {
            subjectTotals.map { (subject, mins, count) ->
                SubjectStudyBreakdown(
                    subject = subject,
                    totalMinutes = mins,
                    sessionCount = count,
                    fraction = (mins.toFloat() / totalCompletedMinutes.toFloat()).coerceIn(0f, 1f),
                )
            }
        } else {
            emptyList()
        }

        return StudyProductivityStats(
            todayMinutes = todayMinutes,
            dailyGoalMinutes = dailyGoal,
            dailyGoalProgressFraction = dailyFraction,
            dailyGoalPercent = ((todayMinutes.toDouble() / dailyGoal.toDouble()) * 100.0).toInt().coerceIn(0, 100),
            weeklyMinutes = weeklyMinutes,
            weeklyGoalMinutes = weeklyGoal,
            weeklyGoalProgressFraction = weeklyFraction,
            weeklyGoalPercent = ((weeklyMinutes.toDouble() / weeklyGoal.toDouble()) * 100.0).toInt().coerceIn(0, 100),
            completedSessionsCount = completed.size,
            plannedSessionsCount = plannedCount,
            totalCompletedMinutes = totalCompletedMinutes,
            averageSessionMinutes = avgMinutes,
            currentStreakDays = computeCurrentStreakDays(completed, nowMillis, timeZone),
            longestStreakDays = computeLongestStreakDays(completed, timeZone),
            weeklyBars = weeklyBars,
            subjectBreakdown = subjectBreakdown,
        )
    }
}

data class SaveNoteRequest(
    val title: String,
    val courseName: String,
    val content: String,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val attachments: List<NoteAttachment> = emptyList(),
)

data class SaveStudySessionRequest(
    val title: String,
    val subject: String,
    val durationMinutes: Long,
    val sessionMode: StudySessionMode,
    val completed: Boolean,
    val scheduledAtMillis: Long,
    val completedAtMillis: Long,
    val studyDateKey: String,
    val notes: String,
)

data class SaveStudyPlannerSettingsRequest(
    val dailyGoalMinutes: Long,
    val weeklyGoalMinutes: Long,
    val pomodoroFocusMinutes: Long,
    val pomodoroBreakMinutes: Long,
)

object NotesAndPlannerMappers {
    private fun mapAttachments(attachments: List<NoteAttachment>): List<Map<String, Any>> =
        attachments.take(10).map { item ->
            mapOf(
                "name" to item.name.trim().take(160),
                "uri" to item.uri.trim().take(1000),
                "mimeType" to item.mimeType.trim().ifBlank { "application/octet-stream" }.take(120),
                "sizeBytes" to item.sizeBytes.coerceAtLeast(0L),
            )
        }

    fun toCreateNotePayload(
        uid: String,
        request: SaveNoteRequest,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "content" to request.content.trim(),
        "pinned" to request.pinned,
        "favorite" to request.favorite,
        "attachments" to mapAttachments(request.attachments),
        "clientUpdatedAtMillis" to nowMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateNotePayload(
        request: SaveNoteRequest,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "content" to request.content.trim(),
        "pinned" to request.pinned,
        "favorite" to request.favorite,
        "attachments" to mapAttachments(request.attachments),
        "clientUpdatedAtMillis" to nowMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toToggleNotePinnedPayload(
        pinned: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any> = mapOf(
        "pinned" to pinned,
        "clientUpdatedAtMillis" to nowMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toToggleNoteFavoritePayload(
        favorite: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any> = mapOf(
        "favorite" to favorite,
        "clientUpdatedAtMillis" to nowMillis.coerceAtLeast(0L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateStudySessionPayload(uid: String, request: SaveStudySessionRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "subject" to request.subject.trim(),
        "durationMinutes" to request.durationMinutes.coerceIn(1L, 600L),
        "sessionMode" to request.sessionMode.wireValue,
        "completed" to request.completed,
        "scheduledAtMillis" to request.scheduledAtMillis.coerceAtLeast(0L),
        "completedAtMillis" to request.completedAtMillis.coerceAtLeast(0L),
        "studyDateKey" to request.studyDateKey.trim(),
        "notes" to request.notes.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateStudySessionPayload(request: SaveStudySessionRequest): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "subject" to request.subject.trim(),
        "durationMinutes" to request.durationMinutes.coerceIn(1L, 600L),
        "sessionMode" to request.sessionMode.wireValue,
        "completed" to request.completed,
        "scheduledAtMillis" to request.scheduledAtMillis.coerceAtLeast(0L),
        "completedAtMillis" to request.completedAtMillis.coerceAtLeast(0L),
        "studyDateKey" to request.studyDateKey.trim(),
        "notes" to request.notes.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toMarkSessionCompletedPayload(
        completed: Boolean,
        completedAtMillis: Long,
        studyDateKey: String,
    ): Map<String, Any> = mapOf(
        "completed" to completed,
        "completedAtMillis" to if (completed) completedAtMillis.coerceAtLeast(0L) else 0L,
        "studyDateKey" to studyDateKey.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreatePlannerSettingsPayload(
        uid: String,
        request: SaveStudyPlannerSettingsRequest,
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "dailyGoalMinutes" to request.dailyGoalMinutes.coerceIn(15L, 720L),
        "weeklyGoalMinutes" to request.weeklyGoalMinutes.coerceIn(30L, 5040L),
        "pomodoroFocusMinutes" to request.pomodoroFocusMinutes.coerceIn(5L, 120L),
        "pomodoroBreakMinutes" to request.pomodoroBreakMinutes.coerceIn(1L, 60L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdatePlannerSettingsPayload(
        request: SaveStudyPlannerSettingsRequest,
    ): Map<String, Any> = mapOf(
        "dailyGoalMinutes" to request.dailyGoalMinutes.coerceIn(15L, 720L),
        "weeklyGoalMinutes" to request.weeklyGoalMinutes.coerceIn(30L, 5040L),
        "pomodoroFocusMinutes" to request.pomodoroFocusMinutes.coerceIn(5L, 120L),
        "pomodoroBreakMinutes" to request.pomodoroBreakMinutes.coerceIn(1L, 60L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
