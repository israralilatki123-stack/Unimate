package com.example.unimate

import com.example.unimate.data.repository.AuthSessionStatus
import com.example.unimate.ui.onboarding.OnboardingDestination
import com.example.unimate.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

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
    fun `completing onboarding persists completion and routes unauthenticated user to LOGIN`() = runTest {
        val onboardingRepo = FakeOnboardingPreferencesRepository(initialCompleted = false)
        val viewModel = OnboardingViewModel(
            onboardingPreferencesRepository = onboardingRepo,
            authStatusRepository = FakeAuthStatusRepository(AuthSessionStatus.Unauthenticated),
        )

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertTrue(onboardingRepo.hasCompletedOnboarding())
        assertEquals(1, onboardingRepo.setCompletedCallCount)
        assertEquals(OnboardingDestination.LOGIN, viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun `completing onboarding routes verified user to DASHBOARD`() = runTest {
        val onboardingRepo = FakeOnboardingPreferencesRepository(initialCompleted = false)
        val viewModel = OnboardingViewModel(
            onboardingPreferencesRepository = onboardingRepo,
            authStatusRepository = FakeAuthStatusRepository(
                AuthSessionStatus.AuthenticatedAndVerified(uid = "uid-1", email = "user@uni.edu"),
            ),
        )

        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertTrue(onboardingRepo.hasCompletedOnboarding())
        assertEquals(OnboardingDestination.DASHBOARD, viewModel.uiState.value.pendingDestination)
    }

    @Test
    fun `duplicate completeOnboarding calls only execute once`() = runTest {
        val onboardingRepo = FakeOnboardingPreferencesRepository(initialCompleted = false)
        val viewModel = OnboardingViewModel(
            onboardingPreferencesRepository = onboardingRepo,
            authStatusRepository = FakeAuthStatusRepository(AuthSessionStatus.Unauthenticated),
        )

        viewModel.completeOnboarding()
        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertEquals(1, onboardingRepo.setCompletedCallCount)

        viewModel.onNavigationConsumed()
        assertNull(viewModel.uiState.value.pendingDestination)

        viewModel.completeOnboarding()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingDestination)
    }
}
