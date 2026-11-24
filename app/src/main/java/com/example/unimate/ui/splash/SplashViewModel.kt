package com.example.unimate.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.repository.AuthSessionStatus
import com.example.unimate.data.repository.AuthStatusRepository
import com.example.unimate.data.repository.OnboardingPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SplashDestination {
    ONBOARDING,
    LOGIN,
    DASHBOARD,
}

data class SplashUiState(
    val isLoading: Boolean = true,
    val pendingDestination: SplashDestination? = null,
)

class SplashViewModel(
    private val onboardingPreferencesRepository: OnboardingPreferencesRepository,
    private val authStatusRepository: AuthStatusRepository,
    private val minSplashDurationMs: Long = DEFAULT_MIN_SPLASH_DURATION_MS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private var hasStartedResolution = false

    init {
        resolveInitialDestination()
    }

    fun resolveInitialDestination() {
        if (hasStartedResolution) return
        hasStartedResolution = true

        viewModelScope.launch {
            val delayDeferred = async {
                if (minSplashDurationMs > 0L) {
                    delay(minSplashDurationMs)
                }
            }
            val destinationDeferred = async {
                val hasCompletedOnboarding = runCatching {
                    onboardingPreferencesRepository.hasCompletedOnboarding()
                }.getOrDefault(false)

                if (!hasCompletedOnboarding) {
                    SplashDestination.ONBOARDING
                } else {
                    when (authStatusRepository.checkAuthStatus()) {
                        is AuthSessionStatus.AuthenticatedAndVerified -> SplashDestination.DASHBOARD
                        AuthSessionStatus.Unauthenticated -> SplashDestination.LOGIN
                    }
                }
            }

            delayDeferred.await()
            val destination = destinationDeferred.await()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    pendingDestination = destination,
                )
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(pendingDestination = null) }
    }

    companion object {
        const val DEFAULT_MIN_SPLASH_DURATION_MS = 1150L

        fun factory(
            onboardingPreferencesRepository: OnboardingPreferencesRepository,
            authStatusRepository: AuthStatusRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SplashViewModel(
                    onboardingPreferencesRepository = onboardingPreferencesRepository,
                    authStatusRepository = authStatusRepository,
                ) as T
            }
        }
    }
}
