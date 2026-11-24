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
import kotlin.math.roundToInt

enum class AttendanceStatus(val wireValue: String, val label: String) {
    PRESENT("PRESENT", "Present"),
    ABSENT("ABSENT", "Absent"),
    ;

    companion object {
        fun fromWire(value: String?): AttendanceStatus {
            val normalized = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == normalized } ?: PRESENT
        }
    }
}

enum class SemesterEntryMode(val wireValue: String, val label: String) {
    COURSES("COURSES", "Course-by-Course"),
    SUMMARY("SUMMARY", "Previous Semester Totals"),
    ;

    companion object {
        fun fromWire(value: String?): SemesterEntryMode {
            val normalized = value?.trim()?.uppercase(Locale.US)
            return entries.firstOrNull { it.wireValue == normalized } ?: COURSES
        }
    }
}

@Keep
@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val uid: String = "",
    val courseId: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val status: String = AttendanceStatus.PRESENT.wireValue,
    val sessionDateMillis: Long = 0L,
    val monthKey: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedStatus(): AttendanceStatus = AttendanceStatus.fromWire(status)
}

@Keep
@IgnoreExtraProperties
data class GradeScaleItem(
    val letter: String = "",
    val points: Double = 0.0,
)

@Keep
@IgnoreExtraProperties
data class AcademicSettings(
    @DocumentId val id: String = "preferences",
    val uid: String = "",
    val attendanceThresholdPercent: Int = DEFAULT_ATTENDANCE_THRESHOLD,
    val scaleName: String = DEFAULT_SCALE_NAME,
    val customScale: Boolean = false,
    val gradingScale: List<GradeScaleItem> = DefaultGradeScales.standard4PointTemplate,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val DEFAULT_ATTENDANCE_THRESHOLD = 75
        const val DEFAULT_SCALE_NAME = "Standard 4.0 Template (Customizable)"
    }
}

@Keep
@IgnoreExtraProperties
data class SemesterCourseGrade(
    val id: String = "",
    val courseId: String = "",
    val courseCode: String = "",
    val courseName: String = "",
    val creditHours: Double = 3.0,
    val letterGrade: String = "A",
    val gradePoints: Double = 4.0,
)

@Keep
@IgnoreExtraProperties
data class SemesterGpaRecord(
    @DocumentId val id: String = "",
    val uid: String = "",
    val semesterName: String = "",
    val orderIndex: Int = 0,
    val activeSemester: Boolean = false,
    val entryMode: String = SemesterEntryMode.COURSES.wireValue,
    val summaryCredits: Double = 0.0,
    val summaryGpa: Double = 0.0,
    val courses: List<SemesterCourseGrade> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedEntryMode(): SemesterEntryMode = SemesterEntryMode.fromWire(entryMode)
}

object DefaultGradeScales {
    val standard4PointTemplate: List<GradeScaleItem> = listOf(
        GradeScaleItem("A", 4.00),
        GradeScaleItem("A-", 3.70),
        GradeScaleItem("B+", 3.30),
        GradeScaleItem("B", 3.00),
        GradeScaleItem("B-", 2.70),
        GradeScaleItem("C+", 2.30),
        GradeScaleItem("C", 2.00),
        GradeScaleItem("C-", 1.70),
        GradeScaleItem("D+", 1.30),
        GradeScaleItem("D", 1.00),
        GradeScaleItem("F", 0.00),
    )

    val plusOnly4PointTemplate: List<GradeScaleItem> = listOf(
        GradeScaleItem("A+", 4.00),
        GradeScaleItem("A", 4.00),
        GradeScaleItem("B+", 3.50),
        GradeScaleItem("B", 3.00),
        GradeScaleItem("C+", 2.50),
        GradeScaleItem("C", 2.00),
        GradeScaleItem("D", 1.00),
        GradeScaleItem("F", 0.00),
    )
}

data class MonthlyAttendanceStat(
    val monthKey: String,
    val monthLabel: String,
    val totalClasses: Long,
    val attendedClasses: Long,
    val absentClasses: Long,
    val percentage: Double,
)

data class CourseAttendanceSummary(
    val course: StudentCourse,
    val totalClasses: Long,
    val attendedClasses: Long,
    val absentClasses: Long,
    val percentage: Double,
    val hasSessions: Boolean,
    val thresholdPercent: Int,
    val meetsThreshold: Boolean,
    val shortageWarning: Boolean,
    val nearThresholdWarning: Boolean,
    val allowableAbsencesRemaining: Long,
    val classesNeededToRecover: Long?, // null if threshold == 100 and already has an absence
    val monthlyStats: List<MonthlyAttendanceStat>,
    val logs: List<AttendanceLog>,
)

