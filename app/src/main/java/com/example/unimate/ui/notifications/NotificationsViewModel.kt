package com.example.unimate.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.AppNotification
import com.example.unimate.data.model.NotificationFilter
import com.example.unimate.data.repository.CalendarRemindersRepository
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
import kotlinx.coroutines.launch

sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data object Empty : NotificationsUiState
    data class Success(
        val allNotifications: List<AppNotification>,
        val unreadNotifications: List<AppNotification>,
        val readNotifications: List<AppNotification>,
        val selectedFilter: NotificationFilter,
        val unreadCount: Int,
        val readCount: Int,
    ) : NotificationsUiState

    data class Error(val message: String) : NotificationsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val uid: String,
    private val repository: CalendarRemindersRepository,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)
    private val selectedFilter = MutableStateFlow(NotificationFilter.ALL)

    private sealed interface StreamResult {
        data object Loading : StreamResult
        data class Loaded(val notifications: List<AppNotification>) : StreamResult
        data class Failed(val message: String) : StreamResult
    }

    private val notificationsStream = retryTrigger.flatMapLatest {
        repository.observeNotifications(uid)
            .map<List<AppNotification>, StreamResult> { items ->
                StreamResult.Loaded(items.sortedByDescending { it.deliveredAtMillis })
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(StreamResult.Failed(rootError.localizedMessage ?: "Failed to load notifications."))
            }
    }

    val uiState: StateFlow<NotificationsUiState> = combine(
        notificationsStream,
        selectedFilter,
    ) { result, filter ->
        when (result) {
            StreamResult.Loading -> NotificationsUiState.Loading
            is StreamResult.Failed -> NotificationsUiState.Error(result.message)
            is StreamResult.Loaded -> {
                if (result.notifications.isEmpty()) {
                    NotificationsUiState.Empty
                } else {
                    val unread = result.notifications.filter { !it.read }
                    val read = result.notifications.filter { it.read }
                    NotificationsUiState.Success(
                        allNotifications = result.notifications,
                        unreadNotifications = unread,
                        readNotifications = read,
                        selectedFilter = filter,
                        unreadCount = unread.size,
                        readCount = read.size,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = NotificationsUiState.Loading,
    )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    fun selectFilter(filter: NotificationFilter) {
        selectedFilter.value = filter
    }

    fun retry() {
        retryTrigger.value += 1
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun markAsRead(notificationId: String) {
        if (_isMutating.value || notificationId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.markNotificationRead(uid, notificationId, true)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Marked notification as read." },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not mark as read." },
            )
        }
    }

    fun markAllAsRead() {
        val current = uiState.value as? NotificationsUiState.Success ?: return
        val unreadIds = current.unreadNotifications.map { it.id }.filter { it.isNotBlank() }
        if (_isMutating.value || unreadIds.isEmpty()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.markAllNotificationsRead(uid, unreadIds)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "All unread notifications marked as read." },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not mark all read." },
            )
        }
    }

    fun deleteNotification(notificationId: String) {
        if (_isMutating.value || notificationId.isBlank()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.deleteNotification(uid, notificationId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Notification removed." },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not remove notification." },
            )
        }
    }

    fun clearReadNotifications() {
        val current = uiState.value as? NotificationsUiState.Success ?: return
        val readIds = current.readNotifications.map { it.id }.filter { it.isNotBlank() }
        if (_isMutating.value || readIds.isEmpty()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.clearNotifications(uid, readIds)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Cleared ${readIds.size} read notification(s)." },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not clear read notifications." },
            )
        }
    }

    fun clearAllNotifications() {
        val current = uiState.value as? NotificationsUiState.Success ?: return
        val allIds = current.allNotifications.map { it.id }.filter { it.isNotBlank() }
        if (_isMutating.value || allIds.isEmpty()) return
        _isMutating.value = true
        viewModelScope.launch {
            val result = repository.clearNotifications(uid, allIds)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Cleared all notifications." },
                onFailure = { err -> _actionMessage.value = err.localizedMessage ?: "Could not clear notifications." },
            )
        }
    }

    companion object {
        fun factory(
            uid: String,
            repository: CalendarRemindersRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NotificationsViewModel(
                    uid = uid,
                    repository = repository,
                ) as T
            }
        }
    }
}
