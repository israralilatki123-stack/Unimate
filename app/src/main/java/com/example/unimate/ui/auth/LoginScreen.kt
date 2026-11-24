package com.example.unimate.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.R

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.pendingNavigation) {
        if (uiState.pendingNavigation == LoginNavigationTarget.Dashboard) {
            viewModel.onNavigationConsumed()
            onNavigateToDashboard()
        }
    }

    AuthScreenShell(
        title = stringResource(R.string.login_title),
        subtitle = stringResource(R.string.login_subtitle),
        badgeText = stringResource(R.string.login_badge),
        screenTestTag = "login_screen",
        logoTestTag = "login_brand_logo",
        modifier = modifier,
    ) {
        AuthStatusBanner(
            message = uiState.errorMessage,
            type = AuthBannerType.ERROR,
            testTag = "login_error_banner",
        )

        AuthStatusBanner(
            message = uiState.infoMessage,
            type = AuthBannerType.SUCCESS,
            testTag = "login_info_banner",
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Dual-Provider Verification",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Sign in with Gmail (Google) or Phone Number OTP. You can also link your phone inside your profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Provider 1: Verify with Gmail (Google Sign-In via Jetpack Credential Manager)
        AuthPrimaryButton(
            text = stringResource(R.string.login_cta_google),
            loadingText = stringResource(R.string.login_signing_in),
            isLoading = uiState.isGoogleLoading,
            onClick = {
                focusManager.clearFocus()
                viewModel.onGoogleSignInClicked(context)
            },
            testTag = "login_google_sign_in_button",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !uiState.isAnyActionLoading) {
                    viewModel.onRememberSessionChanged(!uiState.rememberSession)
                }
                .minimumInteractiveComponentSize()
                .padding(horizontal = 4.dp)
                .testTag("login_remember_session_row"),
        ) {
            Checkbox(
                checked = uiState.rememberSession,
                onCheckedChange = if (!uiState.isAnyActionLoading) {
                    viewModel::onRememberSessionChanged
                } else {
                    null
                },
                modifier = Modifier.testTag("login_remember_checkbox"),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.login_remember_session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.login_divider_or_phone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // Provider 2: Verify with Phone Number (Firebase PhoneAuthProvider OTP)
        if (uiState.verificationId == null) {
            AuthOutlinedField(
                value = uiState.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChanged,
                label = stringResource(R.string.auth_label_phone),
                placeholder = stringResource(R.string.auth_hint_phone),
                leadingIcon = Icons.Rounded.Phone,
                errorRes = uiState.phoneErrorRes,
                enabled = !uiState.isAnyActionLoading,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.sendPhoneOtp(context)
                    },
                ),
                testTag = "login_phone_input",
            )

            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.sendPhoneOtp(context)
                },
                enabled = !uiState.isAnyActionLoading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("login_send_otp_button"),
            ) {
                if (uiState.isSendingOtp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.login_sending_otp))
                } else {
                    Text(
                        text = stringResource(R.string.login_cta_send_otp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        } else {
            AuthOutlinedField(
                value = uiState.otpCode,
                onValueChange = viewModel::onOtpCodeChanged,
                label = stringResource(R.string.auth_label_otp),
                placeholder = stringResource(R.string.auth_hint_otp),
                leadingIcon = Icons.Rounded.Dialpad,
                errorRes = uiState.otpErrorRes,
                enabled = !uiState.isAnyActionLoading,
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.verifyPhoneOtp()
                    },
                ),
                testTag = "login_otp_input",
            )

            AuthPrimaryButton(
                text = stringResource(R.string.login_cta_verify_otp),
                loadingText = stringResource(R.string.login_verifying_otp),
                isLoading = uiState.isVerifyingOtp,
                onClick = {
                    focusManager.clearFocus()
                    viewModel.verifyPhoneOtp()
                },
                testTag = "login_verify_otp_button",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::changePhoneNumber,
                    enabled = !uiState.isAnyActionLoading,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("login_change_phone_button"),
                ) {
                    Text(text = stringResource(R.string.login_cta_change_phone))
                }

                TextButton(
                    onClick = { viewModel.sendPhoneOtp(context, isResend = true) },
                    enabled = !uiState.isAnyActionLoading && uiState.resendCountdownSeconds == 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("login_resend_otp_button"),
                ) {
                    Text(
                        text = if (uiState.resendCountdownSeconds > 0) {
                            stringResource(R.string.login_cta_resend_countdown, uiState.resendCountdownSeconds)
                        } else {
                            stringResource(R.string.login_cta_resend_otp)
                        },
                    )
                }
            }
        }
    }
}