data class OverallAttendanceSummary(
    val totalClasses: Long,
    val attendedClasses: Long,
    val absentClasses: Long,
    val overallPercentage: Double,
    val coursesBelowThresholdCount: Int,
    val thresholdPercent: Int,
    val monthlyStats: List<MonthlyAttendanceStat>,
)

object AttendanceCalculator {

    fun calculatePercentage(attendedClasses: Long, totalClasses: Long): Double {
        if (totalClasses <= 0L) return 0.0
        val safeAttended = attendedClasses.coerceIn(0L, totalClasses)
        return ((safeAttended.toDouble() / totalClasses.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Exact integer-arithmetic computation for maximum consecutive upcoming absences allowed
     * while maintaining (attended / (total + k)) * 100 >= thresholdPercent.
     */
    fun calculateRemainingAllowableAbsences(
        attendedClasses: Long,
        totalClasses: Long,
        thresholdPercent: Int,
    ): Long {
        if (totalClasses <= 0L) return 0L
        val threshold = thresholdPercent.coerceIn(1, 100)
        val safeAttended = attendedClasses.coerceIn(0L, totalClasses)
        if (safeAttended * 100L < threshold * totalClasses) return 0L
        if (threshold == 100) return 0L
        return (((safeAttended * 100L) / threshold) - totalClasses).coerceAtLeast(0L)
    }

    /**
     * Exact integer-arithmetic computation for consecutive upcoming classes a student must attend
     * to recover from shortage to >= thresholdPercent. Returns null when threshold == 100 and absent > 0.
     */
    fun calculateClassesNeededToRecover(
        attendedClasses: Long,
        totalClasses: Long,
        thresholdPercent: Int,
    ): Long? {
        if (totalClasses <= 0L) return 0L
        val threshold = thresholdPercent.coerceIn(1, 100)
        val safeAttended = attendedClasses.coerceIn(0L, totalClasses)
        if (safeAttended * 100L >= threshold * totalClasses) return 0L
        if (threshold >= 100) return null
        val denominator = (100L - threshold).coerceAtLeast(1L)
        val numerator = (threshold * totalClasses) - (safeAttended * 100L)
        return ((numerator + denominator - 1L) / denominator).coerceAtLeast(0L)
    }

    fun summarizeMonthlyLogs(logs: List<AttendanceLog>): List<MonthlyAttendanceStat> {
        if (logs.isEmpty()) return emptyList()
        return logs
            .groupBy { log -> log.monthKey.ifBlank { formatMonthKey(log.sessionDateMillis) } }
            .entries
            .sortedByDescending { it.key }
            .map { (monthKey, monthLogs) ->
                val total = monthLogs.size.toLong()
                val attended = monthLogs.count { it.resolvedStatus() == AttendanceStatus.PRESENT }.toLong()
                val absent = (total - attended).coerceAtLeast(0L)
                MonthlyAttendanceStat(
                    monthKey = monthKey,
                    monthLabel = formatMonthLabel(monthKey),
                    totalClasses = total,
                    attendedClasses = attended,
                    absentClasses = absent,
                    percentage = calculatePercentage(attended, total),
                )
            }
    }

    fun summarizeCourse(
        course: StudentCourse,
        courseLogs: List<AttendanceLog>,
        thresholdPercent: Int,
    ): CourseAttendanceSummary {
        val safeThreshold = thresholdPercent.coerceIn(1, 100)
        val total = course.totalClasses.coerceAtLeast(0L)
        val attended = course.attendedClasses.coerceIn(0L, total)
        val absent = (total - attended).coerceAtLeast(0L)
        val hasSessions = total > 0L
        val percentage = calculatePercentage(attended, total)
        val meetsThreshold = if (!hasSessions) true else (attended * 100L >= safeThreshold * total)
        val shortage = hasSessions && !meetsThreshold
        val allowable = calculateRemainingAllowableAbsences(attended, total, safeThreshold)
        val recovery = calculateClassesNeededToRecover(attended, total, safeThreshold)
        val nearThreshold = hasSessions && meetsThreshold && allowable == 0L

        return CourseAttendanceSummary(
            course = course,
            totalClasses = total,
            attendedClasses = attended,
            absentClasses = absent,
            percentage = percentage,
            hasSessions = hasSessions,
            thresholdPercent = safeThreshold,
            meetsThreshold = meetsThreshold,
            shortageWarning = shortage,
            nearThresholdWarning = nearThreshold,
            allowableAbsencesRemaining = allowable,
            classesNeededToRecover = recovery,
            monthlyStats = summarizeMonthlyLogs(courseLogs),
            logs = courseLogs.sortedByDescending { it.sessionDateMillis },
        )
    }

    fun summarizeOverall(
        courseSummaries: List<CourseAttendanceSummary>,
        allLogs: List<AttendanceLog>,
        thresholdPercent: Int,
    ): OverallAttendanceSummary {
        val total = courseSummaries.sumOf { it.totalClasses }
        val attended = courseSummaries.sumOf { it.attendedClasses }
        val absent = (total - attended).coerceAtLeast(0L)
        return OverallAttendanceSummary(
            totalClasses = total,
            attendedClasses = attended,
            absentClasses = absent,
            overallPercentage = calculatePercentage(attended, total),
            coursesBelowThresholdCount = courseSummaries.count { it.shortageWarning },
            thresholdPercent = thresholdPercent.coerceIn(1, 100),
            monthlyStats = summarizeMonthlyLogs(allLogs),
        )
    }

    fun formatMonthKey(epochMillis: Long): String {
        val safeMillis = if (epochMillis > 0L) epochMillis else System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM", Locale.US)
        return formatter.format(Date(safeMillis))
    }

    fun formatMonthLabel(monthKey: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM", Locale.US).apply { isLenient = false }
            val date = parser.parse(monthKey) ?: return monthKey
            SimpleDateFormat("MMM yyyy", Locale.US).format(date)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun formatSessionDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return "No date"
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
    }

    fun startOfDayOffset(daysOffset: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysOffset)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

data class SemesterCalculatedMetrics(
    val semester: SemesterGpaRecord,
    val totalCredits: Double,
    val qualityPoints: Double,
    val semesterGpa: Double,
    val cumulativeCreditsAfter: Double,
    val cumulativeCgpaAfter: Double,
)

data class CgpaDashboardSummary(
    val cgpa: Double,
    val totalCredits: Double,
    val totalQualityPoints: Double,
    val semesterMetrics: List<SemesterCalculatedMetrics>,
    val activeSemesterMetrics: SemesterCalculatedMetrics?,
    val maxScalePoints: Double,
)

object AcademicGpaCalculator {

    fun calculateCourseListGpa(courses: List<SemesterCourseGrade>): Double {
        val valid = courses.filter { it.creditHours > 0.0 && it.creditHours.isFinite() && it.gradePoints >= 0.0 && it.gradePoints.isFinite() }
        if (valid.isEmpty()) return 0.0
        val totalCredits = valid.sumOf { it.creditHours }
        if (totalCredits <= 0.0) return 0.0
        val weightedPoints = valid.sumOf { it.gradePoints * it.creditHours }
        return weightedPoints / totalCredits
    }

    fun calculateCourseListCredits(courses: List<SemesterCourseGrade>): Double =
        courses.filter { it.creditHours > 0.0 && it.creditHours.isFinite() }.sumOf { it.creditHours }

    fun calculateSemesterValues(record: SemesterGpaRecord): Triple<Double, Double, Double> {
        return when (record.resolvedEntryMode()) {
            SemesterEntryMode.SUMMARY -> {
                val credits = record.summaryCredits.takeIf { it > 0.0 && it.isFinite() } ?: 0.0
                val gpa = record.summaryGpa.takeIf { it >= 0.0 && it.isFinite() } ?: 0.0
                val qp = credits * gpa
                Triple(credits, qp, if (credits > 0.0) gpa else 0.0)
            }
            SemesterEntryMode.COURSES -> {
                val credits = calculateCourseListCredits(record.courses)
                if (credits <= 0.0) {
                    Triple(0.0, 0.0, 0.0)
                } else {
                    val qp = record.courses
                        .filter { it.creditHours > 0.0 && it.creditHours.isFinite() && it.gradePoints >= 0.0 && it.gradePoints.isFinite() }
                        .sumOf { it.creditHours * it.gradePoints }
                    Triple(credits, qp, qp / credits)
                }
            }
        }
    }

    fun calculateCgpaSummary(
        semesters: List<SemesterGpaRecord>,
        scale: List<GradeScaleItem>,
    ): CgpaDashboardSummary {
        val maxScale = (scale.maxOfOrNull { it.points } ?: 4.0).coerceAtLeast(1.0)
        if (semesters.isEmpty()) {
            return CgpaDashboardSummary(
                cgpa = 0.0,
                totalCredits = 0.0,
                totalQualityPoints = 0.0,
                semesterMetrics = emptyList(),
                activeSemesterMetrics = null,
                maxScalePoints = maxScale,
            )
        }

        val ordered = semesters.sortedWith(compareBy<SemesterGpaRecord> { it.orderIndex }.thenBy { it.semesterName })
        var runningCredits = 0.0
        var runningQualityPoints = 0.0

        val metrics = ordered.map { sem ->
            val (semCredits, semQualityPoints, semGpa) = calculateSemesterValues(sem)
            runningCredits += semCredits
            runningQualityPoints += semQualityPoints
            val cumulativeCgpa = if (runningCredits > 0.0) runningQualityPoints / runningCredits else 0.0
            SemesterCalculatedMetrics(
                semester = sem,
                totalCredits = semCredits,
                qualityPoints = semQualityPoints,
                semesterGpa = semGpa,
                cumulativeCreditsAfter = runningCredits,
                cumulativeCgpaAfter = cumulativeCgpa,
            )
        }

        val finalCgpa = if (runningCredits > 0.0) runningQualityPoints / runningCredits else 0.0
        val active = metrics.firstOrNull { it.semester.activeSemester } ?: metrics.lastOrNull()

        return CgpaDashboardSummary(
            cgpa = finalCgpa,
            totalCredits = runningCredits,
            totalQualityPoints = runningQualityPoints,
            semesterMetrics = metrics,
            activeSemesterMetrics = active,
            maxScalePoints = maxScale,
        )
    }

    fun resolveGradePoints(letter: String, scale: List<GradeScaleItem>): Double? {
        val clean = letter.trim()
        return scale.firstOrNull { it.letter.equals(clean, ignoreCase = true) }?.points
    }

    fun roundToTwoDecimals(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 100.0).roundToInt() / 100.0
    }
}

data class SaveAttendanceLogRequest(
    val courseId: String,
    val courseCode: String,
    val courseName: String,
    val status: AttendanceStatus,
    val sessionDateMillis: Long,
    val notes: String,
)

data class SaveAcademicSettingsRequest(
    val attendanceThresholdPercent: Int,
    val scaleName: String,
    val customScale: Boolean,
    val gradingScale: List<GradeScaleItem>,
)

data class SaveSemesterGpaRequest(
    val semesterName: String,
    val orderIndex: Int,
    val activeSemester: Boolean,
    val entryMode: SemesterEntryMode,
    val summaryCredits: Double,
    val summaryGpa: Double,
    val courses: List<SemesterCourseGrade>,
)

object AttendanceGpaMappers {

    fun toCreateAttendanceLogPayload(
        uid: String,
        request: SaveAttendanceLogRequest,
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "courseId" to request.courseId.trim(),
        "courseCode" to request.courseCode.trim(),
        "courseName" to request.courseName.trim(),
        "status" to request.status.wireValue,
        "sessionDateMillis" to request.sessionDateMillis,
        "monthKey" to AttendanceCalculator.formatMonthKey(request.sessionDateMillis),
        "notes" to request.notes.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateAttendanceLogPayload(
        status: AttendanceStatus,
        sessionDateMillis: Long,
        notes: String,
    ): Map<String, Any> = mapOf(
        "status" to status.wireValue,
        "sessionDateMillis" to sessionDateMillis,
        "monthKey" to AttendanceCalculator.formatMonthKey(sessionDateMillis),
        "notes" to notes.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toSaveAcademicSettingsPayload(
        uid: String,
        request: SaveAcademicSettingsRequest,
        isCreate: Boolean,
    ): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "attendanceThresholdPercent" to request.attendanceThresholdPercent.coerceIn(1, 100),
            "scaleName" to request.scaleName.trim(),
            "customScale" to request.customScale,
            "gradingScale" to request.gradingScale.map {
                mapOf(
                    "letter" to it.letter.trim(),
                    "points" to it.points,
                )
            },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isCreate) {
            base["uid"] = uid
            base["createdAt"] = FieldValue.serverTimestamp()
        }
        return base
    }

    fun toCreateSemesterGpaPayload(
        uid: String,
        request: SaveSemesterGpaRequest,
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "semesterName" to request.semesterName.trim(),
        "orderIndex" to request.orderIndex.coerceIn(0, 100),
        "activeSemester" to request.activeSemester,
        "entryMode" to request.entryMode.wireValue,
        "summaryCredits" to request.summaryCredits,
        "summaryGpa" to request.summaryGpa,
        "courses" to request.courses.map(::courseToMap),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateSemesterGpaPayload(
        request: SaveSemesterGpaRequest,
    ): Map<String, Any> = mapOf(
        "semesterName" to request.semesterName.trim(),
        "orderIndex" to request.orderIndex.coerceIn(0, 100),
        "activeSemester" to request.activeSemester,
        "entryMode" to request.entryMode.wireValue,
        "summaryCredits" to request.summaryCredits,
        "summaryGpa" to request.summaryGpa,
        "courses" to request.courses.map(::courseToMap),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun courseToMap(course: SemesterCourseGrade): Map<String, Any> = mapOf(
        "id" to course.id.trim(),
        "courseId" to course.courseId.trim(),
        "courseCode" to course.courseCode.trim(),
        "courseName" to course.courseName.trim(),
        "creditHours" to course.creditHours,
        "letterGrade" to course.letterGrade.trim(),
        "gradePoints" to course.gradePoints,
    )
}
