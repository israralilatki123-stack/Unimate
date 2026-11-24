package com.example.unimate

import com.example.unimate.data.repository.AuthSessionStatus
import com.example.unimate.data.repository.AuthStatusRepository
import com.example.unimate.data.repository.OnboardingPreferencesRepository
import com.example.unimate.ui.splash.SplashDestination
import com.example.unimate.ui.splash.SplashViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new user who has not completed onboarding routes to ONBOARDING`() = runTest {
        val viewModel = SplashViewModel(
            onboardingPreferencesRepository = FakeOnboardingPreferencesRepository(initialCompleted = false),
            authStatusRepository = FakeAuthStatusRepository(AuthSessionStatus.Unauthenticated),
            minSplashDurationMs = 0L,
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(SplashDestination.ONBOARDING, viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun `returning unauthenticated user routes to LOGIN`() = runTest {
        val viewModel = SplashViewModel(
            onboardingPreferencesRepository = FakeOnboardingPreferencesRepository(initialCompleted = true),
            authStatusRepository = FakeAuthStatusRepository(AuthSessionStatus.Unauthenticated),
            minSplashDurationMs = 0L,
        )

        advanceUntilIdle()

        assertEquals(SplashDestination.LOGIN, viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun `returning authenticated and verified user routes to DASHBOARD`() = runTest {
        val viewModel = SplashViewModel(
            onboardingPreferencesRepository = FakeOnboardingPreferencesRepository(initialCompleted = true),
            authStatusRepository = FakeAuthStatusRepository(
                AuthSessionStatus.AuthenticatedAndVerified(uid = "u123", email = "student@iba-suk.edu.pk"),
            ),
            minSplashDurationMs = 0L,
        )

        advanceUntilIdle()

        assertEquals(SplashDestination.DASHBOARD, viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun `consuming navigation clears pending destination and prevents duplicate emission`() = runTest {
        val viewModel = SplashViewModel(
            onboardingPreferencesRepository = FakeOnboardingPreferencesRepository(initialCompleted = false),
            authStatusRepository = FakeAuthStatusRepository(AuthSessionStatus.Unauthenticated),
            minSplashDurationMs = 0L,
        )

        advanceUntilIdle()
        assertEquals(SplashDestination.ONBOARDING, viewModel.uiState.value.pendingDestination)

        viewModel.onNavigationConsumed()
        assertNull(viewModel.uiState.value.pendingDestination)

        viewModel.resolveInitialDestination()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingDestination)
    }
}

internal class FakeOnboardingPreferencesRepository(
    initialCompleted: Boolean,
) : OnboardingPreferencesRepository {
    private val state = MutableStateFlow(initialCompleted)
    var setCompletedCallCount = 0
        private set

    override val hasCompletedOnboardingFlow: Flow<Boolean> = state

    override suspend fun hasCompletedOnboarding(): Boolean = state.value

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        setCompletedCallCount++
        state.value = completed
    }
}

internal class FakeAuthStatusRepository(
    private val status: AuthSessionStatus,
) : AuthStatusRepository {
    override suspend fun checkAuthStatus(): AuthSessionStatus = status
}
