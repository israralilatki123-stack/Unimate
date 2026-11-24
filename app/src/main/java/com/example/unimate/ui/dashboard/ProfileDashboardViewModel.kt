package com.example.unimate.ui.dashboard

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.model.ProfileSetupRequest
import com.example.unimate.data.model.ProfileUpdateRequest
import com.example.unimate.data.model.UserProfile
import com.example.unimate.data.repository.AuthRepository
import com.example.unimate.data.repository.AuthenticatedUser
import com.example.unimate.data.repository.PhoneCodeSendResult
import com.example.unimate.data.repository.PhoneVerifyResult
import com.example.unimate.data.repository.UserProfileRepository
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.OfflineSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.example.unimate.ui.auth.AuthValidation
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data object NeedsProfileSetup : ProfileUiState
    data class Ready(val profile: UserProfile) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

data class ProfileFormState(
    val fullName: String = "",
    val email: String = "",
    val isEmailLocked: Boolean = true,
    val phoneNumber: String = "",
    val university: String = "",
    val department: String = "",
    val semester: String = "",
    val profileImage: String = "",
    @StringRes val fullNameErrorRes: Int? = null,
    @StringRes val emailErrorRes: Int? = null,
    @StringRes val universityErrorRes: Int? = null,
    @StringRes val departmentErrorRes: Int? = null,
    @StringRes val semesterErrorRes: Int? = null,
    @StringRes val profileImageErrorRes: Int? = null,
    val isSubmitting: Boolean = false,
    val submitErrorMessage: String? = null,
    val statusBannerMessage: String? = null,
    val isEditDialogOpen: Boolean = false,
    val isSigningOut: Boolean = false,
    val showUnsyncedSignOutWarning: Boolean = false,
    val unsyncedPendingCount: Int = 0,
    val unsyncedConflictCount: Int = 0,
    val isSyncingBeforeSignOut: Boolean = false,
    val signOutSyncError: String? = null,
)

data class PhoneLinkUiState(
    val isDialogOpen: Boolean = false,
    val phoneNumber: String = "",
    val otpCode: String = "",
    val verificationId: String? = null,
    val resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    val resendCountdownSeconds: Int = 0,
    val isSendingOtp: Boolean = false,
    val isVerifyingOtp: Boolean = false,
    @StringRes val phoneErrorRes: Int? = null,
    @StringRes val otpErrorRes: Int? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val isBusy: Boolean get() = isSendingOtp || isVerifyingOtp
}

