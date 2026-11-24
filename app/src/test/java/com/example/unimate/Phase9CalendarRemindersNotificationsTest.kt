package com.example.unimate

import com.example.unimate.data.model.AppNotification
import com.example.unimate.data.model.AssignmentPriority
import com.example.unimate.data.model.AssignmentStatus
import com.example.unimate.data.model.CalendarDateEngine
import com.example.unimate.data.model.CalendarDisplaySource
import com.example.unimate.data.model.CalendarEvent
import com.example.unimate.data.model.CalendarEventCategory
import com.example.unimate.data.model.CalendarReminderMappers
import com.example.unimate.data.model.CalendarViewMode
import com.example.unimate.data.model.CreateAppNotificationRequest
import com.example.unimate.data.model.DayOfWeekOption
import com.example.unimate.data.model.ExamPreparationStatus
import com.example.unimate.data.model.ExamType
import com.example.unimate.data.model.NotificationFilter
import com.example.unimate.data.model.NotificationSourceType
import com.example.unimate.data.model.QuizPreparationStatus
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.data.model.SaveCalendarEventRequest
import com.example.unimate.data.model.SaveScheduledReminderRequest
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.repository.CalendarRemindersRepository
import com.example.unimate.notifications.ReminderScheduler
import com.example.unimate.ui.calendar.CalendarUiState
import com.example.unimate.ui.calendar.CalendarViewModel
import com.example.unimate.ui.notifications.NotificationsUiState
import com.example.unimate.ui.notifications.NotificationsViewModel
import com.example.unimate.ui.reminders.RemindersUiState
import com.example.unimate.ui.reminders.RemindersViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class Phase9CalendarRemindersNotificationsTest {

    private val testDispatcher = StandardTestDispatcher()

    // Monday 2026-11-23 10:00 AM local time
    private val fixedNow: Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 23, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mappers omit document ids, align with rules, and preserve server timestamps`() {
        val eventPayload = CalendarReminderMappers.toCreateCalendarEventPayload(
            uid = "uid-1",
            request = SaveCalendarEventRequest(
                title = "  Convocation Ceremony ",
                description = "Main Auditorium",
                category = CalendarEventCategory.UNIVERSITY_EVENTS,
                locationOrCourse = "Campus Hall",
                startAtMillis = fixedNow,
                endAtMillis = fixedNow + 7_200_000L,
                allDay = false,
            ),
        )
        assertFalse(eventPayload.containsKey("id"))
        assertEquals("Convocation Ceremony", eventPayload["title"])
        assertEquals("UNIVERSITY_EVENTS", eventPayload["category"])
        assertEquals(false, eventPayload["allDay"])
        assertTrue(eventPayload["createdAt"] is FieldValue)

        val reminderPayload = CalendarReminderMappers.toCreateReminderPayload(
            uid = "uid-1",
            request = SaveScheduledReminderRequest(
                title = "OS Midterm Prep",
                notes = "Chapter 4",
                category = ReminderCategory.EXAM,
                triggerAtMillis = fixedNow + 86_400_000L,
                leadMinutes = 30L,
                enabled = true,
            ),
            workRequestId = "work-123",
        )
        assertFalse(reminderPayload.containsKey("id"))
        assertEquals("EXAM", reminderPayload["category"])
        assertEquals(30L, reminderPayload["leadMinutes"])
        assertEquals(true, reminderPayload["enabled"])
        assertEquals(false, reminderPayload["completed"])

        val notifPayload = CalendarReminderMappers.toCreateNotificationPayload(
            uid = "uid-1",
            request = CreateAppNotificationRequest(
                title = "Quiz Reminder",
                body = "Room 104",
                category = ReminderCategory.QUIZ,
                sourceType = NotificationSourceType.REMINDER,
                sourceId = "rem-1",
                deliveredAtMillis = fixedNow,
            ),
        )
        assertEquals(false, notifPayload["read"])
        assertEquals("QUIZ", notifPayload["category"])
    }

    @Test
    fun `calendar date engine builds 42-cell month grid, 7-day week, and projects synced academic items`() {
        val monthCells = CalendarDateEngine.buildMonthGrid(selectedDateMillis = fixedNow, todayMillis = fixedNow)
        assertEquals(42, monthCells.size)
        assertEquals(1, monthCells.count { it.isToday })
        assertEquals(1, monthCells.count { it.isSelected })

        val weekDays = CalendarDateEngine.weekDays(fixedNow)
        assertEquals(7, weekDays.size)
        assertTrue(CalendarDateEngine.isSameDay(weekDays.first(), fixedNow))

        val dayStart = CalendarDateEngine.startOfDayMillis(fixedNow)
        val projected = CalendarDateEngine.projectEventsForDay(
            dayMillis = dayStart,
            calendarEvents = listOf(
                CalendarEvent(
                    id = "ev-1",
                    uid = "uid-1",
                    title = "National Holiday",
                    category = CalendarEventCategory.HOLIDAYS.wireValue,
                    startAtMillis = dayStart,
                    endAtMillis = CalendarDateEngine.endOfDayMillis(dayStart),
                    allDay = true,
                ),
            ),
            timetableClasses = listOf(
                TimetableClass(
                    id = "tt-1",
                    uid = "uid-1",
                    dayOfWeek = DayOfWeekOption.MONDAY.wireValue,
                    subjectName = "Algorithms",
                    courseCode = "CS-301",
                    classroom = "Room 201",
                    startTimeMinutes = 540,
                    endTimeMinutes = 630,
                ),
            ),
            assignments = listOf(
                StudentAssignment(
                    id = "as-1",
                    uid = "uid-1",
                    title = "Graph HW",
                    courseName = "CS-301",
                    deadlineMillis = dayStart + 18 * 3_600_000L,
                    priority = AssignmentPriority.HIGH.wireValue,
                    status = AssignmentStatus.PENDING.wireValue,
                ),
            ),
            quizzes = listOf(
                StudentQuiz(
                    id = "qz-1",
                    uid = "uid-1",
                    title = "Pop Quiz 2",
                    courseName = "CS-301",
                    scheduledAtMillis = dayStart + 14 * 3_600_000L,
                    totalMarks = 15.0,
                    preparationStatus = QuizPreparationStatus.READY.wireValue,
                ),
            ),
            exams = listOf(
                StudentExam(
                    id = "ex-1",
                    uid = "uid-1",
                    title = "Midterm",
                    examType = ExamType.MIDTERM.wireValue,
                    courseName = "CS-301",
                    examAtMillis = dayStart + 16 * 3_600_000L,
                    room = "Hall A",
                    preparationStatus = ExamPreparationStatus.REVISING.wireValue,
                ),
            ),
        )

        assertEquals(5, projected.size)
        assertEquals(CalendarEventCategory.HOLIDAYS, projected.first().category)
        assertTrue(projected.any { it.source == CalendarDisplaySource.TIMETABLE_SYNCED && it.category == CalendarEventCategory.CLASSES })
        assertTrue(projected.any { it.source == CalendarDisplaySource.ASSIGNMENT_SYNCED && it.category == CalendarEventCategory.ASSIGNMENTS })
        assertTrue(projected.any { it.source == CalendarDisplaySource.QUIZ_SYNCED && it.category == CalendarEventCategory.QUIZZES })
        assertTrue(projected.any { it.source == CalendarDisplaySource.EXAM_SYNCED && it.category == CalendarEventCategory.EXAMS })
    }

    @Test
    fun `calendar viewmodel supports monthly weekly daily views, 7 categories, crud, and error retry`() = runTest {
        val repo = FakeCalendarRemindersRepository()
        val vm = CalendarViewModel(uid = "uid-1", repository = repo, clockMillis = { fixedNow })
        val job = launch { vm.uiState.collect {} }

        assertEquals(CalendarUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CalendarUiState.Success)

        // Add events across multiple categories
        vm.openAddEventDialog(CalendarEventCategory.PERSONAL_EVENTS)
        vm.onTitleChanged("Dentist Appointment")
        vm.onLocationOrCourseChanged("City Clinic")
        vm.saveEvent()
        advanceUntilIdle()

        var success = vm.uiState.value as CalendarUiState.Success
        assertEquals(1, success.totalUserEventsCount)
        assertEquals(1, success.selectedDayItems.size)

        // Switch views and filter by category
        vm.selectViewMode(CalendarViewMode.WEEKLY)
        advanceUntilIdle()
        assertEquals(CalendarViewMode.WEEKLY, (vm.uiState.value as CalendarUiState.Success).viewMode)

        vm.selectViewMode(CalendarViewMode.DAILY)
        vm.selectCategoryFilter(CalendarEventCategory.HOLIDAYS)
        advanceUntilIdle()
        success = vm.uiState.value as CalendarUiState.Success
        assertEquals(CalendarViewMode.DAILY, success.viewMode)
        assertEquals(0, success.selectedDayItems.size)

        // Clear category filter and edit event
        vm.selectCategoryFilter(null)
        advanceUntilIdle()
        val addedEvent = (vm.uiState.value as CalendarUiState.Success).selectedDayItems.first().editableEvent!!
        vm.openEditEventDialog(addedEvent)
        vm.onCategoryChanged(CalendarEventCategory.UNIVERSITY_EVENTS)
        vm.onTitleChanged("Campus Orientation")
        vm.saveEvent()
        advanceUntilIdle()

        success = vm.uiState.value as CalendarUiState.Success
        assertEquals("Campus Orientation", success.selectedDayItems.first().title)
        assertEquals(CalendarEventCategory.UNIVERSITY_EVENTS, success.selectedDayItems.first().category)

        // Date navigation prev/next/today
        val beforeNav = success.selectedDateMillis
        vm.navigateNextPeriod()
        advanceUntilIdle()
        assertTrue((vm.uiState.value as CalendarUiState.Success).selectedDateMillis > beforeNav)
        vm.jumpToToday()
        advanceUntilIdle()
        assertEquals(CalendarDateEngine.startOfDayMillis(fixedNow), (vm.uiState.value as CalendarUiState.Success).selectedDateMillis)

        // Delete event
        vm.deleteEvent(addedEvent.id)
        advanceUntilIdle()
        assertEquals(0, (vm.uiState.value as CalendarUiState.Success).totalUserEventsCount)

        // Error + Retry
        repo.failCalendar = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CalendarUiState.Error)

        repo.failCalendar = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CalendarUiState.Success)

        job.cancel()
    }

    @Test
    fun `reminders viewmodel validates, schedules via ReminderScheduler, toggles pause complete, and deletes`() = runTest {
        val repo = FakeCalendarRemindersRepository()
        val scheduler = FakeReminderScheduler()
        val vm = RemindersViewModel(
            uid = "uid-1",
            repository = repo,
            scheduler = scheduler,
            clockMillis = { fixedNow },
        )
        val job = launch { vm.uiState.collect {} }

        assertEquals(RemindersUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(RemindersUiState.Empty, vm.uiState.value)

        // Validation guard
        vm.openAddDialog(ReminderCategory.STUDY)
        vm.saveReminder()
        assertNotNull(vm.editorState.value.errorMessage)

        // Save valid Study reminder
        vm.onTitleChanged("Revise Dynamic Programming")
        vm.onNotesChanged("Solve 4 DP problems")
        vm.onLeadMinutesChanged(30L)
        vm.saveReminder()
        advanceUntilIdle()

        var success = vm.uiState.value as RemindersUiState.Success
        assertEquals(1, success.allReminders.size)
        val created = success.allReminders.first()
        assertEquals(ReminderCategory.STUDY, created.resolvedCategory())
        assertTrue(scheduler.scheduledIds.contains(created.id))

        // Pause scheduling -> cancels in WorkManager
        vm.toggleEnabled(created)
        advanceUntilIdle()
        assertTrue(scheduler.cancelledIds.contains(created.id))
        success = vm.uiState.value as RemindersUiState.Success
        assertFalse(success.allReminders.first().enabled)

        // Re-enable -> schedules again
        vm.toggleEnabled(success.allReminders.first())
        advanceUntilIdle()
        assertTrue((vm.uiState.value as RemindersUiState.Success).allReminders.first().enabled)

        // Complete -> disables & cancels
        vm.toggleCompleted((vm.uiState.value as RemindersUiState.Success).allReminders.first())
        advanceUntilIdle()
        val completed = (vm.uiState.value as RemindersUiState.Success).allReminders.first()
        assertTrue(completed.completed)
        assertFalse(completed.enabled)

        // Category filter
        vm.selectCategoryFilter(ReminderCategory.EXAM)
        advanceUntilIdle()
        assertEquals(0, (vm.uiState.value as RemindersUiState.Success).filteredReminders.size)

        vm.selectCategoryFilter(null)
        vm.deleteReminder(completed.id)
        advanceUntilIdle()
        assertEquals(RemindersUiState.Empty, vm.uiState.value)

        // Verify all 6 categories exist
        assertEquals(6, ReminderCategory.entries.size)

        job.cancel()
    }

    @Test
    fun `notifications viewmodel tracks unread and read lists, marks read, clears read and clears all`() = runTest {
        val repo = FakeCalendarRemindersRepository()
        val vm = NotificationsViewModel(uid = "uid-1", repository = repo)
        val job = launch { vm.uiState.collect {} }

        assertEquals(NotificationsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(NotificationsUiState.Empty, vm.uiState.value)

        repo.notificationsFlow.value = listOf(
            AppNotification(
                id = "n-1",
                uid = "uid-1",
                title = "Class in 15m",
                body = "Algorithms CS-301",
                category = ReminderCategory.CLASS.wireValue,
                deliveredAtMillis = fixedNow,
                read = false,
            ),
            AppNotification(
                id = "n-2",
                uid = "uid-1",
                title = "Assignment Due",
                body = "OS Lab",
                category = ReminderCategory.ASSIGNMENT.wireValue,
                deliveredAtMillis = fixedNow - 3_600_000L,
                read = false,
            ),
            AppNotification(
                id = "n-3",
                uid = "uid-1",
                title = "Old Quiz Alert",
                body = "Done",
                category = ReminderCategory.QUIZ.wireValue,
                deliveredAtMillis = fixedNow - 7_200_000L,
                read = true,
            ),
        )
        advanceUntilIdle()

        var success = vm.uiState.value as NotificationsUiState.Success
        assertEquals(2, success.unreadCount)
        assertEquals(1, success.readCount)

        // Mark single unread notification as read
        vm.markAsRead("n-1")
        advanceUntilIdle()
        success = vm.uiState.value as NotificationsUiState.Success
        assertEquals(1, success.unreadCount)
        assertEquals(2, success.readCount)

        // Mark remaining unread notification as read
        vm.markAllAsRead()
        advanceUntilIdle()
        success = vm.uiState.value as NotificationsUiState.Success
        assertEquals(0, success.unreadCount)
        assertEquals(3, success.readCount)

        // Filter check
        vm.selectFilter(NotificationFilter.UNREAD)
        advanceUntilIdle()
        assertEquals(NotificationFilter.UNREAD, (vm.uiState.value as NotificationsUiState.Success).selectedFilter)

        // Clear read notifications
        vm.clearReadNotifications()
        advanceUntilIdle()
        assertEquals(NotificationsUiState.Empty, vm.uiState.value)

        // Repopulate & clear all
        repo.notificationsFlow.value = listOf(
            AppNotification(id = "n-4", uid = "uid-1", title = "Exam Alert", deliveredAtMillis = fixedNow, read = false),
        )
        advanceUntilIdle()
        vm.clearAllNotifications()
        advanceUntilIdle()
        assertEquals(NotificationsUiState.Empty, vm.uiState.value)

        // Error + Retry
        repo.failNotifications = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is NotificationsUiState.Error)

        job.cancel()
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    val scheduledIds = mutableSetOf<String>()
    val cancelledIds = mutableSetOf<String>()

    override fun scheduleReminder(uid: String, reminder: ScheduledReminder, nowMillis: Long): String {
        scheduledIds += reminder.id
        cancelledIds -= reminder.id
        return "fake-work-${reminder.id}"
    }

    override fun cancelReminder(reminderId: String) {
        cancelledIds += reminderId
        scheduledIds -= reminderId
    }
}

private class FakeCalendarRemindersRepository : CalendarRemindersRepository {
    val eventsFlow = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val remindersFlow = MutableStateFlow<List<ScheduledReminder>>(emptyList())
    val notificationsFlow = MutableStateFlow<List<AppNotification>>(emptyList())

    var failCalendar = false
    var failReminders = false
    var failNotifications = false
    private var nextSeq = 1

    override fun observeCalendarEvents(uid: String): Flow<List<CalendarEvent>> = flow {
        if (failCalendar) throw IllegalStateException("Simulated calendar sync error")
        eventsFlow.collect { emit(it) }
    }

    override suspend fun addCalendarEvent(uid: String, request: SaveCalendarEventRequest): Result<Unit> {
        val id = "ev-${nextSeq++}"
        eventsFlow.value = eventsFlow.value + CalendarEvent(
            id = id,
            uid = uid,
            title = request.title,
            description = request.description,
            category = request.category.wireValue,
            locationOrCourse = request.locationOrCourse,
            startAtMillis = request.startAtMillis,
            endAtMillis = request.endAtMillis,
            allDay = request.allDay,
        )
        return Result.success(Unit)
    }

    override suspend fun updateCalendarEvent(uid: String, eventId: String, request: SaveCalendarEventRequest): Result<Unit> {
        eventsFlow.value = eventsFlow.value.map {
            if (it.id == eventId) {
                it.copy(
                    title = request.title,
                    description = request.description,
                    category = request.category.wireValue,
                    locationOrCourse = request.locationOrCourse,
                    startAtMillis = request.startAtMillis,
                    endAtMillis = request.endAtMillis,
                    allDay = request.allDay,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteCalendarEvent(uid: String, eventId: String): Result<Unit> {
        eventsFlow.value = eventsFlow.value.filterNot { it.id == eventId }
        return Result.success(Unit)
    }

    override fun observeScheduledReminders(uid: String): Flow<List<ScheduledReminder>> = flow {
        if (failReminders) throw IllegalStateException("Simulated reminder sync error")
        remindersFlow.collect { emit(it) }
    }

    override suspend fun addScheduledReminder(
        uid: String,
        reminderId: String,
        request: SaveScheduledReminderRequest,
    ): Result<String> {
        remindersFlow.value = remindersFlow.value + ScheduledReminder(
            id = reminderId,
            uid = uid,
            title = request.title,
            notes = request.notes,
            category = request.category.wireValue,
            triggerAtMillis = request.triggerAtMillis,
            leadMinutes = request.leadMinutes,
            enabled = request.enabled,
            completed = request.completed,
            workRequestId = request.workRequestId,
        )
        return Result.success(reminderId)
    }

    override suspend fun updateScheduledReminder(
        uid: String,
        reminderId: String,
        request: SaveScheduledReminderRequest,
    ): Result<Unit> {
        remindersFlow.value = remindersFlow.value.map {
            if (it.id == reminderId) {
                it.copy(
                    title = request.title,
                    notes = request.notes,
                    category = request.category.wireValue,
                    triggerAtMillis = request.triggerAtMillis,
                    leadMinutes = request.leadMinutes,
                    enabled = request.enabled,
                    completed = request.completed,
                    workRequestId = request.workRequestId,
                )
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateScheduledReminderState(
        uid: String,
        reminderId: String,
        enabled: Boolean,
        completed: Boolean,
        workRequestId: String,
    ): Result<Unit> {
        remindersFlow.value = remindersFlow.value.map {
            if (it.id == reminderId) {
                it.copy(enabled = enabled, completed = completed, workRequestId = workRequestId)
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteScheduledReminder(uid: String, reminderId: String): Result<Unit> {
        remindersFlow.value = remindersFlow.value.filterNot { it.id == reminderId }
        return Result.success(Unit)
    }

    override fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        if (failNotifications) throw IllegalStateException("Simulated notifications error")
        notificationsFlow.collect { emit(it) }
    }

    override suspend fun createNotification(uid: String, request: CreateAppNotificationRequest): Result<Unit> {
        val id = "n-${nextSeq++}"
        notificationsFlow.value = notificationsFlow.value + AppNotification(
            id = id,
            uid = uid,
            title = request.title,
            body = request.body,
            category = request.category.wireValue,
            sourceType = request.sourceType.wireValue,
            sourceId = request.sourceId,
            deliveredAtMillis = request.deliveredAtMillis,
            read = false,
        )
        return Result.success(Unit)
    }

    override suspend fun markNotificationRead(uid: String, notificationId: String, read: Boolean): Result<Unit> {
        notificationsFlow.value = notificationsFlow.value.map {
            if (it.id == notificationId) it.copy(read = read) else it
        }
        return Result.success(Unit)
    }

    override suspend fun markAllNotificationsRead(uid: String, unreadIds: List<String>): Result<Unit> {
        notificationsFlow.value = notificationsFlow.value.map {
            if (unreadIds.contains(it.id)) it.copy(read = true) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteNotification(uid: String, notificationId: String): Result<Unit> {
        notificationsFlow.value = notificationsFlow.value.filterNot { it.id == notificationId }
        return Result.success(Unit)
    }

    override suspend fun clearNotifications(uid: String, notificationIds: List<String>): Result<Unit> {
        notificationsFlow.value = notificationsFlow.value.filterNot { notificationIds.contains(it.id) }
        return Result.success(Unit)
    }
}
