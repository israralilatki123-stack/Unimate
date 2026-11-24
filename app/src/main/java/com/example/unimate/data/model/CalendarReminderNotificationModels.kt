package com.example.unimate.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

enum class CalendarViewMode(val label: String) {
    MONTHLY("Monthly"),
    WEEKLY("Weekly"),
    DAILY("Daily"),
}

enum class CalendarEventCategory(
    val wireValue: String,
    val label: String,
    val color: Color,
) {
    CLASSES("CLASSES", "Classes", Color(0xFF2563EB)),
    ASSIGNMENTS("ASSIGNMENTS", "Assignments", Color(0xFF5B3DF5)),
    QUIZZES("QUIZZES", "Quizzes", Color(0xFFD97706)),
    EXAMS("EXAMS", "Exams", Color(0xFFEF4444)),
    UNIVERSITY_EVENTS("UNIVERSITY_EVENTS", "University Events", Color(0xFF0D9488)),
    HOLIDAYS("HOLIDAYS", "Holidays", Color(0xFF10B981)),
    PERSONAL_EVENTS("PERSONAL_EVENTS", "Personal Events", Color(0xFFE11D48)),
    ;

    companion object {
        fun fromWire(value: String?): CalendarEventCategory {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: PERSONAL_EVENTS
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CalendarEvent(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = CalendarEventCategory.PERSONAL_EVENTS.wireValue,
    val locationOrCourse: String = "",
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L,
    val allDay: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedCategory(): CalendarEventCategory = CalendarEventCategory.fromWire(category)
}

enum class CalendarDisplaySource {
    CALENDAR_EVENT,
    TIMETABLE_SYNCED,
    ASSIGNMENT_SYNCED,
    QUIZ_SYNCED,
    EXAM_SYNCED,
}

data class CalendarDisplayItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val category: CalendarEventCategory,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val allDay: Boolean,
    val source: CalendarDisplaySource,
    val editableEvent: CalendarEvent? = null,
) {
    val isEditableCalendarEvent: Boolean
        get() = source == CalendarDisplaySource.CALENDAR_EVENT && editableEvent != null
}

enum class ReminderCategory(
    val wireValue: String,
    val label: String,
    val shortLabel: String,
    val color: Color,
) {
    CLASS("CLASS", "Class Reminder", "Class", Color(0xFF2563EB)),
    ASSIGNMENT("ASSIGNMENT", "Assignment Reminder", "Assignment", Color(0xFF5B3DF5)),
    QUIZ("QUIZ", "Quiz Reminder", "Quiz", Color(0xFFD97706)),
    EXAM("EXAM", "Exam Reminder", "Exam", Color(0xFFEF4444)),
    STUDY("STUDY", "Study Reminder", "Study", Color(0xFF0D9488)),
    CUSTOM("CUSTOM", "Custom Reminder", "Custom", Color(0xFFE11D48)),
    ;

    companion object {
        fun fromWire(value: String?): ReminderCategory {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: CUSTOM
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ScheduledReminder(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = ReminderCategory.CUSTOM.wireValue,
    val triggerAtMillis: Long = 0L,
    val leadMinutes: Long = 0L,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val workRequestId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedCategory(): ReminderCategory = ReminderCategory.fromWire(category)

    fun effectiveScheduleMillis(): Long =
        (triggerAtMillis - leadMinutes.coerceAtLeast(0L) * 60_000L).coerceAtLeast(0L)
}

enum class NotificationSourceType(val wireValue: String, val label: String) {
    REMINDER("REMINDER", "Reminder"),
    EVENT("EVENT", "Calendar Event"),
    ACADEMIC("ACADEMIC", "Academic Alert"),
    SYSTEM("SYSTEM", "System"),
    ;

    companion object {
        fun fromWire(value: String?): NotificationSourceType {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: REMINDER
        }
    }
}

enum class NotificationFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    READ("Read"),
}

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = ReminderCategory.CUSTOM.wireValue,
    val sourceType: String = NotificationSourceType.REMINDER.wireValue,
    val sourceId: String = "",
    val deliveredAtMillis: Long = 0L,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedCategory(): ReminderCategory = ReminderCategory.fromWire(category)

    fun resolvedSourceType(): NotificationSourceType = NotificationSourceType.fromWire(sourceType)
}

data class SaveCalendarEventRequest(
    val title: String,
    val description: String,
    val category: CalendarEventCategory,
    val locationOrCourse: String,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val allDay: Boolean,
)

data class SaveScheduledReminderRequest(
    val title: String,
    val notes: String,
    val category: ReminderCategory,
    val triggerAtMillis: Long,
    val leadMinutes: Long,
    val enabled: Boolean,
    val completed: Boolean = false,
    val workRequestId: String = "",
)

data class CreateAppNotificationRequest(
    val title: String,
    val body: String,
    val category: ReminderCategory,
    val sourceType: NotificationSourceType,
    val sourceId: String,
    val deliveredAtMillis: Long,
)

object CalendarReminderMappers {
    fun toCreateCalendarEventPayload(uid: String, request: SaveCalendarEventRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "description" to request.description.trim(),
        "category" to request.category.wireValue,
        "locationOrCourse" to request.locationOrCourse.trim(),
        "startAtMillis" to request.startAtMillis,
        "endAtMillis" to request.endAtMillis,
        "allDay" to request.allDay,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateCalendarEventPayload(request: SaveCalendarEventRequest): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "description" to request.description.trim(),
        "category" to request.category.wireValue,
        "locationOrCourse" to request.locationOrCourse.trim(),
        "startAtMillis" to request.startAtMillis,
        "endAtMillis" to request.endAtMillis,
        "allDay" to request.allDay,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateReminderPayload(
        uid: String,
        request: SaveScheduledReminderRequest,
        workRequestId: String = request.workRequestId,
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "notes" to request.notes.trim(),
        "category" to request.category.wireValue,
        "triggerAtMillis" to request.triggerAtMillis,
        "leadMinutes" to request.leadMinutes.coerceIn(0L, 10080L),
        "enabled" to request.enabled,
        "completed" to request.completed,
        "workRequestId" to workRequestId.trim().take(128),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateReminderPayload(
        request: SaveScheduledReminderRequest,
        workRequestId: String = request.workRequestId,
    ): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "notes" to request.notes.trim(),
        "category" to request.category.wireValue,
        "triggerAtMillis" to request.triggerAtMillis,
        "leadMinutes" to request.leadMinutes.coerceIn(0L, 10080L),
        "enabled" to request.enabled,
        "completed" to request.completed,
        "workRequestId" to workRequestId.trim().take(128),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toReminderStatePayload(
        enabled: Boolean,
        completed: Boolean,
        workRequestId: String,
    ): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "completed" to completed,
        "workRequestId" to workRequestId.trim().take(128),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateNotificationPayload(uid: String, request: CreateAppNotificationRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "body" to request.body.trim(),
        "category" to request.category.wireValue,
        "sourceType" to request.sourceType.wireValue,
        "sourceId" to request.sourceId.trim().take(128),
        "deliveredAtMillis" to request.deliveredAtMillis,
        "read" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toMarkNotificationReadPayload(read: Boolean): Map<String, Any> = mapOf(
        "read" to read,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
