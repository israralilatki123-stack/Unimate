package com.example.unimate.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class AssignmentPriority(val wireValue: String, val label: String, val sortRank: Int) {
    HIGH("HIGH", "High", 0),
    MEDIUM("MEDIUM", "Medium", 1),
    LOW("LOW", "Low", 2),
    ;

    companion object {
        fun fromWire(value: String?): AssignmentPriority {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: MEDIUM
        }
    }
}

enum class AssignmentStatus(val wireValue: String, val label: String) {
    PENDING("PENDING", "Pending"),
    COMPLETED("COMPLETED", "Completed"),
    ;

    companion object {
        fun fromWire(value: String?): AssignmentStatus {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: PENDING
        }
    }
}

enum class AssignmentFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    OVERDUE("Overdue"),
}

enum class AssignmentSortOrder(val label: String) {
    DEADLINE_ASC("Deadline (Earliest)"),
    DEADLINE_DESC("Deadline (Latest)"),
    PRIORITY_HIGH_FIRST("Priority (High First)"),
    TITLE_ASC("Title (A–Z)"),
}

@Keep
@IgnoreExtraProperties
data class AssignmentAttachment(
    val name: String = "",
    val uri: String = "",
    val mimeType: String = "application/octet-stream",
)

@Keep
@IgnoreExtraProperties
data class StudentAssignment(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val description: String = "",
    val courseName: String = "",
    val deadlineMillis: Long = 0L,
    val priority: String = AssignmentPriority.MEDIUM.wireValue,
    val status: String = AssignmentStatus.PENDING.wireValue,
    val attachments: List<AssignmentAttachment> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedPriority(): AssignmentPriority = AssignmentPriority.fromWire(priority)

    fun resolvedStatus(): AssignmentStatus = AssignmentStatus.fromWire(status)

    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        resolvedStatus() == AssignmentStatus.PENDING && deadlineMillis in 1 until nowMillis

    fun formattedDeadline(): String = AcademicDateFormatters.formatDateTime(deadlineMillis)

    fun deadlineRelativeLabel(nowMillis: Long = System.currentTimeMillis()): String {
        if (resolvedStatus() == AssignmentStatus.COMPLETED) return "Submitted"
        return AcademicDateFormatters.formatCountdown(deadlineMillis, nowMillis)
    }
}

enum class QuizPreparationStatus(val wireValue: String, val label: String) {
    NOT_STARTED("NOT_STARTED", "Not Started"),
    IN_PROGRESS("IN_PROGRESS", "Preparing"),
    READY("READY", "Ready"),
    COMPLETED("COMPLETED", "Completed"),
    ;

    companion object {
        fun fromWire(value: String?): QuizPreparationStatus {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: NOT_STARTED
        }
    }
}

enum class QuizSectionFilter(val label: String) {
    UPCOMING("Upcoming"),
    HISTORY("Quiz History"),
    ALL("All Quizzes"),
}

@Keep
@IgnoreExtraProperties
data class StudentQuiz(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val courseName: String = "",
    val scheduledAtMillis: Long = 0L,
    val totalMarks: Double = 10.0,
    val obtainedMarks: Double = 0.0,
    val scoreRecorded: Boolean = false,
    val preparationStatus: String = QuizPreparationStatus.NOT_STARTED.wireValue,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedPreparationStatus(): QuizPreparationStatus = QuizPreparationStatus.fromWire(preparationStatus)

    fun isHistory(nowMillis: Long = System.currentTimeMillis()): Boolean =
        resolvedPreparationStatus() == QuizPreparationStatus.COMPLETED ||
            scoreRecorded ||
            (scheduledAtMillis in 1 until nowMillis)

    fun formattedDate(): String = AcademicDateFormatters.formatDateOnly(scheduledAtMillis)

    fun formattedTime(): String = AcademicDateFormatters.formatTimeOnly(scheduledAtMillis)

    fun scorePercent(): Int {
        if (!scoreRecorded || totalMarks <= 0.0) return 0
        return ((obtainedMarks / totalMarks) * 100.0).toInt().coerceIn(0, 100)
    }
}

enum class ExamType(val wireValue: String, val label: String) {
    MIDTERM("MIDTERM", "Midterm"),
    FINAL("FINAL", "Final"),
    ;

    companion object {
        fun fromWire(value: String?): ExamType {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: MIDTERM
        }
    }
}

enum class ExamTypeFilter(val label: String) {
    ALL("All Exams"),
    MIDTERM("Midterm"),
    FINAL("Final"),
}

