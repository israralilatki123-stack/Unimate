package com.example.unimate.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.data.model.SaveScheduledReminderRequest
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.data.repository.CalendarRemindersRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.example.unimate.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface RemindersUiState {
    data object Loading : RemindersUiState
    data object Empty : RemindersUiState
    data class Success(
        val allReminders: List<ScheduledReminder>,
        val filteredReminders: List<ScheduledReminder>,
        val selectedCategory: ReminderCategory?,
        val activeCount: Int,
        val completedCount: Int,
    ) : RemindersUiState

    data class Error(val message: String) : RemindersUiState
}

data class ReminderEditorState(
    val isOpen: Boolean = false,
    val editingReminderId: String? = null,
    val draftReminderId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: ReminderCategory = ReminderCategory.CLASS,
    val triggerAtMillis: Long = 0L,
    val leadMinutes: Long = 15L,
    val enabled: Boolean = true,
    val completed: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val uid: String,
    private val repository: CalendarRemindersRepository,
    private val scheduler: ReminderScheduler,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val selectedCategory = MutableStateFlow<ReminderCategory?>(null)

    private sealed interface StreamResult {
        data object Loading : StreamResult
        data class Loaded(val reminders: List<ScheduledReminder>) : StreamResult
        data class Failed(val message: String) : StreamResult
    }

    private val remindersStream = retryTrigger.flatMapLatest {
        repository.observeScheduledReminders(uid)
            .map<List<ScheduledReminder>, StreamResult> { items ->
                StreamResult.Loaded(
                    items.sortedWith(
                        compareBy<ScheduledReminder> { it.completed }
                            .thenByDescending { it.enabled }
                            .thenBy { it.triggerAtMillis },
                    ),
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(StreamResult.Failed(rootError.localizedMessage ?: "Failed to load reminders."))
            }
    }

    val uiState: StateFlow<RemindersUiState> = combine(
        remindersStream,
        selectedCategory,
    ) { streamResult, categoryFilter ->
        when (streamResult) {
            StreamResult.Loading -> RemindersUiState.Loading
            is StreamResult.Failed -> RemindersUiState.Error(streamResult.message)
            is StreamResult.Loaded -> {
                if (streamResult.reminders.isEmpty()) {
                    RemindersUiState.Empty
                } else {
                    val filtered = if (categoryFilter == null) {
                        streamResult.reminders
                    } else {
                        streamResult.reminders.filter { it.resolvedCategory() == categoryFilter }
                    }
                    RemindersUiState.Success(
                        allReminders = streamResult.reminders,
                        filteredReminders = filtered,
                        selectedCategory = categoryFilter,
                        activeCount = streamResult.reminders.count { it.enabled && !it.completed },
                        completedCount = streamResult.reminders.count { it.completed },
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RemindersUiState.Loading,
    )

    private val _editorState = MutableStateFlow(ReminderEditorState())
    val editorState: StateFlow<ReminderEditorState> = _editorState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)

    fun selectCategoryFilter(category: ReminderCategory?) {
        selectedCategory.value = category
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openAddDialog(defaultCategory: ReminderCategory = ReminderCategory.CLASS) {
        val defaultTrigger = clockMillis() + 2 * 3_600_000L
        _editorState.value = ReminderEditorState(
            isOpen = true,
            draftReminderId = UUID.randomUUID().toString(),
            category = defaultCategory,
            triggerAtMillis = defaultTrigger,
            leadMinutes = 15L,
            enabled = true,
            completed = false,
        )
    }

    fun openEditDialog(reminder: ScheduledReminder) {
        _editorState.value = ReminderEditorState(
            isOpen = true,
            editingReminderId = reminder.id,
            title = reminder.title,
            notes = reminder.notes,
            category = reminder.resolvedCategory(),
            triggerAtMillis = reminder.triggerAtMillis,
            leadMinutes = reminder.leadMinutes,
            enabled = reminder.enabled,
            completed = reminder.completed,
        )
    }

    fun dismissDialog() {
        if (_editorState.value.isSaving) return
        _editorState.value = ReminderEditorState(isOpen = false)
    }

    fun onTitleChanged(value: String) {
        _editorState.update { it.copy(title = value, errorMessage = null) }
    }

    fun onNotesChanged(value: String) {
        _editorState.update { it.copy(notes = value, errorMessage = null) }
    }

    fun onCategoryChanged(category: ReminderCategory) {
        _editorState.update { it.copy(category = category, errorMessage = null) }
    }

    fun onTriggerAtChanged(millis: Long) {
        _editorState.update { it.copy(triggerAtMillis = millis, errorMessage = null) }
    }

    fun onLeadMinutesChanged(leadMinutes: Long) {
        _editorState.update { it.copy(leadMinutes = leadMinutes.coerceIn(0L, 10080L), errorMessage = null) }
    }

    fun onEnabledChanged(enabled: Boolean) {
        _editorState.update { it.copy(enabled = enabled, errorMessage = null) }
    }

    fun saveReminder() {
        val form = _editorState.value
        if (form.isSaving) return

        val title = form.title.trim()
        val notes = form.notes.trim()

        val error = when {
            title.length !in 1..160 -> "Reminder title is required (1–160 characters)."
            notes.length > 500 -> "Notes must be 500 characters or fewer."
            form.triggerAtMillis <= 0L -> "Please pick a valid reminder date and time."
            else -> null
        }
        if (error != null) {
            _editorState.update { it.copy(errorMessage = error) }
            return
        }

        val targetId = form.editingReminderId ?: form.draftReminderId.ifBlank { UUID.randomUUID().toString() }
        val reminderModel = ScheduledReminder(
            id = targetId,
            uid = uid,
            title = title,
            notes = notes,
            category = form.category.wireValue,
            triggerAtMillis = form.triggerAtMillis,
            leadMinutes = form.leadMinutes,
            enabled = form.enabled,
            completed = form.completed,
        )

        val workId = if (form.enabled && !form.completed) {
            scheduler.scheduleReminder(uid, reminderModel, clockMillis())
        } else {
            scheduler.cancelReminder(targetId)
            ""
        }

        val request = SaveScheduledReminderRequest(
            title = title,
            notes = notes,
            category = form.category,
            triggerAtMillis = form.triggerAtMillis,
            leadMinutes = form.leadMinutes,
            enabled = form.enabled,
            completed = form.completed,
            workRequestId = workId,
        )

        _editorState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val editingId = form.editingReminderId
            val result = if (editingId == null) {
                repository.addScheduledReminder(uid, targetId, request).map { }
            } else {
                repository.updateScheduledReminder(uid, editingId, request)
            }
            result.fold(
                onSuccess = {
                    _editorState.value = ReminderEditorState(isOpen = false)
                    val baseMessage = when {
                        !form.enabled -> "Reminder saved (scheduling paused)."
                        editingId == null -> "Reminder saved and scheduled locally with WorkManager."
                        else -> "Reminder updated and rescheduled."
                    }
                    _actionMessage.value = syncCoordinator.formatActionMessage(baseMessage)
                },
                onFailure = { err ->
                    if (editingId == null) {
                        scheduler.cancelReminder(targetId)
                    }
                    _editorState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Could not save reminder.",
                        )
                    }
                },
            )
        }
    }

    fun toggleEnabled(reminder: ScheduledReminder) {
        if (_isMutating.value || reminder.id.isBlank()) return
        val nextEnabled = !reminder.enabled
        val updatedModel = reminder.copy(enabled = nextEnabled)
        val nextWorkId = if (nextEnabled && !reminder.completed) {
            scheduler.scheduleReminder(uid, updatedModel, clockMillis())
        } else {
            scheduler.cancelReminder(reminder.id)
            ""
        }
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateScheduledReminderState(
                uid = uid,
                reminderId = reminder.id,
                enabled = nextEnabled,
                completed = reminder.completed,
                workRequestId = nextWorkId,
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        if (nextEnabled) "Reminder scheduled." else "Reminder paused.",
                    )
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update reminder."
                },
            )
        }
    }

    fun toggleCompleted(reminder: ScheduledReminder) {
        if (_isMutating.value || reminder.id.isBlank()) return
        val nextCompleted = !reminder.completed
        val nextEnabled = if (nextCompleted) false else reminder.enabled
        val nextWorkId = if (nextEnabled && !nextCompleted) {
            scheduler.scheduleReminder(uid, reminder.copy(completed = false, enabled = nextEnabled), clockMillis())
        } else {
            scheduler.cancelReminder(reminder.id)
            ""
        }
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.updateScheduledReminderState(
                uid = uid,
                reminderId = reminder.id,
                enabled = nextEnabled,
                completed = nextCompleted,
                workRequestId = nextWorkId,
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = syncCoordinator.formatActionMessage(
                        if (nextCompleted) "Reminder marked completed." else "Reminder reopened.",
                    )
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Failed to update reminder."
                },
            )
        }
    }

    fun deleteReminder(reminderId: String) {
        if (_isMutating.value || reminderId.isBlank()) return
        scheduler.cancelReminder(reminderId)
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteScheduledReminder(uid, reminderId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = syncCoordinator.formatActionMessage("Reminder deleted.") },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Failed to delete reminder." },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            repository: CalendarRemindersRepository,
            scheduler: ReminderScheduler,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RemindersViewModel(
                    uid = uid,
                    repository = repository,
                    scheduler = scheduler,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
