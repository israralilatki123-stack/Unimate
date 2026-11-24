package com.example.unimate.data.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

enum class TaskCategory(val wireValue: String, val label: String) {
    ASSIGNMENT("ASSIGNMENT", "Assignment"),
    QUIZ("QUIZ", "Quiz"),
    EXAM("EXAM", "Exam"),
    ;

    companion object {
        fun fromWire(value: String?): TaskCategory {
            val normalized = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == normalized } ?: ASSIGNMENT
        }
    }
}

enum class DayOfWeekOption(
    val wireValue: String,
    val fullLabel: String,
    val shortLabel: String,
    val calendarDay: Int,
) {
    MONDAY("MONDAY", "Monday", "Mon", Calendar.MONDAY),
    TUESDAY("TUESDAY", "Tuesday", "Tue", Calendar.TUESDAY),
    WEDNESDAY("WEDNESDAY", "Wednesday", "Wed", Calendar.WEDNESDAY),
    THURSDAY("THURSDAY", "Thursday", "Thu", Calendar.THURSDAY),
    FRIDAY("FRIDAY", "Friday", "Fri", Calendar.FRIDAY),
    SATURDAY("SATURDAY", "Saturday", "Sat", Calendar.SATURDAY),
    SUNDAY("SUNDAY", "Sunday", "Sun", Calendar.SUNDAY),
    ;

    companion object {
        fun fromWire(value: String?): DayOfWeekOption {
            val normalized = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == normalized } ?: MONDAY
        }

        fun today(calendar: Calendar = Calendar.getInstance()): DayOfWeekOption {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            return entries.firstOrNull { it.calendarDay == day } ?: MONDAY
        }
    }
}

data class SubjectColorOption(
    val index: Long,
    val name: String,
    val color: Color,
)

object SubjectColorPalette {
    val options: List<SubjectColorOption> = listOf(
        SubjectColorOption(0L, "Indigo", Color(0xFF4F46E5)),
        SubjectColorOption(1L, "Electric Blue", Color(0xFF2563EB)),
        SubjectColorOption(2L, "Emerald", Color(0xFF059669)),
        SubjectColorOption(3L, "Amber", Color(0xFFD97706)),
        SubjectColorOption(4L, "Rose", Color(0xFFE11D48)),
        SubjectColorOption(5L, "Violet", Color(0xFF7C3AED)),
        SubjectColorOption(6L, "Teal", Color(0xFF0D9488)),
        SubjectColorOption(7L, "Coral", Color(0xFFEA580C)),
    )

    fun colorForIndex(index: Long): Color {
        val safeIdx = abs(index.toInt()) % options.size
        return options[safeIdx].color
    }

    fun suggestedIndex(seed: String): Long {
        val clean = seed.trim().uppercase(Locale.US)
        if (clean.isEmpty()) return 0L
        return (abs(clean.hashCode()) % options.size).toLong()
    }
}

object TimeFormatUtils {
    fun formatMinutes(minutes: Long): String {
        val clamped = minutes.coerceIn(0L, 1439L).toInt()
        val hour24 = clamped / 60
        val min = clamped % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, min, amPm)
    }

    fun formatDuration(startMinutes: Long, endMinutes: Long): String {
        val diff = (endMinutes - startMinutes).coerceAtLeast(0L)
        val hours = diff / 60L
        val mins = diff % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours}h ${mins}m"
            hours > 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AcademicTask(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val courseName: String = "",
    val category: String = TaskCategory.ASSIGNMENT.wireValue,
    val dueDateText: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedCategory(): TaskCategory = TaskCategory.fromWire(category)
}