enum class ExamPreparationStatus(val wireValue: String, val label: String) {
    NOT_STARTED("NOT_STARTED", "Not Started"),
    REVISING("REVISING", "Revising"),
    READY("READY", "Ready"),
    COMPLETED("COMPLETED", "Completed"),
    ;

    companion object {
        fun fromWire(value: String?): ExamPreparationStatus {
            val clean = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == clean } ?: NOT_STARTED
        }
    }
}

@Keep
@IgnoreExtraProperties
data class RevisionChecklistItem(
    val id: String = "",
    val title: String = "",
    val done: Boolean = false,
)

@Keep
@IgnoreExtraProperties
data class StudentExam(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val examType: String = ExamType.MIDTERM.wireValue,
    val courseName: String = "",
    val examAtMillis: Long = 0L,
    val room: String = "",
    val preparationStatus: String = ExamPreparationStatus.NOT_STARTED.wireValue,
    val revisionChecklist: List<RevisionChecklistItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedExamType(): ExamType = ExamType.fromWire(examType)

    fun resolvedPreparationStatus(): ExamPreparationStatus = ExamPreparationStatus.fromWire(preparationStatus)

    fun countdownLabel(nowMillis: Long = System.currentTimeMillis()): String =
        AcademicDateFormatters.formatCountdown(examAtMillis, nowMillis)

    fun isPast(nowMillis: Long = System.currentTimeMillis()): Boolean =
        examAtMillis in 1 until nowMillis

    fun revisedCount(): Int = revisionChecklist.count { it.done }

    fun revisionProgressFraction(): Float {
        if (revisionChecklist.isEmpty()) return 0f
        return (revisedCount().toFloat() / revisionChecklist.size.toFloat()).coerceIn(0f, 1f)
    }

    fun formattedDateTime(): String = AcademicDateFormatters.formatDateTime(examAtMillis)
}

object AcademicDateFormatters {
    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Not scheduled"
        val formatter = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatDateOnly(millis: Long): String {
        if (millis <= 0L) return "No date"
        val formatter = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatTimeOnly(millis: Long): String {
        if (millis <= 0L) return "No time"
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    fun formatCountdown(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (targetMillis <= 0L) return "Unscheduled"
        val diff = targetMillis - nowMillis
        val absDiff = abs(diff)
        val totalMinutes = absDiff / 60_000L
        val days = totalMinutes / (60L * 24L)
        val hours = (totalMinutes % (60L * 24L)) / 60L
        val mins = totalMinutes % 60L

        return if (diff >= 0L) {
            when {
                days >= 1L -> "In ${days}d ${hours}h"
                hours >= 1L -> "In ${hours}h ${mins}m"
                totalMinutes >= 1L -> "In ${totalMinutes}m"
                else -> "Due now"
            }
        } else {
            when {
                days >= 1L -> "${days}d overdue"
                hours >= 1L -> "${hours}h ago"
                else -> "Just passed"
            }
        }
    }
}

object AssignmentQueryEngine {
    fun filterAndSort(
        assignments: List<StudentAssignment>,
        filter: AssignmentFilter,
        sortOrder: AssignmentSortOrder,
        searchQuery: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<StudentAssignment> {
        val query = searchQuery.trim().lowercase(Locale.US)
        val filtered = assignments.filter { item ->
            val matchesFilter = when (filter) {
                AssignmentFilter.ALL -> true
                AssignmentFilter.PENDING -> item.resolvedStatus() == AssignmentStatus.PENDING
                AssignmentFilter.COMPLETED -> item.resolvedStatus() == AssignmentStatus.COMPLETED
                AssignmentFilter.OVERDUE -> item.isOverdue(nowMillis)
            }
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                item.title.lowercase(Locale.US).contains(query) ||
                    item.description.lowercase(Locale.US).contains(query) ||
                    item.courseName.lowercase(Locale.US).contains(query)
            }
            matchesFilter && matchesSearch
        }

        return when (sortOrder) {
            AssignmentSortOrder.DEADLINE_ASC -> filtered.sortedWith(
                compareBy<StudentAssignment> { it.resolvedStatus() == AssignmentStatus.COMPLETED }
                    .thenBy { it.deadlineMillis },
            )
            AssignmentSortOrder.DEADLINE_DESC -> filtered.sortedByDescending { it.deadlineMillis }
            AssignmentSortOrder.PRIORITY_HIGH_FIRST -> filtered.sortedWith(
                compareBy<StudentAssignment> { it.resolvedStatus() == AssignmentStatus.COMPLETED }
                    .thenBy { it.resolvedPriority().sortRank }
                    .thenBy { it.deadlineMillis },
            )
            AssignmentSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase(Locale.US) }
        }
    }
}

data class SaveAssignmentRequest(
    val title: String,
    val description: String,
    val courseName: String,
    val deadlineMillis: Long,
    val priority: AssignmentPriority,
    val status: AssignmentStatus,
    val attachments: List<AssignmentAttachment>,
)

data class SaveQuizRequest(
    val title: String,
    val courseName: String,
    val scheduledAtMillis: Long,
    val totalMarks: Double,
    val obtainedMarks: Double,
    val scoreRecorded: Boolean,
    val preparationStatus: QuizPreparationStatus,
    val notes: String,
)

data class SaveExamRequest(
    val title: String,
    val examType: ExamType,
    val courseName: String,
    val examAtMillis: Long,
    val room: String,
    val preparationStatus: ExamPreparationStatus,
    val revisionChecklist: List<RevisionChecklistItem>,
)

object AcademicModulesMappers {
    private fun attachmentsToWire(items: List<AssignmentAttachment>): List<Map<String, Any>> =
        items.take(10).map { item ->
            mapOf(
                "name" to item.name.trim().take(120),
                "uri" to item.uri.trim().take(1000),
                "mimeType" to item.mimeType.trim().ifEmpty { "application/octet-stream" }.take(100),
            )
        }