class ProfileDashboardViewModel(
    private val uid: String,
    val authUser: AuthenticatedUser,
    private val userProfileRepository: UserProfileRepository,
    private val authRepository: AuthRepository,
    private val syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) : ViewModel() {

    val profileUiState: StateFlow<ProfileUiState> = userProfileRepository.observeUserProfile(uid)
        .map<UserProfile?, ProfileUiState> { profile ->
            if (profile == null) ProfileUiState.NeedsProfileSetup else ProfileUiState.Ready(profile)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(ProfileUiState.Error(rootError.localizedMessage ?: "Failed to load student profile."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProfileUiState.Loading,
        )

    private val _formState = MutableStateFlow(
        ProfileFormState(
            fullName = authUser.displayName,
            email = authUser.email,
            isEmailLocked = authUser.email.isNotBlank(),
            phoneNumber = authUser.phoneNumber.orEmpty(),
            profileImage = authUser.photoUrl.orEmpty(),
        ),
    )
    val formState: StateFlow<ProfileFormState> = _formState.asStateFlow()

    private val _phoneLinkState = MutableStateFlow(PhoneLinkUiState())
    val phoneLinkState: StateFlow<PhoneLinkUiState> = _phoneLinkState.asStateFlow()

    private var linkResendTimerJob: Job? = null

    init {
        viewModelScope.launch {
            syncCoordinator.syncState.collectLatest { sync ->
                val effectivePending = sync.pendingCount +
                    if (sync.hasSdkPendingWrites && sync.pendingCount == 0) 1 else 0
                _formState.update { state ->
                    state.copy(
                        unsyncedPendingCount = effectivePending,
                        unsyncedConflictCount = sync.conflictCount,
                    )
                }
            }
        }
    }

    fun onFullNameChanged(value: String) {
        _formState.update {
            it.copy(fullName = value, fullNameErrorRes = null, submitErrorMessage = null)
        }
    }

    fun onEmailChanged(value: String) {
        if (_formState.value.isEmailLocked) return
        _formState.update {
            it.copy(email = value, emailErrorRes = null, submitErrorMessage = null)
        }
    }

    fun onUniversityChanged(value: String) {
        _formState.update {
            it.copy(university = value, universityErrorRes = null, submitErrorMessage = null)
        }
    }

    fun onDepartmentChanged(value: String) {
        _formState.update {
            it.copy(department = value, departmentErrorRes = null, submitErrorMessage = null)
        }
    }

    fun onSemesterSelected(value: String) {
        _formState.update {
            it.copy(semester = value, semesterErrorRes = null, submitErrorMessage = null)
        }
    }

    fun onProfileImageChanged(value: String) {
        _formState.update {
            it.copy(profileImage = value, profileImageErrorRes = null, submitErrorMessage = null)
        }
    }

    fun openEditDialog(profile: UserProfile) {
        _formState.update {
            it.copy(
                fullName = profile.fullName,
                email = profile.email,
                isEmailLocked = true,
                phoneNumber = profile.phoneNumber,
                university = profile.university,
                department = profile.department,
                semester = profile.semester,
                profileImage = profile.profileImage,
                fullNameErrorRes = null,
                emailErrorRes = null,
                universityErrorRes = null,
                departmentErrorRes = null,
                semesterErrorRes = null,
                profileImageErrorRes = null,
                submitErrorMessage = null,
                isEditDialogOpen = true,
            )
        }
    }

    fun dismissEditDialog() {
        _formState.update {
            it.copy(
                isEditDialogOpen = false,
                submitErrorMessage = null,
            )
        }
    }

    fun dismissStatusBanner() {
        _formState.update { it.copy(statusBannerMessage = null) }
    }

    fun submitInitialProfile() {
        val current = _formState.value
        if (current.isSubmitting || !validateForm(current, validateEmail = !current.isEmailLocked)) return

        _formState.update { it.copy(isSubmitting = true, submitErrorMessage = null) }

        viewModelScope.launch {
            val result = userProfileRepository.createUserProfile(
                ProfileSetupRequest(
                    fullName = current.fullName.trim(),
                    email = current.email.trim(),
                    university = current.university.trim(),
                    department = current.department.trim(),
                    semester = current.semester.trim(),
                    phoneNumber = current.phoneNumber.trim().takeIf { it.isNotEmpty() },
                    profileImage = current.profileImage.trim().takeIf { it.isNotEmpty() },
                ),
            )
            result.fold(
                onSuccess = {
                    _formState.update { it.copy(isSubmitting = false, submitErrorMessage = null) }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            submitErrorMessage = error.localizedMessage
                                ?: "Could not save your student profile.",
                        )
                    }
                },
            )
        }
    }

    fun submitProfileUpdate(successMessage: String) {
        val current = _formState.value
        if (current.isSubmitting || !validateForm(current, validateEmail = false)) return

        _formState.update { it.copy(isSubmitting = true, submitErrorMessage = null) }

        viewModelScope.launch {
            val result = userProfileRepository.updateUserProfile(
                uid = uid,
                request = ProfileUpdateRequest(
                    fullName = current.fullName.trim(),
                    university = current.university.trim(),
                    department = current.department.trim(),
                    semester = current.semester.trim(),
                    profileImage = current.profileImage.trim().takeIf { it.isNotEmpty() },
                ),
            )
            result.fold(
                onSuccess = {
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            isEditDialogOpen = false,
                            submitErrorMessage = null,
                            statusBannerMessage = syncCoordinator.formatActionMessage(successMessage),
                        )
                    }
                },
                onFailure = { error ->
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            submitErrorMessage = error.localizedMessage
                                ?: "Could not update your student profile.",
                        )
                    }
                },
            )
        }
    }

    fun openLinkPhoneDialog() {
        linkResendTimerJob?.cancel()
        _phoneLinkState.value = PhoneLinkUiState(isDialogOpen = true)
    }

    fun dismissLinkPhoneDialog() {
        if (_phoneLinkState.value.isBusy) return
        linkResendTimerJob?.cancel()
        _phoneLinkState.value = PhoneLinkUiState(isDialogOpen = false)
    }

    fun onLinkPhoneChanged(value: String) {
        _phoneLinkState.update {
            it.copy(phoneNumber = value, phoneErrorRes = null, errorMessage = null)
        }
    }

    fun onLinkOtpChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _phoneLinkState.update {
            it.copy(otpCode = digitsOnly, otpErrorRes = null, errorMessage = null)
        }
    }

    fun sendLinkPhoneOtp(
        activityContext: Context,
        successMessage: String,
        isResend: Boolean = false,
    ) {
        val current = _phoneLinkState.value
        if (current.isBusy) return
        if (isResend && current.resendCountdownSeconds > 0) return

        val phoneErr = AuthValidation.validatePhoneNumber(current.phoneNumber)
        if (phoneErr != null) {
            _phoneLinkState.update { it.copy(phoneErrorRes = phoneErr) }
            return
        }

        val normalizedPhone = AuthValidation.normalizePhoneNumber(current.phoneNumber)
        _phoneLinkState.update {
            it.copy(
                phoneNumber = normalizedPhone,
                isSendingOtp = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (
                val result = authRepository.sendPhoneVerificationCode(
                    activityContext = activityContext,
                    phoneNumber = normalizedPhone,
                    resendToken = if (isResend) current.resendToken else null,
                    linkToCurrentUser = true,
                )
            ) {
                is PhoneCodeSendResult.CodeSent -> {
                    _phoneLinkState.update {
                        it.copy(
                            isSendingOtp = false,
                            verificationId = result.verificationId,
                            resendToken = result.resendToken,
                            infoMessage = "Verification code sent to $normalizedPhone.",
                        )
                    }
                    startLinkResendCountdown()
                }
                is PhoneCodeSendResult.AutoVerified -> {
                    val linkedPhone = result.user.phoneNumber ?: normalizedPhone
                    val saveResult = userProfileRepository.updatePhoneNumber(uid, linkedPhone)
                    saveResult.fold(
                        onSuccess = {
                            linkResendTimerJob?.cancel()
                            _phoneLinkState.value = PhoneLinkUiState(isDialogOpen = false)
                            _formState.update {
                                it.copy(
                                    phoneNumber = linkedPhone,
                                    statusBannerMessage = syncCoordinator.formatActionMessage(successMessage),
                                )
                            }
                        },
                        onFailure = { err ->
                            _phoneLinkState.update {
                                it.copy(
                                    isSendingOtp = false,
                                    errorMessage = err.localizedMessage ?: "Verified phone, but Firestore update failed.",
                                )
                            }
                        },
                    )
                }
                is PhoneCodeSendResult.Error -> {
                    _phoneLinkState.update {
                        it.copy(isSendingOtp = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun verifyLinkPhoneOtp(successMessage: String) {
        val current = _phoneLinkState.value
        if (current.isBusy) return
        val verId = current.verificationId ?: return

        val otpErr = AuthValidation.validateOtpCode(current.otpCode)
        if (otpErr != null) {
            _phoneLinkState.update { it.copy(otpErrorRes = otpErr) }
            return
        }

        _phoneLinkState.update { it.copy(isVerifyingOtp = true, errorMessage = null) }

        viewModelScope.launch {
            when (
                val verifyResult = authRepository.verifyPhoneOtp(
                    verificationId = verId,
                    smsCode = current.otpCode,
                    linkToCurrentUser = true,
                )
            ) {
                is PhoneVerifyResult.Success -> {
                    val linkedPhone = verifyResult.user.phoneNumber ?: current.phoneNumber
                    val saveResult = userProfileRepository.updatePhoneNumber(uid, linkedPhone)
                    saveResult.fold(
                        onSuccess = {
                            linkResendTimerJob?.cancel()
                            _phoneLinkState.value = PhoneLinkUiState(isDialogOpen = false)
                            _formState.update {
                                it.copy(
                                    phoneNumber = linkedPhone,
                                    statusBannerMessage = syncCoordinator.formatActionMessage(successMessage),
                                )
                            }
                        },
                        onFailure = { err ->
                            _phoneLinkState.update {
                                it.copy(
                                    isVerifyingOtp = false,
                                    errorMessage = err.localizedMessage ?: "Verified phone, but Firestore sync failed.",
                                )
                            }
                        },
                    )
                }
                is PhoneVerifyResult.Error -> {
                    _phoneLinkState.update {
                        it.copy(isVerifyingOtp = false, errorMessage = verifyResult.message)
                    }
                }
            }
        }
    }

    private fun startLinkResendCountdown() {
        linkResendTimerJob?.cancel()
        linkResendTimerJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 0) {
                _phoneLinkState.update { it.copy(resendCountdownSeconds = seconds) }
                if (seconds > 0) delay(1_000L)
            }
        }
    }

    fun requestSignOut() {
        val sync = syncCoordinator.syncState.value
        if (!sync.hasUnsynchronizedData) {
            signOut()
            return
        }
        val effectivePending = sync.pendingCount +
            if (sync.hasSdkPendingWrites && sync.pendingCount == 0) 1 else 0
        _formState.update {
            it.copy(
                showUnsyncedSignOutWarning = true,
                unsyncedPendingCount = effectivePending,
                unsyncedConflictCount = sync.conflictCount,
                isSyncingBeforeSignOut = false,
                signOutSyncError = null,
            )
        }
    }

    fun dismissSignOutWarning() {
        if (_formState.value.isSyncingBeforeSignOut) return
        _formState.update {
            it.copy(
                showUnsyncedSignOutWarning = false,
                signOutSyncError = null,
            )
        }
    }

    fun syncAndSignOut() {
        if (_formState.value.isSyncingBeforeSignOut || _formState.value.isSigningOut) return
        if (!syncCoordinator.isOnline) {
            _formState.update {
                it.copy(
                    signOutSyncError = "You are offline. Reconnect to the internet to upload pending changes before signing out.",
                )
            }
            return
        }
        _formState.update {
            it.copy(isSyncingBeforeSignOut = true, signOutSyncError = null)
        }
        viewModelScope.launch {
            val offlineCoordinator = syncCoordinator as? OfflineSyncCoordinator
            if (offlineCoordinator != null) {
                offlineCoordinator.syncNowAwait(triggerBanner = false)
            } else {
                syncCoordinator.syncNow()
            }
            val latest = syncCoordinator.syncState.value
            if (!latest.hasUnsynchronizedData) {
                _formState.update {
                    it.copy(
                        isSyncingBeforeSignOut = false,
                        showUnsyncedSignOutWarning = false,
                        signOutSyncError = null,
                    )
                }
                signOut()
            } else {
                val effectivePending = latest.pendingCount +
                    if (latest.hasSdkPendingWrites && latest.pendingCount == 0) 1 else 0
                _formState.update {
                    it.copy(
                        isSyncingBeforeSignOut = false,
                        unsyncedPendingCount = effectivePending,
                        unsyncedConflictCount = latest.conflictCount,
                        signOutSyncError = latest.lastSyncError
                            ?: "Pending changes or sync conflicts remain. Resolve them or choose Sign Out Anyway.",
                    )
                }
            }
        }
    }

    fun confirmSignOutAnyway() {
        _formState.update {
            it.copy(
                showUnsyncedSignOutWarning = false,
                isSyncingBeforeSignOut = false,
                signOutSyncError = null,
            )
        }
        signOut()
    }

    fun signOut() {
        if (_formState.value.isSigningOut) return
        _formState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    private fun validateForm(state: ProfileFormState, validateEmail: Boolean): Boolean {
        val fullNameErr = AuthValidation.validateFullName(state.fullName)
        val emailErr = if (validateEmail) AuthValidation.validateEmail(state.email) else null
        val universityErr = AuthValidation.validateUniversity(state.university)
        val departmentErr = AuthValidation.validateDepartment(state.department)
        val semesterErr = AuthValidation.validateSemester(state.semester)
        val imageErr = AuthValidation.validateProfileImageUrl(state.profileImage)

        if (
            fullNameErr != null ||
            emailErr != null ||
            universityErr != null ||
            departmentErr != null ||
            semesterErr != null ||
            imageErr != null
        ) {
            _formState.update {
                it.copy(
                    fullNameErrorRes = fullNameErr,
                    emailErrorRes = emailErr,
                    universityErrorRes = universityErr,
                    departmentErrorRes = departmentErr,
                    semesterErrorRes = semesterErr,
                    profileImageErrorRes = imageErr,
                )
            }
            return false
        }
        return true
    }

    override fun onCleared() {
        linkResendTimerJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val RESEND_COOLDOWN_SECONDS = 60

        fun factory(
            uid: String,
            authUser: AuthenticatedUser,
            userProfileRepository: UserProfileRepository,
            authRepository: AuthRepository,
            syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileDashboardViewModel(
                    uid = uid,
                    authUser = authUser,
                    userProfileRepository = userProfileRepository,
                    authRepository = authRepository,
                    syncCoordinator = syncCoordinator,
                ) as T
            }
        }
    }
}
