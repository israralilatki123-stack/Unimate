package com.example.unimate.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.repository.AuthSessionStatus
import com.example.unimate.data.repository.AuthStatusRepository
import com.example.unimate.data.repository.OnboardingPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingDestination {
    LOGIN,
    DASHBOARD,
}

data class OnboardingUiState(
    val isSubmitting: Boolean = false,
    val pendingDestination: OnboardingDestination? = null,
)

class OnboardingViewModel(
    private val onboardingPreferencesRepository: OnboardingPreferencesRepository,
    private val authStatusRepository: AuthStatusRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var hasTriggeredNavigation = false

    fun completeOnboarding() {
        if (hasTriggeredNavigation || _uiState.value.isSubmitting) return
        hasTriggeredNavigation = true

        _uiState.update { it.copy(isSubmitting = true) }

        viewModelScope.launch {
            runCatching {
                onboardingPreferencesRepository.setOnboardingCompleted(true)
            }

            val destination = when (authStatusRepository.checkAuthStatus()) {
                is AuthSessionStatus.AuthenticatedAndVerified -> OnboardingDestination.DASHBOARD
                AuthSessionStatus.Unauthenticated -> OnboardingDestination.LOGIN
            }

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    pendingDestination = destination,
                )
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(pendingDestination = null) }
    }

    companion object {
        fun factory(
            onboardingPreferencesRepository: OnboardingPreferencesRepository,
            authStatusRepository: AuthStatusRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(
                    onboardingPreferencesRepository = onboardingPreferencesRepository,
                    authStatusRepository = authStatusRepository,
                ) as T
            }
        }
    }
}
