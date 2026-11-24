package com.example.unimate.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthGridCell(
    val dayStartMillis: Long,
    val dayOfMonth: Int,
    val isInCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
)

object CalendarDateEngine {
    fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun addDays(millis: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, days)
        }
        return cal.timeInMillis
    }

    fun addMonths(millis: Long, months: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.MONTH, months)
        }
        return cal.timeInMillis
    }

    fun startOfWeekMonday(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = startOfDayMillis(millis)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }
        return cal.timeInMillis
    }

    fun weekDays(selectedDateMillis: Long): List<Long> {
        val monday = startOfWeekMonday(selectedDateMillis)
        return (0 until 7).map { offset -> addDays(monday, offset) }
    }

    fun buildMonthGrid(
        selectedDateMillis: Long,
        todayMillis: Long = System.currentTimeMillis(),
    ): List<MonthGridCell> {
        val selectedDayStart = startOfDayMillis(selectedDateMillis)
        val todayDayStart = startOfDayMillis(todayMillis)

        val monthCal = Calendar.getInstance().apply {
            timeInMillis = selectedDayStart
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val targetMonth = monthCal.get(Calendar.MONTH)
        val targetYear = monthCal.get(Calendar.YEAR)

        val dayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK)
        val leadingEmpty = (dayOfWeek - Calendar.MONDAY + 7) % 7
        val gridStart = addDays(monthCal.timeInMillis, -leadingEmpty)

        return (0 until 42).map { index ->
            val cellMillis = startOfDayMillis(addDays(gridStart, index))
            val cellCal = Calendar.getInstance().apply { timeInMillis = cellMillis }
            MonthGridCell(
                dayStartMillis = cellMillis,
                dayOfMonth = cellCal.get(Calendar.DAY_OF_MONTH),
                isInCurrentMonth = cellCal.get(Calendar.MONTH) == targetMonth &&
                    cellCal.get(Calendar.YEAR) == targetYear,
                isToday = cellMillis == todayDayStart,
                isSelected = cellMillis == selectedDayStart,
            )
        }
    }

    fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
        val calA = Calendar.getInstance().apply { timeInMillis = aMillis }
        val calB = Calendar.getInstance().apply { timeInMillis = bMillis }
        return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
    }

    fun formatMonthYear(millis: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(millis))

    fun formatDayFull(millis: Long): String =
        SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(millis))

    fun formatWeekRange(millis: Long): String {
        val start = startOfWeekMonday(millis)
        val end = addDays(start, 6)
        val fmtStart = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(start))
        val fmtEnd = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(end))
        return "$fmtStart – $fmtEnd"
    }

    fun formatDayChipShort(millis: Long): Pair<String, String> {
        val dow = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
        val dom = SimpleDateFormat("d", Locale.getDefault()).format(Date(millis))
        return dow to dom
    }

    fun projectEventsForDay(
        dayMillis: Long,
        calendarEvents: List<CalendarEvent>,
        timetableClasses: List<TimetableClass>,
        assignments: List<StudentAssignment>,
        quizzes: List<StudentQuiz>,
        exams: List<StudentExam>,
    ): List<CalendarDisplayItem> {
        val dayStart = startOfDayMillis(dayMillis)
        val dayEnd = endOfDayMillis(dayMillis)
        val items = mutableListOf<CalendarDisplayItem>()

        calendarEvents.forEach { event ->
            val evStart = event.startAtMillis
            val evEnd = maxOf(event.endAtMillis, evStart)
            if (evStart in 1..dayEnd && evEnd >= dayStart) {
                val timePart = if (event.allDay) {
                    "All day"
                } else {
                    "${AcademicDateFormatters.formatTimeOnly(evStart)} – ${AcademicDateFormatters.formatTimeOnly(evEnd)}"
                }
                val locationPart = event.locationOrCourse.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
                items += CalendarDisplayItem(
                    key = "cal-${event.id}",
                    title = event.title,
                    subtitle = "$timePart$locationPart",
                    category = event.resolvedCategory(),
                    startAtMillis = evStart,
                    endAtMillis = evEnd,
                    allDay = event.allDay,
                    source = CalendarDisplaySource.CALENDAR_EVENT,
                    editableEvent = event,
                )
            }
        }

        val dayOption = DayOfWeekOption.today(Calendar.getInstance().apply { timeInMillis = dayStart })
        timetableClasses.filter { it.resolvedDay() == dayOption }.forEach { cls ->
            val start = dayStart + cls.startTimeMinutes * 60_000L
            val end = dayStart + cls.endTimeMinutes * 60_000L
            items += CalendarDisplayItem(
                key = "tt-${cls.id}-$dayStart",
                title = "${cls.courseCode} • ${cls.subjectName}",
                subtitle = "${cls.formattedTimeRange()} • ${cls.classroom} (Timetable)",
                category = CalendarEventCategory.CLASSES,
                startAtMillis = start,
                endAtMillis = end,
                allDay = false,
                source = CalendarDisplaySource.TIMETABLE_SYNCED,
            )
        }

        assignments.filter { it.deadlineMillis in dayStart..dayEnd }.forEach { assignment ->
            items += CalendarDisplayItem(
                key = "assign-${assignment.id}",
                title = assignment.title,
                subtitle = "Due ${AcademicDateFormatters.formatTimeOnly(assignment.deadlineMillis)} • ${assignment.courseName}",
                category = CalendarEventCategory.ASSIGNMENTS,
                startAtMillis = assignment.deadlineMillis,
                endAtMillis = assignment.deadlineMillis,
                allDay = false,
                source = CalendarDisplaySource.ASSIGNMENT_SYNCED,
            )
        }

        quizzes.filter { it.scheduledAtMillis in dayStart..dayEnd }.forEach { quiz ->
            items += CalendarDisplayItem(
                key = "quiz-${quiz.id}",
                title = quiz.title,
                subtitle = "${AcademicDateFormatters.formatTimeOnly(quiz.scheduledAtMillis)} • ${quiz.courseName} (${quiz.totalMarks.toInt()} marks)",
                category = CalendarEventCategory.QUIZZES,
                startAtMillis = quiz.scheduledAtMillis,
                endAtMillis = quiz.scheduledAtMillis,
                allDay = false,
                source = CalendarDisplaySource.QUIZ_SYNCED,
            )
        }

        exams.filter { it.examAtMillis in dayStart..dayEnd }.forEach { exam ->
            items += CalendarDisplayItem(
                key = "exam-${exam.id}",
                title = "${exam.resolvedExamType().label}: ${exam.title}",
                subtitle = "${AcademicDateFormatters.formatTimeOnly(exam.examAtMillis)} • ${exam.courseName} • Room ${exam.room}",
                category = CalendarEventCategory.EXAMS,
                startAtMillis = exam.examAtMillis,
                endAtMillis = exam.examAtMillis,
                allDay = false,
                source = CalendarDisplaySource.EXAM_SYNCED,
            )
        }

        return items.sortedWith(
            compareByDescending<CalendarDisplayItem> { it.allDay }
                .thenBy { it.startAtMillis }
                .thenBy { it.title.lowercase(Locale.US) },
        )
    }
}
