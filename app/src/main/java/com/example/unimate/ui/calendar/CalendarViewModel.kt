package com.example.unimate.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.CalendarDateEngine
import com.example.unimate.data.model.CalendarDisplayItem
import com.example.unimate.data.model.CalendarEvent
import com.example.unimate.data.model.CalendarEventCategory
import com.example.unimate.data.model.CalendarViewMode
import com.example.unimate.data.model.MonthGridCell
import com.example.unimate.data.model.SaveCalendarEventRequest
import com.example.unimate.data.model.StudentAssignment
import com.example.unimate.data.model.StudentExam
import com.example.unimate.data.model.StudentQuiz
import com.example.unimate.data.model.TimetableClass
import com.example.unimate.data.repository.AcademicModulesRepository
import com.example.unimate.data.repository.CalendarRemindersRepository
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CalendarUiState {
    data object Loading : CalendarUiState

    data class Success(
        val viewMode: CalendarViewMode,
        val selectedDateMillis: Long,
        val selectedCategoryFilter: CalendarEventCategory?,
        val monthCells: List<MonthGridCell>,
        val monthItemsByDay: Map<Long, List<CalendarDisplayItem>>,
        val weekDays: List<Long>,
        val weekItemsByDay: Map<Long, List<CalendarDisplayItem>>,
        val selectedDayItems: List<CalendarDisplayItem>,
        val totalUserEventsCount: Int,
        val totalVisiblePeriodItemsCount: Int,
    ) : CalendarUiState

    data class Error(val message: String) : CalendarUiState
}