    private fun checklistToWire(items: List<RevisionChecklistItem>): List<Map<String, Any>> =
        items.take(30).map { item ->
            mapOf(
                "id" to item.id.trim().take(64),
                "title" to item.title.trim().take(160),
                "done" to item.done,
            )
        }

    fun toCreateAssignmentPayload(uid: String, request: SaveAssignmentRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "description" to request.description.trim(),
        "courseName" to request.courseName.trim(),
        "deadlineMillis" to request.deadlineMillis,
        "priority" to request.priority.wireValue,
        "status" to request.status.wireValue,
        "attachments" to attachmentsToWire(request.attachments),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateAssignmentPayload(request: SaveAssignmentRequest): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "description" to request.description.trim(),
        "courseName" to request.courseName.trim(),
        "deadlineMillis" to request.deadlineMillis,
        "priority" to request.priority.wireValue,
        "status" to request.status.wireValue,
        "attachments" to attachmentsToWire(request.attachments),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toAssignmentStatusPayload(status: AssignmentStatus): Map<String, Any> = mapOf(
        "status" to status.wireValue,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateQuizPayload(uid: String, request: SaveQuizRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "scheduledAtMillis" to request.scheduledAtMillis,
        "totalMarks" to request.totalMarks,
        "obtainedMarks" to if (request.scoreRecorded) request.obtainedMarks.coerceIn(0.0, request.totalMarks) else 0.0,
        "scoreRecorded" to request.scoreRecorded,
        "preparationStatus" to request.preparationStatus.wireValue,
        "notes" to request.notes.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateQuizPayload(request: SaveQuizRequest): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "scheduledAtMillis" to request.scheduledAtMillis,
        "totalMarks" to request.totalMarks,
        "obtainedMarks" to if (request.scoreRecorded) request.obtainedMarks.coerceIn(0.0, request.totalMarks) else 0.0,
        "scoreRecorded" to request.scoreRecorded,
        "preparationStatus" to request.preparationStatus.wireValue,
        "notes" to request.notes.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toQuizStatusPayload(
        preparationStatus: QuizPreparationStatus,
        scoreRecorded: Boolean,
        obtainedMarks: Double,
    ): Map<String, Any> = mapOf(
        "preparationStatus" to preparationStatus.wireValue,
        "scoreRecorded" to scoreRecorded,
        "obtainedMarks" to obtainedMarks,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateExamPayload(uid: String, request: SaveExamRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "examType" to request.examType.wireValue,
        "courseName" to request.courseName.trim(),
        "examAtMillis" to request.examAtMillis,
        "room" to request.room.trim(),
        "preparationStatus" to request.preparationStatus.wireValue,
        "revisionChecklist" to checklistToWire(request.revisionChecklist),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateExamPayload(request: SaveExamRequest): Map<String, Any> = mapOf(
        "title" to request.title.trim(),
        "examType" to request.examType.wireValue,
        "courseName" to request.courseName.trim(),
        "examAtMillis" to request.examAtMillis,
        "room" to request.room.trim(),
        "preparationStatus" to request.preparationStatus.wireValue,
        "revisionChecklist" to checklistToWire(request.revisionChecklist),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toExamChecklistAndStatusPayload(
        checklist: List<RevisionChecklistItem>,
        preparationStatus: ExamPreparationStatus,
    ): Map<String, Any> = mapOf(
        "revisionChecklist" to checklistToWire(checklist),
        "preparationStatus" to preparationStatus.wireValue,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
