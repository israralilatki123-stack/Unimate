package com.example.unimate

import android.content.Context
import com.example.unimate.data.model.ProfileSetupRequest
import com.example.unimate.data.model.ProfileUpdateRequest
import com.example.unimate.data.model.UserProfile
import com.example.unimate.data.model.UserProfileMapper
import com.example.unimate.data.repository.AuthRepository
import com.example.unimate.data.repository.AuthenticatedUser
import com.example.unimate.data.repository.GoogleSignInResult
import com.example.unimate.data.repository.PhoneCodeSendResult
import com.example.unimate.data.repository.PhoneVerifyResult
import com.example.unimate.data.repository.SessionPreferences
import com.example.unimate.data.repository.SessionPreferencesRepository
import com.example.unimate.data.repository.UserProfileRepository
import com.example.unimate.ui.auth.AuthValidation
import com.example.unimate.ui.auth.LoginNavigationTarget
import com.example.unimate.ui.auth.LoginViewModel
import com.example.unimate.ui.dashboard.ProfileDashboardViewModel
import com.example.unimate.ui.dashboard.ProfileUiState
import com.google.firebase.auth.PhoneAuthProvider
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

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
    fun `auth validation enforces firestore rule bounds including phone and otp`() {
        assertNotNull(AuthValidation.validateFullName("A"))
        assertNotNull(AuthValidation.validateFullName("A".repeat(101)))
        assertNull(AuthValidation.validateFullName("Israr Ali"))

        assertNotNull(AuthValidation.validateEmail("bad-email"))
        assertNull(AuthValidation.validateEmail("student@gmail.com"))

        assertNotNull(AuthValidation.validatePhoneNumber("03001234567"))
        assertNull(AuthValidation.validatePhoneNumber("+92 300-1234567"))
        assertEquals("+923001234567", AuthValidation.normalizePhoneNumber("+92 300-1234567"))

        assertNotNull(AuthValidation.validateOtpCode("123"))
        assertNull(AuthValidation.validateOtpCode("123456"))

        assertNotNull(AuthValidation.validateUniversity("X"))
        assertNull(AuthValidation.validateUniversity("Sukkur IBA University"))

        assertNotNull(AuthValidation.validateDepartment("C"))
        assertNull(AuthValidation.validateDepartment("Computer Science"))

        assertNotNull(AuthValidation.validateSemester(""))
        assertNull(AuthValidation.validateSemester("5th Semester"))

        assertNull(AuthValidation.validateProfileImageUrl(""))
        assertNotNull(AuthValidation.validateProfileImageUrl("ftp://invalid-scheme.png"))
        assertNull(AuthValidation.validateProfileImageUrl("https://example.com/avatar.png"))
    }

    @Test
    fun `user profile mapper omits document id and supports phoneNumber fields`() {
        val createMap = UserProfileMapper.toCreatePayload(
            uid = "uid-1",
            email = "aisha@gmail.com",
            request = ProfileSetupRequest(
                fullName = "  Aisha Khan  ",
                university = "  Sukkur IBA University ",
                department = " Computer Science ",
                semester = "5th Semester",
                phoneNumber = "+923001234567",
                profileImage = null,
            ),
        )
        assertFalse(createMap.containsKey("id"))
        assertFalse(createMap.containsKey("password"))
        assertFalse(createMap.containsKey("profileImage"))
        assertEquals("+923001234567", createMap["phoneNumber"])
        assertEquals("Aisha Khan", createMap["fullName"])
        assertTrue(createMap["createdAt"] is FieldValue)
        assertTrue(createMap["updatedAt"] is FieldValue)

        val phoneUpdateMap = UserProfileMapper.toPhoneUpdatePayload(" +923009998877 ")
        assertEquals("+923009998877", phoneUpdateMap["phoneNumber"])
        assertTrue(phoneUpdateMap["updatedAt"] is FieldValue)

        val updateMap = UserProfileMapper.toUpdatePayload(
            ProfileUpdateRequest(
                fullName = "Aisha K.",
                university = "Sukkur IBA",
                department = "Software Engineering",
                semester = "6th Semester",
                profileImage = "https://example.com/p.png",
            ),
        )
        assertFalse(updateMap.containsKey("uid"))
        assertFalse(updateMap.containsKey("email"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertEquals("https://example.com/p.png", updateMap["profileImage"])
        assertTrue(updateMap["updatedAt"] is FieldValue)
    }

    @Test
    fun `login silent auto sign-in runs when remember session is true`() = runTest {
        val user = AuthenticatedUser("uid-1", "student@gmail.com", "Student One", null)
        val authRepo = FakeAuthRepository(autoSignInResult = GoogleSignInResult.Success(user))
        val sessionRepo = FakeSessionPreferencesRepository(rememberSession = true)

        val viewModel = LoginViewModel(authRepo, sessionRepo)
        advanceUntilIdle()

        assertEquals(1, authRepo.autoSignInCalls)
        assertEquals(LoginNavigationTarget.Dashboard, viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `login skips silent auto sign-in when remember session is false`() = runTest {
        val authRepo = FakeAuthRepository()
        val sessionRepo = FakeSessionPreferencesRepository(rememberSession = false)

        val viewModel = LoginViewModel(authRepo, sessionRepo)
        advanceUntilIdle()

        assertEquals(0, authRepo.autoSignInCalls)
        assertNull(viewModel.uiState.value.pendingNavigation)
    }

    @Test
    fun `phone-signed-in user must enter valid email during profile setup`() = runTest {
        val phoneUser = AuthenticatedUser(
            uid = "uid-phone",
            email = "",
            displayName = "",
            photoUrl = null,
            phoneNumber = "+923001234567",
            providerIds = listOf("phone"),
        )
        val profileRepo = FakeUserProfileRepository(initialProfile = null)
        val viewModel = ProfileDashboardViewModel(
            uid = "uid-phone",
            authUser = phoneUser,
            userProfileRepository = profileRepo,
            authRepository = FakeAuthRepository(),
        )

        val collectJob = launch { viewModel.profileUiState.collect {} }
        advanceUntilIdle()

        viewModel.onFullNameChanged("Israr Ali")
        viewModel.onUniversityChanged("Sukkur IBA University")
        viewModel.onDepartmentChanged("Computer Science")
        viewModel.onSemesterSelected("5th Semester")
        viewModel.submitInitialProfile()
        advanceUntilIdle()

        assertEquals(0, profileRepo.createCalls)
        assertNotNull(viewModel.formState.value.emailErrorRes)

        viewModel.onEmailChanged("israr@gmail.com")
        viewModel.submitInitialProfile()
        advanceUntilIdle()

        assertEquals(1, profileRepo.createCalls)
        assertTrue(viewModel.profileUiState.value is ProfileUiState.Ready)
        collectJob.cancel()
    }

    @Test
    fun `profile dashboard emits Error state when profile stream fails`() = runTest {
        val authUser = AuthenticatedUser("uid-1", "aisha@gmail.com", "Aisha Khan", null)
        val failingRepo = object : UserProfileRepository {
            override fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
                throw IllegalStateException("Firestore profile unavailable")
            }

            override suspend fun createUserProfile(request: ProfileSetupRequest): Result<Unit> =
                Result.success(Unit)

            override suspend fun updateUserProfile(uid: String, request: ProfileUpdateRequest): Result<Unit> =
                Result.success(Unit)

            override suspend fun updatePhoneNumber(uid: String, phoneNumber: String): Result<Unit> =
                Result.success(Unit)
        }

        val viewModel = ProfileDashboardViewModel(
            uid = "uid-1",
            authUser = authUser,
            userProfileRepository = failingRepo,
            authRepository = FakeAuthRepository(),
        )

        val collectJob = launch { viewModel.profileUiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.profileUiState.value
        assertTrue(state is ProfileUiState.Error)
        assertEquals("Firestore profile unavailable", (state as ProfileUiState.Error).message)
        collectJob.cancel()
    }
}

private class FakeSessionPreferencesRepository(
    rememberSession: Boolean = true,
) : SessionPreferencesRepository {
    var current = SessionPreferences(rememberSession = rememberSession, lastEmail = "")
    private val flow = MutableStateFlow(current)

    override val sessionPreferencesFlow: Flow<SessionPreferences> = flow

    override suspend fun getSessionPreferences(): SessionPreferences = current

    override suspend fun saveRememberSession(remember: Boolean, email: String) {
        current = SessionPreferences(
            rememberSession = remember,
            lastEmail = if (remember) email.trim() else "",
        )
        flow.value = current
    }
}

private class FakeAuthRepository(
    private val autoSignInResult: GoogleSignInResult = GoogleSignInResult.NoSilentCredential,
) : AuthRepository {
    var autoSignInCalls = 0
    var signOutCalls = 0
    private val userFlow = MutableStateFlow<AuthenticatedUser?>(null)

    override val authStateFlow: Flow<AuthenticatedUser?> = userFlow

    override fun currentAuthenticatedUser(): AuthenticatedUser? = userFlow.value

    override suspend fun attemptAutoSignIn(): GoogleSignInResult {
        autoSignInCalls++
        return autoSignInResult
    }

    override suspend fun signInWithGoogle(activityContext: Context): GoogleSignInResult {
        return GoogleSignInResult.Cancelled
    }

    override suspend fun sendPhoneVerificationCode(
        activityContext: Context,
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        linkToCurrentUser: Boolean,
    ): PhoneCodeSendResult {
        return PhoneCodeSendResult.CodeSent("ver-123", null)
    }

    override suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
        linkToCurrentUser: Boolean,
    ): PhoneVerifyResult {
        val user = AuthenticatedUser(
            uid = "uid-phone",
            email = "aisha@gmail.com",
            displayName = "Aisha Khan",
            photoUrl = null,
            phoneNumber = "+923001234567",
            providerIds = listOf("phone"),
        )
        return PhoneVerifyResult.Success(user)
    }

    override suspend fun signOut() {
        signOutCalls++
        userFlow.value = null
    }
}

private class FakeUserProfileRepository(
    initialProfile: UserProfile?,
) : UserProfileRepository {
    private val profileFlow = MutableStateFlow(initialProfile)
    var createCalls = 0
    var updateCalls = 0

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = profileFlow

    override suspend fun createUserProfile(request: ProfileSetupRequest): Result<Unit> {
        createCalls++
        profileFlow.value = UserProfile(
            id = "uid-1",
            uid = "uid-1",
            fullName = request.fullName,
            email = request.email ?: "aisha@gmail.com",
            university = request.university,
            department = request.department,
            semester = request.semester,
            phoneNumber = request.phoneNumber.orEmpty(),
            profileImage = request.profileImage.orEmpty(),
        )
        return Result.success(Unit)
    }

    override suspend fun updateUserProfile(uid: String, request: ProfileUpdateRequest): Result<Unit> {
        updateCalls++
        profileFlow.value = profileFlow.value?.copy(
            fullName = request.fullName,
            university = request.university,
            department = request.department,
            semester = request.semester,
            profileImage = request.profileImage.orEmpty(),
        )
        return Result.success(Unit)
    }

    override suspend fun updatePhoneNumber(uid: String, phoneNumber: String): Result<Unit> {
        profileFlow.value = profileFlow.value?.copy(phoneNumber = phoneNumber)
        return Result.success(Unit)
    }
}
