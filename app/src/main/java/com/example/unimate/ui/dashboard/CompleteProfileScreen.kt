package com.example.unimate.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unimate.R
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthOutlinedField
import com.example.unimate.ui.auth.AuthPrimaryButton
import com.example.unimate.ui.auth.AuthScreenShell
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.auth.AuthValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(
    formState: ProfileFormState,
    onFullNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onUniversityChanged: (String) -> Unit,
    onDepartmentChanged: (String) -> Unit,
    onSemesterSelected: (String) -> Unit,
    onProfileImageChanged: (String) -> Unit,
    onSubmitProfile: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var semesterExpanded by remember { mutableStateOf(false) }

    AuthScreenShell(
        title = stringResource(R.string.profile_setup_title),
        subtitle = stringResource(R.string.profile_setup_subtitle),
        badgeText = stringResource(R.string.profile_setup_badge),
        screenTestTag = "complete_profile_screen",
        logoTestTag = "complete_profile_logo",
        modifier = modifier,
        footerContent = {
            TextButton(
                onClick = onSignOutClick,
                enabled = !formState.isSubmitting && !formState.isSigningOut,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("complete_profile_sign_out_button"),
            ) {
                Text(
                    text = stringResource(R.string.profile_setup_switch_account),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        AuthStatusBanner(
            message = formState.submitErrorMessage,
            type = AuthBannerType.ERROR,
            testTag = "complete_profile_error_banner",
        )

        if (formState.isEmailLocked) {
            VerifiedCredentialPill(
                icon = Icons.Rounded.Email,
                label = stringResource(R.string.auth_label_email),
                value = formState.email,
            )
        } else {
            AuthOutlinedField(
                value = formState.email,
                onValueChange = onEmailChanged,
                label = stringResource(R.string.auth_label_email),
                placeholder = stringResource(R.string.auth_hint_email),
                leadingIcon = Icons.Rounded.Email,
                errorRes = formState.emailErrorRes,
                enabled = !formState.isSubmitting,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                testTag = "profile_email_input",
            )
        }

        if (formState.phoneNumber.isNotBlank()) {
            VerifiedCredentialPill(
                icon = Icons.Rounded.Phone,
                label = stringResource(R.string.dashboard_badge_phone_verified),
                value = formState.phoneNumber,
            )
        }

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
            testTag = "profile_full_name_input",
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
            testTag = "profile_university_input",
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
            testTag = "profile_department_input",
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
                    .testTag("profile_semester_dropdown"),
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
            testTag = "profile_image_input",
        )

        Spacer(modifier = Modifier.height(4.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.profile_setup_cta_save),
            loadingText = stringResource(R.string.profile_setup_saving),
            isLoading = formState.isSubmitting,
            onClick = {
                focusManager.clearFocus()
                onSubmitProfile()
            },
            testTag = "profile_setup_submit_button",
        )
    }
}

@Composable
private fun VerifiedCredentialPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
