package com.example.unimate.ui.auth

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.unimate.data.repository.AuthRepository
import com.example.unimate.data.repository.GoogleSignInResult
import com.example.unimate.data.repository.PhoneCodeSendResult
import com.example.unimate.data.repository.PhoneVerifyResult
import com.example.unimate.data.repository.SessionPreferencesRepository
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginNavigationTarget {
    data object Dashboard : LoginNavigationTarget
}

data class LoginUiState(
    val rememberSession: Boolean = true,
    val isGoogleLoading: Boolean = false,
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
    val pendingNavigation: LoginNavigationTarget? = null,
) {
    val isAnyActionLoading: Boolean
        get() = isGoogleLoading || isSendingOtp || isVerifyingOtp
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionPreferencesRepository: SessionPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var hasAttemptedAutoSignIn = false
    private var resendTimerJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = runCatching { sessionPreferencesRepository.getSessionPreferences() }
                .getOrNull()
            if (prefs != null) {
                _uiState.update { it.copy(rememberSession = prefs.rememberSession) }
            }

            if ((prefs?.rememberSession != false) && !hasAttemptedAutoSignIn) {
                hasAttemptedAutoSignIn = true
                when (authRepository.attemptAutoSignIn()) {
                    is GoogleSignInResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isGoogleLoading = false,
                                pendingNavigation = LoginNavigationTarget.Dashboard,
                            )
                        }
                    }
                    is GoogleSignInResult.Error,
                    GoogleSignInResult.Cancelled,
                    GoogleSignInResult.NoSilentCredential -> Unit
                }
            }
        }
    }

    fun onRememberSessionChanged(remember: Boolean) {
        _uiState.update { it.copy(rememberSession = remember) }
        viewModelScope.launch {
            runCatching {
                sessionPreferencesRepository.saveRememberSession(
                    remember = remember,
                    email = authRepository.currentAuthenticatedUser()?.email.orEmpty(),
                )
            }
        }
    }

    fun onGoogleSignInClicked(activityContext: Context) {
        if (_uiState.value.isAnyActionLoading) return

        _uiState.update {
            it.copy(
                isGoogleLoading = true,
                errorMessage = null,
                infoMessage = null,
            )
        }

        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(activityContext)) {
                is GoogleSignInResult.Success -> {
                    runCatching {
                        sessionPreferencesRepository.saveRememberSession(
                            remember = _uiState.value.rememberSession,
                            email = result.user.email,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = null,
                            pendingNavigation = LoginNavigationTarget.Dashboard,
                        )
                    }
                }
                GoogleSignInResult.Cancelled,
                GoogleSignInResult.NoSilentCredential -> {
                    _uiState.update { it.copy(isGoogleLoading = false) }
                }
                is GoogleSignInResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isGoogleLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onPhoneNumberChanged(value: String) {
        _uiState.update {
            it.copy(
                phoneNumber = value,
                phoneErrorRes = null,
                errorMessage = null,
            )
        }
    }

    fun onOtpCodeChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update {
            it.copy(
                otpCode = digitsOnly,
                otpErrorRes = null,
                errorMessage = null,
            )
        }
    }

    fun changePhoneNumber() {
        resendTimerJob?.cancel()
        _uiState.update {
            it.copy(
                verificationId = null,
                resendToken = null,
                otpCode = "",
                otpErrorRes = null,
                resendCountdownSeconds = 0,
                infoMessage = null,
                errorMessage = null,
            )
        }
    }

    fun sendPhoneOtp(activityContext: Context, isResend: Boolean = false) {
        val current = _uiState.value
        if (current.isAnyActionLoading) return
        if (isResend && current.resendCountdownSeconds > 0) return

        val phoneErr = AuthValidation.validatePhoneNumber(current.phoneNumber)
        if (phoneErr != null) {
            _uiState.update { it.copy(phoneErrorRes = phoneErr) }
            return
        }

        val normalizedPhone = AuthValidation.normalizePhoneNumber(current.phoneNumber)
        _uiState.update {
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
                    linkToCurrentUser = false,
                )
            ) {
                is PhoneCodeSendResult.CodeSent -> {
                    _uiState.update {
                        it.copy(
                            isSendingOtp = false,
                            verificationId = result.verificationId,
                            resendToken = result.resendToken,
                            infoMessage = "Verification code sent to $normalizedPhone.",
                        )
                    }
                    startResendCountdown()
                }
                is PhoneCodeSendResult.AutoVerified -> {
                    _uiState.update {
                        it.copy(
                            isSendingOtp = false,
                            pendingNavigation = LoginNavigationTarget.Dashboard,
                        )
                    }
                }
                is PhoneCodeSendResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSendingOtp = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun verifyPhoneOtp() {
        val current = _uiState.value
        if (current.isAnyActionLoading) return
        val verId = current.verificationId ?: return

        val otpErr = AuthValidation.validateOtpCode(current.otpCode)
        if (otpErr != null) {
            _uiState.update { it.copy(otpErrorRes = otpErr) }
            return
        }

        _uiState.update { it.copy(isVerifyingOtp = true, errorMessage = null) }

        viewModelScope.launch {
            when (
                val result = authRepository.verifyPhoneOtp(
                    verificationId = verId,
                    smsCode = current.otpCode,
                    linkToCurrentUser = false,
                )
            ) {
                is PhoneVerifyResult.Success -> {
                    resendTimerJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isVerifyingOtp = false,
                            errorMessage = null,
                            pendingNavigation = LoginNavigationTarget.Dashboard,
                        )
                    }
                }
                is PhoneVerifyResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isVerifyingOtp = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    private fun startResendCountdown() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            for (seconds in RESEND_COOLDOWN_SECONDS downTo 0) {
                _uiState.update { it.copy(resendCountdownSeconds = seconds) }
                if (seconds > 0) delay(1_000L)
            }
        }
    }

    fun onNavigationConsumed() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    override fun onCleared() {
        resendTimerJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val RESEND_COOLDOWN_SECONDS = 60

        fun factory(
            authRepository: AuthRepository,
            sessionPreferencesRepository: SessionPreferencesRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(
                    authRepository = authRepository,
                    sessionPreferencesRepository = sessionPreferencesRepository,
                ) as T
            }
        }
    }
}
