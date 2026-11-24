package com.example.unimate.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthOutlinedField
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.auth.AuthValidation

@Composable
fun LinkPhoneOtpDialog(
    state: PhoneLinkUiState,
    onPhoneChanged: (String) -> Unit,
    onOtpChanged: (String) -> Unit,
    onSendCode: () -> Unit,
    onResendCode: () -> Unit,
    onVerifyCode: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dashboard_link_phone_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_link_phone_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AuthStatusBanner(
                    message = state.errorMessage,
                    type = AuthBannerType.ERROR,
                )
                AuthStatusBanner(
                    message = state.infoMessage,
                    type = AuthBannerType.SUCCESS,
                )

                AuthOutlinedField(
                    value = state.phoneNumber,
                    onValueChange = onPhoneChanged,
                    label = stringResource(R.string.auth_label_phone),
                    placeholder = stringResource(R.string.auth_hint_phone),
                    leadingIcon = Icons.Rounded.Phone,
                    errorRes = state.phoneErrorRes,
                    enabled = !state.isBusy && state.verificationId == null,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                    testTag = "link_phone_input",
                )

                if (state.verificationId != null) {
                    AuthOutlinedField(
                        value = state.otpCode,
                        onValueChange = onOtpChanged,
                        label = stringResource(R.string.auth_label_otp),
                        placeholder = stringResource(R.string.auth_hint_otp),
                        leadingIcon = Icons.Rounded.Dialpad,
                        errorRes = state.otpErrorRes,
                        enabled = !state.isBusy,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        testTag = "link_phone_otp_input",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = onResendCode,
                            enabled = !state.isBusy && state.resendCountdownSeconds == 0,
                        ) {
                            Text(
                                text = if (state.resendCountdownSeconds > 0) {
                                    stringResource(
                                        R.string.login_cta_resend_countdown,
                                        state.resendCountdownSeconds,
                                    )
                                } else {
                                    stringResource(R.string.login_cta_resend_otp)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.verificationId == null) {
                Button(
                    onClick = onSendCode,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("link_phone_send_button"),
                ) {
                    if (state.isSendingOtp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.login_cta_send_otp))
                }
            } else {
                Button(
                    onClick = onVerifyCode,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("link_phone_verify_button"),
                ) {
                    if (state.isVerifyingOtp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.login_cta_verify_otp))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isBusy,
            ) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    formState: ProfileFormState,
    onFullNameChanged: (String) -> Unit,
    onUniversityChanged: (String) -> Unit,
    onDepartmentChanged: (String) -> Unit,
    onSemesterSelected: (String) -> Unit,
    onProfileImageChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var semesterExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!formState.isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.dashboard_edit_profile_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AuthStatusBanner(
                    message = formState.submitErrorMessage,
                    type = AuthBannerType.ERROR,
                )

                AuthOutlinedField(
                    value = formState.fullName,
                    onValueChange = onFullNameChanged,
                    label = stringResource(R.string.auth_label_full_name),
                    placeholder = stringResource(R.string.auth_hint_full_name),
                    leadingIcon = Icons.Rounded.Person,
                    errorRes = formState.fullNameErrorRes,
                    enabled = !formState.isSubmitting,
                    keyboardCapitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                    testTag = "edit_profile_full_name_input",
                )

                AuthOutlinedField(
                    value = formState.university,
                    onValueChange = onUniversityChanged,
                    label = stringResource(R.string.auth_label_university),
                    placeholder = stringResource(R.string.auth_hint_university),
                    leadingIcon = Icons.Rounded.AccountBalance,
                    errorRes = formState.universityErrorRes,
                    enabled = !formState.isSubmitting,
                    keyboardCapitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                    testTag = "edit_profile_university_input",
                )

                AuthOutlinedField(
                    value = formState.department,
                    onValueChange = onDepartmentChanged,
                    label = stringResource(R.string.auth_label_department),
                    placeholder = stringResource(R.string.auth_hint_department),
                    leadingIcon = Icons.AutoMirrored.Rounded.MenuBook,
                    errorRes = formState.departmentErrorRes,
                    enabled = !formState.isSubmitting,
                    keyboardCapitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                    testTag = "edit_profile_department_input",
                )

                ExposedDropdownMenuBox(
                    expanded = semesterExpanded,
                    onExpandedChange = {
                        if (!formState.isSubmitting) {
                            semesterExpanded = !semesterExpanded
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = formState.semester,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !formState.isSubmitting,
                        singleLine = true,
                        label = { Text(stringResource(R.string.auth_label_semester)) },
                        placeholder = { Text(stringResource(R.string.auth_hint_semester)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = semesterExpanded)
                        },
                        isError = formState.semesterErrorRes != null,
                        supportingText = if (formState.semesterErrorRes != null) {
                            { Text(text = stringResource(formState.semesterErrorRes)) }
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("edit_profile_semester_dropdown"),
                    )

                    ExposedDropdownMenu(
                        expanded = semesterExpanded,
                        onDismissRequest = { semesterExpanded = false },
                    ) {
                        AuthValidation.semesterOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onSemesterSelected(option)
                                    semesterExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                AuthOutlinedField(
                    value = formState.profileImage,
                    onValueChange = onProfileImageChanged,
                    label = stringResource(R.string.profile_label_image_url),
                    placeholder = stringResource(R.string.profile_hint_image_url),
                    leadingIcon = Icons.Rounded.Image,
                    errorRes = formState.profileImageErrorRes,
                    enabled = !formState.isSubmitting,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    testTag = "edit_profile_image_input",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !formState.isSubmitting,
                modifier = Modifier.testTag("edit_profile_save_button"),
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.dashboard_save_changes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !formState.isSubmitting,
            ) {
                Text(stringResource(R.string.dashboard_cancel))
            }
        },
    )
}