data class CalendarEventEditorState(
    val isOpen: Boolean = false,
    val editingEventId: String? = null,
    val draftEventId: String = "",
    val title: String = "",
    val description: String = "",
    val category: CalendarEventCategory = CalendarEventCategory.UNIVERSITY_EVENTS,
    val locationOrCourse: String = "",
    val startAtMillis: Long = 0L,
    val endAtMillis: Long = 0L,
    val allDay: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

private data class CalendarSyncedAcademicSnapshot(
    val timetable: List<TimetableClass> = emptyList(),
    val assignments: List<StudentAssignment> = emptyList(),
    val quizzes: List<StudentQuiz> = emptyList(),
    val exams: List<StudentExam> = emptyList(),
)

private data class CalendarFilterState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTHLY,
    val selectedDateMillis: Long = 0L,
    val categoryFilter: CalendarEventCategory? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val uid: String,
    private val repository: CalendarRemindersRepository,
    workspaceRepository: WorkspaceRepository? = null,
    academicModulesRepository: AcademicModulesRepository? = null,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val filterState = MutableStateFlow(
        CalendarFilterState(
            viewMode = CalendarViewMode.MONTHLY,
            selectedDateMillis = CalendarDateEngine.startOfDayMillis(clockMillis()),
            categoryFilter = null,
        ),
    )

    private sealed interface EventsStreamResult {
        data object Loading : EventsStreamResult
        data class Loaded(val events: List<CalendarEvent>) : EventsStreamResult
        data class Failed(val message: String) : EventsStreamResult
    }

    private val eventsStream = retryTrigger.flatMapLatest {
        repository.observeCalendarEvents(uid)
            .map<List<CalendarEvent>, EventsStreamResult> { EventsStreamResult.Loaded(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(EventsStreamResult.Failed(rootError.localizedMessage ?: "Failed to load calendar events."))
            }
    }

    private val syncedAcademicStream: Flow<CalendarSyncedAcademicSnapshot> = combine(
        workspaceRepository?.observeTimetable(uid)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        academicModulesRepository?.observeAssignments(uid)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        academicModulesRepository?.observeQuizzes(uid)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
        academicModulesRepository?.observeExams(uid)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
    ) { timetable, assignments, quizzes, exams ->
        CalendarSyncedAcademicSnapshot(
            timetable = timetable,
            assignments = assignments,
            quizzes = quizzes,
            exams = exams,
        )
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        eventsStream,
        syncedAcademicStream,
        filterState,
    ) { eventsResult, academic, controls ->
        when (eventsResult) {
            EventsStreamResult.Loading -> CalendarUiState.Loading
            is EventsStreamResult.Failed -> CalendarUiState.Error(eventsResult.message)
            is EventsStreamResult.Loaded -> {
                val now = clockMillis()
                val monthCells = CalendarDateEngine.buildMonthGrid(
                    selectedDateMillis = controls.selectedDateMillis,
                    todayMillis = now,
                )
                val weekDays = CalendarDateEngine.weekDays(controls.selectedDateMillis)

                fun itemsForDay(dayMillis: Long): List<CalendarDisplayItem> {
                    val projected = CalendarDateEngine.projectEventsForDay(
                        dayMillis = dayMillis,
                        calendarEvents = eventsResult.events,
                        timetableClasses = academic.timetable,
                        assignments = academic.assignments,
                        quizzes = academic.quizzes,
                        exams = academic.exams,
                    )
                    val category = controls.categoryFilter
                    return if (category == null) projected else projected.filter { it.category == category }
                }

                val monthItemsByDay = monthCells.associate { cell ->
                    cell.dayStartMillis to itemsForDay(cell.dayStartMillis)
                }
                val weekItemsByDay = weekDays.associateWith { dayMillis ->
                    itemsForDay(dayMillis)
                }
                val selectedDayStart = CalendarDateEngine.startOfDayMillis(controls.selectedDateMillis)
                val selectedDayItems = monthItemsByDay[selectedDayStart] ?: itemsForDay(selectedDayStart)

                val periodCount = when (controls.viewMode) {
                    CalendarViewMode.MONTHLY -> monthCells
                        .filter { it.isInCurrentMonth }
                        .sumOf { monthItemsByDay[it.dayStartMillis]?.size ?: 0 }
                    CalendarViewMode.WEEKLY -> weekItemsByDay.values.sumOf { it.size }
                    CalendarViewMode.DAILY -> selectedDayItems.size
                }

                CalendarUiState.Success(
                    viewMode = controls.viewMode,
                    selectedDateMillis = selectedDayStart,
                    selectedCategoryFilter = controls.categoryFilter,
                    monthCells = monthCells,
                    monthItemsByDay = monthItemsByDay,
                    weekDays = weekDays,
                    weekItemsByDay = weekItemsByDay,
                    selectedDayItems = selectedDayItems,
                    totalUserEventsCount = eventsResult.events.size,
                    totalVisiblePeriodItemsCount = periodCount,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CalendarUiState.Loading,
    )

    private val _editorState = MutableStateFlow(CalendarEventEditorState())
    val editorState: StateFlow<CalendarEventEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)

    fun selectViewMode(mode: CalendarViewMode) {
        filterState.update { it.copy(viewMode = mode) }
    }

    fun selectDate(dayMillis: Long) {
        filterState.update {
            it.copy(selectedDateMillis = CalendarDateEngine.startOfDayMillis(dayMillis))
        }
    }

    fun selectCategoryFilter(category: CalendarEventCategory?) {
        filterState.update { it.copy(categoryFilter = category) }
    }

    fun navigatePreviousPeriod() {
        filterState.update { state ->
            val shifted = when (state.viewMode) {
                CalendarViewMode.MONTHLY -> CalendarDateEngine.addMonths(state.selectedDateMillis, -1)
                CalendarViewMode.WEEKLY -> CalendarDateEngine.addDays(state.selectedDateMillis, -7)
                CalendarViewMode.DAILY -> CalendarDateEngine.addDays(state.selectedDateMillis, -1)
            }
            state.copy(selectedDateMillis = CalendarDateEngine.startOfDayMillis(shifted))
        }
    }

    fun navigateNextPeriod() {
        filterState.update { state ->
            val shifted = when (state.viewMode) {
                CalendarViewMode.MONTHLY -> CalendarDateEngine.addMonths(state.selectedDateMillis, 1)
                CalendarViewMode.WEEKLY -> CalendarDateEngine.addDays(state.selectedDateMillis, 7)
                CalendarViewMode.DAILY -> CalendarDateEngine.addDays(state.selectedDateMillis, 1)
            }
            state.copy(selectedDateMillis = CalendarDateEngine.startOfDayMillis(shifted))
        }
    }

    fun jumpToToday() {
        filterState.update {
            it.copy(selectedDateMillis = CalendarDateEngine.startOfDayMillis(clockMillis()))
        }
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddEventDialog(defaultCategory: CalendarEventCategory = CalendarEventCategory.UNIVERSITY_EVENTS) {
        val dayStart = filterState.value.selectedDateMillis
        val defaultStart = dayStart + 9 * 3_600_000L
        val defaultEnd = dayStart + 10 * 3_600_000L
        _editorState.value = CalendarEventEditorState(
            isOpen = true,
            draftEventId = UUID.randomUUID().toString(),
            category = defaultCategory,
            startAtMillis = defaultStart,
            endAtMillis = defaultEnd,
        )
    }

    fun openEditEventDialog(event: CalendarEvent) {
        _editorState.value = CalendarEventEditorState(
            isOpen = true,
            editingEventId = event.id,
            title = event.title,
            description = event.description,
            category = event.resolvedCategory(),
            locationOrCourse = event.locationOrCourse,
            startAtMillis = event.startAtMillis,
            endAtMillis = maxOf(event.endAtMillis, event.startAtMillis),
            allDay = event.allDay,
        )
    }

    fun dismissEditorDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = CalendarEventEditorState(isOpen = false)
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onDescriptionChanged(value: String) {
        _editorState.update { it.copy(description = value, errorMessage = null) }
    }

    fun onCategoryChanged(category: CalendarEventCategory) {
        _editorState.update { it.copy(category = category, errorMessage = null) }
    }

    fun onLocationOrCourseChanged(value: String) {
        _editorState.update { it.copy(locationOrCourse = value, errorMessage = null) }
    }

    fun onAllDayChanged(allDay: Boolean) {
        _editorState.update { state ->
            if (allDay) {
                val start = CalendarDateEngine.startOfDayMillis(state.startAtMillis)
                val end = CalendarDateEngine.endOfDayMillis(state.startAtMillis)
                state.copy(allDay = true, startAtMillis = start, endAtMillis = end, errorMessage = null)
            } else {
                state.copy(allDay = false, errorMessage = null)
            }
        }
    }

    fun onStartAtChanged(millis: Long) {
        _editorState.update { state ->
            val adjustedEnd = if (state.endAtMillis < millis) millis + 3_600_000L else state.endAtMillis
            state.copy(startAtMillis = millis, endAtMillis = adjustedEnd, errorMessage = null)
        }
    }

    fun onEndAtChanged(millis: Long) {
        _editorState.update { it.copy(endAtMillis = millis, errorMessage = null) }
    }

    fun saveEvent() {
        val form = _editorState.value
        if (form.isSaving) return

        val title = form.title.trim()
        val description = form.description.trim()
        val location = form.locationOrCourse.trim()

        val error = when {
            title.length !in 1..160 -> "Event title is required (1–160 characters)."
            description.length > 1000 -> "Description must be 1000 characters or fewer."
            location.length > 120 -> "Course or location must be 120 characters or fewer."
            form.startAtMillis <= 0L -> "Please pick a valid start date & time."
            form.endAtMillis < form.startAtMillis -> "End date & time cannot be before start time."
            else -> null
        }
        if (error != null) {
            _editorState.update { it.copy(errorMessage = error) }
            return
        }

        val request = SaveCalendarEventRequest(
            title = title,
            description = description,
            category = form.category,
            locationOrCourse = location,
            startAtMillis = form.startAtMillis,
            endAtMillis = form.endAtMillis,
            allDay = form.allDay,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingEventId
            val result = if (editingId == null) {
                repository.addCalendarEventWithId(
                    uid = uid,
                    eventId = form.draftEventId.ifBlank { UUID.randomUUID().toString() },
                    request = request,
                )
            } else {
                repository.updateCalendarEvent(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    filterState.update {
                        it.copy(selectedDateMillis = CalendarDateEngine.startOfDayMillis(form.startAtMillis))
                    }
                    _editorState.value = CalendarEventEditorState(isOpen = false)
                    _actionMessage.value = if (editingId == null) {
                        syncCoordinator.formatActionMessage("Event added to calendar.")
                    } else {
                        syncCoordinator.formatActionMessage("Calendar event updated.")
                    }
                },
                onFailure = { err ->
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Could not save event.",
                        )
                    }
                },
            )
        }
    }

    fun deleteEvent(eventId: String) {
        if (_isMutating.value || eventId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteCalendarEvent(uid, eventId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = syncCoordinator.formatActionMessage("Calendar event deleted.") },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not delete event." },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            repository: CalendarRemindersRepository,
            workspaceRepository: WorkspaceRepository? = null,
            academicModulesRepository: AcademicModulesRepository? = null,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CalendarViewModel(
                    uid = uid,
                    repository = repository,
                    workspaceRepository = workspaceRepository,
                    academicModulesRepository = academicModulesRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