@Keep
@IgnoreExtraProperties
data class StudentCourse(
    @DocumentId val id: String = "",
    val uid: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val creditHours: Double = 3.0,
    val instructor: String = "",
    val semester: String = "",
    val description: String = "",
    val classroom: String = "",
    val schedule: String = "",
    val attendedClasses: Long = 0L,
    val totalClasses: Long = 0L,
    val colorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun attendancePercentage(): Int {
        if (totalClasses <= 0L) return 100
        return ((attendedClasses.toDouble() / totalClasses.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
    }
}

@Keep
@IgnoreExtraProperties
data class TimetableClass(
    @DocumentId val id: String = "",
    val uid: String = "",
    val dayOfWeek: String = DayOfWeekOption.MONDAY.wireValue,
    val subjectName: String = "",
    val courseCode: String = "",
    val instructor: String = "",
    val classroom: String = "",
    val startTimeMinutes: Long = 540L,
    val endTimeMinutes: Long = 630L,
    val colorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedDay(): DayOfWeekOption = DayOfWeekOption.fromWire(dayOfWeek)

    fun durationMinutes(): Long = (endTimeMinutes - startTimeMinutes).coerceAtLeast(0L)

    fun formattedTimeRange(): String =
        "${TimeFormatUtils.formatMinutes(startTimeMinutes)} - ${TimeFormatUtils.formatMinutes(endTimeMinutes)}"

    fun formattedDuration(): String =
        TimeFormatUtils.formatDuration(startTimeMinutes, endTimeMinutes)

    fun isToday(today: DayOfWeekOption = DayOfWeekOption.today()): Boolean =
        resolvedDay() == today
}

object TimetableConflictDetector {
    fun intervalsOverlap(
        startA: Long,
        endA: Long,
        startB: Long,
        endB: Long,
    ): Boolean = startA < endB && startB < endA

    fun findConflicts(
        day: DayOfWeekOption,
        startTimeMinutes: Long,
        endTimeMinutes: Long,
        excludeClassId: String?,
        existingClasses: List<TimetableClass>,
    ): List<TimetableClass> {
        if (endTimeMinutes <= startTimeMinutes) return emptyList()
        return existingClasses.filter { existing ->
            existing.id != excludeClassId &&
                existing.resolvedDay() == day &&
                intervalsOverlap(
                    startA = startTimeMinutes,
                    endA = endTimeMinutes,
                    startB = existing.startTimeMinutes,
                    endB = existing.endTimeMinutes,
                )
        }
    }

    fun computeConflictMap(classes: List<TimetableClass>): Map<String, List<TimetableClass>> {
        if (classes.size < 2) return emptyMap()
        val result = mutableMapOf<String, List<TimetableClass>>()
        for (item in classes) {
            val conflicts = findConflicts(
                day = item.resolvedDay(),
                startTimeMinutes = item.startTimeMinutes,
                endTimeMinutes = item.endTimeMinutes,
                excludeClassId = item.id,
                existingClasses = classes,
            )
            if (conflicts.isNotEmpty()) {
                result[item.id] = conflicts
            }
        }
        return result
    }
}

@Keep
@IgnoreExtraProperties
data class NoteAttachment(
    val name: String = "",
    val uri: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
) {
    fun isPdf(): Boolean =
        mimeType.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val courseName: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val attachments: List<NoteAttachment> = emptyList(),
    val clientUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun effectiveUpdatedAtMillis(): Long =
        updatedAt?.toDate()?.time
            ?: createdAt?.toDate()?.time
            ?: clientUpdatedAtMillis

    fun effectiveCreatedAtMillis(): Long =
        createdAt?.toDate()?.time
            ?: updatedAt?.toDate()?.time
            ?: clientUpdatedAtMillis

    fun formattedUpdatedTimestamp(): String {
        val millis = effectiveUpdatedAtMillis()
        if (millis <= 0L) return "Just now"
        return AcademicDateFormatters.formatDateTime(millis)
    }

    fun formattedCreatedTimestamp(): String {
        val millis = effectiveCreatedAtMillis()
        if (millis <= 0L) return "Saved"
        return AcademicDateFormatters.formatDateTime(millis)
    }
}

@Keep
@IgnoreExtraProperties
data class StudentReminder(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val remindAtText: String = "",
    val done: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyGoal(
    @DocumentId val id: String = "",
    val uid: String = "",
    val title: String = "",
    val completedSteps: Long = 0L,
    val targetSteps: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun progressFraction(): Float {
        if (targetSteps <= 0L) return 0f
        return (completedSteps.toFloat() / targetSteps.toFloat()).coerceIn(0f, 1f)
    }
}

data class AddTaskRequest(
    val title: String,
    val courseName: String,
    val category: TaskCategory,
    val dueDateText: String,
)

data class SaveCourseRequest(
    val courseCode: String,
    val courseName: String,
    val creditHours: Double,
    val instructor: String,
    val semester: String,
    val description: String,
    val classroom: String,
    val schedule: String,
    val attendedClasses: Long,
    val totalClasses: Long,
    val colorIndex: Long,
)

data class SaveTimetableClassRequest(
    val dayOfWeek: DayOfWeekOption,
    val subjectName: String,
    val courseCode: String,
    val instructor: String,
    val classroom: String,
    val startTimeMinutes: Long,
    val endTimeMinutes: Long,
    val colorIndex: Long,
)

data class AddNoteRequest(
    val title: String,
    val courseName: String,
    val content: String,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val attachments: List<NoteAttachment> = emptyList(),
)

data class AddReminderRequest(
    val title: String,
    val remindAtText: String,
)

data class AddGoalRequest(
    val title: String,
    val completedSteps: Long,
    val targetSteps: Long,
)

object WorkspaceMappers {
    fun toCreateTaskPayload(uid: String, request: AddTaskRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "category" to request.category.wireValue,
        "dueDateText" to request.dueDateText.trim(),
        "completed" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toToggleTaskPayload(completed: Boolean): Map<String, Any> = mapOf(
        "completed" to completed,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateCoursePayload(uid: String, request: SaveCourseRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "courseCode" to request.courseCode.trim(),
        "courseName" to request.courseName.trim(),
        "creditHours" to request.creditHours,
        "instructor" to request.instructor.trim(),
        "semester" to request.semester.trim(),
        "description" to request.description.trim(),
        "classroom" to request.classroom.trim(),
        "schedule" to request.schedule.trim(),
        "attendedClasses" to request.attendedClasses,
        "totalClasses" to request.totalClasses,
        "colorIndex" to request.colorIndex.coerceIn(0L, 7L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateCoursePayload(request: SaveCourseRequest): Map<String, Any> = mapOf(
        "courseCode" to request.courseCode.trim(),
        "courseName" to request.courseName.trim(),
        "creditHours" to request.creditHours,
        "instructor" to request.instructor.trim(),
        "semester" to request.semester.trim(),
        "description" to request.description.trim(),
        "classroom" to request.classroom.trim(),
        "schedule" to request.schedule.trim(),
        "attendedClasses" to request.attendedClasses,
        "totalClasses" to request.totalClasses,
        "colorIndex" to request.colorIndex.coerceIn(0L, 7L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateAttendancePayload(attendedClasses: Long, totalClasses: Long): Map<String, Any> = mapOf(
        "attendedClasses" to attendedClasses,
        "totalClasses" to totalClasses,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateTimetableClassPayload(uid: String, request: SaveTimetableClassRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "dayOfWeek" to request.dayOfWeek.wireValue,
        "subjectName" to request.subjectName.trim(),
        "courseCode" to request.courseCode.trim(),
        "instructor" to request.instructor.trim(),
        "classroom" to request.classroom.trim(),
        "startTimeMinutes" to request.startTimeMinutes,
        "endTimeMinutes" to request.endTimeMinutes,
        "colorIndex" to request.colorIndex.coerceIn(0L, 7L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateTimetableClassPayload(request: SaveTimetableClassRequest): Map<String, Any> = mapOf(
        "dayOfWeek" to request.dayOfWeek.wireValue,
        "subjectName" to request.subjectName.trim(),
        "courseCode" to request.courseCode.trim(),
        "instructor" to request.instructor.trim(),
        "classroom" to request.classroom.trim(),
        "startTimeMinutes" to request.startTimeMinutes,
        "endTimeMinutes" to request.endTimeMinutes,
        "colorIndex" to request.colorIndex.coerceIn(0L, 7L),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateNotePayload(
        uid: String,
        request: AddNoteRequest,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "courseName" to request.courseName.trim(),
        "content" to request.content.trim(),
        "pinned" to request.pinned,
        "favorite" to request.favorite,
        "attachments" to request.attachments.take(10).map { item ->
            mapOf(
                "name" to item.name.trim().take(160),
                "uri" to item.uri.trim().take(1000),
                "mimeType" to item.mimeType.trim().ifBlank { "application/octet-stream" }.take(120),
                "sizeBytes" to item.sizeBytes.coerceAtLeast(0L),
            )
        },
        "clientUpdatedAtMillis" to nowMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateReminderPayload(uid: String, request: AddReminderRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "remindAtText" to request.remindAtText.trim(),
        "done" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toToggleReminderPayload(done: Boolean): Map<String, Any> = mapOf(
        "done" to done,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toCreateGoalPayload(uid: String, request: AddGoalRequest): Map<String, Any> = mapOf(
        "uid" to uid,
        "title" to request.title.trim(),
        "completedSteps" to request.completedSteps,
        "targetSteps" to request.targetSteps,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateGoalProgressPayload(completedSteps: Long): Map<String, Any> = mapOf(
        "completedSteps" to completedSteps,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

enum class LetterGrade(val label: String, val points: Double) {
    A("A", 4.0),
    A_MINUS("A-", 3.7),
    B_PLUS("B+", 3.3),
    B("B", 3.0),
    B_MINUS("B-", 2.7),
    C_PLUS("C+", 2.3),
    C("C", 2.0),
    C_MINUS("C-", 1.7),
    D("D", 1.0),
    F("F", 0.0),
}

data class GpaCourseEntry(
    val id: String,
    val courseName: String,
    val creditHours: Double,
    val grade: LetterGrade,
)

object GpaCalculator {
    fun calculateGpa(entries: List<GpaCourseEntry>): Double {
        val valid = entries.filter { it.creditHours > 0.0 }
        if (valid.isEmpty()) return 0.0
        val totalCredits = valid.sumOf { it.creditHours }
        if (totalCredits <= 0.0) return 0.0
        val weightedSum = valid.sumOf { it.creditHours * it.grade.points }
        return weightedSum / totalCredits
    }

    fun totalCredits(entries: List<GpaCourseEntry>): Double {
        return entries.filter { it.creditHours > 0.0 }.sumOf { it.creditHours }
    }
}

/**
 * Clearly-labeled sample preview items for Home Overview ONLY when the student has not yet saved
 * real records to Cloud Firestore and leaves Demo Preview Mode active.
 */
object SampleWorkspaceData {
    val sampleCourses: List<StudentCourse> = listOf(
        StudentCourse(
            id = "demo-course-1",
            courseCode = "CS-301",
            courseName = "Data Structures & Algorithms",
            creditHours = 3.0,
            instructor = "Dr. Tariq Mehmood",
            semester = "5th Semester",
            description = "Trees, graphs, dynamic programming, and greedy algorithms.",
            classroom = "AB-2 Room 204",
            schedule = "Mon & Wed • 09:00 AM - 10:30 AM",
            attendedClasses = 18L,
            totalClasses = 20L,
            colorIndex = 0L,
        ),
        StudentCourse(
            id = "demo-course-2",
            courseCode = "SE-312",
            courseName = "Software Architecture",
            creditHours = 3.0,
            instructor = "Ms. Ayesha Siddiqui",
            semester = "5th Semester",
            description = "Architectural styles, clean architecture, and scalable system design.",
            classroom = "CS Lab 3",
            schedule = "Tue & Thu • 11:30 AM - 01:00 PM",
            attendedClasses = 15L,
            totalClasses = 18L,
            colorIndex = 1L,
        ),
        StudentCourse(
            id = "demo-course-3",
            courseCode = "AI-305",
            courseName = "Machine Learning Foundations",
            creditHours = 4.0,
            instructor = "Dr. Bilal Qureshi",
            semester = "5th Semester",
            description = "Supervised and unsupervised learning with neural foundations.",
            classroom = "Auditorium B",
            schedule = "Fri • 02:00 PM - 04:00 PM",
            attendedClasses = 14L,
            totalClasses = 16L,
            colorIndex = 2L,
        ),
    )

    val sampleTimetable: List<TimetableClass> = listOf(
        TimetableClass(
            id = "demo-tt-1",
            dayOfWeek = DayOfWeekOption.today().wireValue,
            subjectName = "Data Structures & Algorithms",
            courseCode = "CS-301",
            instructor = "Dr. Tariq Mehmood",
            classroom = "AB-2 Room 204",
            startTimeMinutes = 540L,
            endTimeMinutes = 630L,
            colorIndex = 0L,
        ),
        TimetableClass(
            id = "demo-tt-2",
            dayOfWeek = DayOfWeekOption.today().wireValue,
            subjectName = "Software Architecture",
            courseCode = "SE-312",
            instructor = "Ms. Ayesha Siddiqui",
            classroom = "CS Lab 3",
            startTimeMinutes = 690L,
            endTimeMinutes = 780L,
            colorIndex = 1L,
        ),
    )

    val sampleTasks: List<AcademicTask> = listOf(
        AcademicTask(
            id = "demo-task-1",
            title = "Balanced BST Implementation Report",
            courseName = "CS-301",
            category = TaskCategory.ASSIGNMENT.wireValue,
            dueDateText = "Due Tomorrow, 11:59 PM",
            completed = false,
        ),
        AcademicTask(
            id = "demo-task-2",
            title = "Clean Architecture Case Study",
            courseName = "SE-312",
            category = TaskCategory.ASSIGNMENT.wireValue,
            dueDateText = "Due Friday, 5:00 PM",
            completed = false,
        ),
        AcademicTask(
            id = "demo-task-3",
            title = "Graph Traversal Quiz 2",
            courseName = "CS-301",
            category = TaskCategory.QUIZ.wireValue,
            dueDateText = "Thu • 10:00 AM",
            completed = false,
        ),
        AcademicTask(
            id = "demo-task-4",
            title = "Midterm Exam: Linear Regression & SVMs",
            courseName = "AI-305",
            category = TaskCategory.EXAM.wireValue,
            dueDateText = "Next Mon • 09:00 AM",
            completed = false,
        ),
    )

    val sampleReminders: List<StudentReminder> = listOf(
        StudentReminder(
            id = "demo-rem-1",
            title = "Submit CS-301 lab worksheet on portal",
            remindAtText = "Today • 6:00 PM",
            done = false,
        ),
        StudentReminder(
            id = "demo-rem-2",
            title = "Review SE-312 sequence diagram slides",
            remindAtText = "Tomorrow • 8:30 AM",
            done = false,
        ),
    )

    val sampleGoals: List<StudyGoal> = listOf(
        StudyGoal(
            id = "demo-goal-1",
            title = "Complete 5 DSA dynamic programming sets",
            completedSteps = 3L,
            targetSteps = 5L,
        ),
        StudyGoal(
            id = "demo-goal-2",
            title = "Revise 4 Machine Learning lecture decks",
            completedSteps = 2L,
            targetSteps = 4L,
        ),
    )

    val sampleNotes: List<StudyNote> = listOf(
        StudyNote(
            id = "demo-note-1",
            title = "Dijkstra vs Bellman-Ford Complexity",
            courseName = "CS-301",
            content = "Dijkstra runs in O((V + E) log V) with a min-heap for non-negative weights. Bellman-Ford handles negative edge weights in O(VE).",
        ),
    )

    val defaultGpaEntries: List<GpaCourseEntry> = listOf(
        GpaCourseEntry("gpa-1", "Data Structures & Algorithms", 3.0, LetterGrade.A),
        GpaCourseEntry("gpa-2", "Software Architecture", 3.0, LetterGrade.A_MINUS),
        GpaCourseEntry("gpa-3", "Machine Learning Foundations", 4.0, LetterGrade.B_PLUS),
    )
}
