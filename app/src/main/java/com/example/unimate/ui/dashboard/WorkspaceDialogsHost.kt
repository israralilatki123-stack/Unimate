package com.example.unimate.ui.dashboard

import com.example.unimate.data.model.UserProfile

@androidx.compose.runtime.Composable
internal fun WorkspaceDialogsHost(
    profile: UserProfile,
    workspaceState: MainDashboardUiState,
    formState: ProfileFormState,
    phoneLinkState: PhoneLinkUiState,
    isGmailVerified: Boolean,
    isPhoneVerified: Boolean,
    mainDashboardViewModel: MainDashboardViewModel,
    profileViewModel: ProfileDashboardViewModel,
    updatedSuccessMessage: String,
    phoneLinkedSuccessMessage: String,
    onSendLinkPhoneOtp: (Boolean) -> Unit,
) {
    when (workspaceState.activeDialog) {
        DashboardDialog.ADD_TASK -> AddTaskDialog(
            isMutating = workspaceState.isMutating,
            errorMessage = workspaceState.errorMessage,
            onDismiss = mainDashboardViewModel::dismissDialog,
            onSave = mainDashboardViewModel::addTask,
        )
        DashboardDialog.ADD_NOTE -> AddNoteDialog(
            isMutating = workspaceState.isMutating,
            errorMessage = workspaceState.errorMessage,
            onDismiss = mainDashboardViewModel::dismissDialog,
            onSave = mainDashboardViewModel::addNote,
        )
        DashboardDialog.ADD_REMINDER -> AddReminderDialog(
            isMutating = workspaceState.isMutating,
            errorMessage = workspaceState.errorMessage,
            onDismiss = mainDashboardViewModel::dismissDialog,
            onSave = mainDashboardViewModel::addReminder,
        )
        DashboardDialog.ADD_GOAL -> AddGoalDialog(
            isMutating = workspaceState.isMutating,
            errorMessage = workspaceState.errorMessage,
            onDismiss = mainDashboardViewModel::dismissDialog,
            onSave = mainDashboardViewModel::addGoal,
        )
        DashboardDialog.GPA_CALCULATOR -> GpaCalculatorDialog(
            entries = workspaceState.gpaEntries,
            calculatedGpa = workspaceState.calculatedGpa,
            totalCredits = workspaceState.totalGpaCredits,
            errorMessage = workspaceState.errorMessage,
            onAddCourse = mainDashboardViewModel::addGpaCourse,
            onRemoveCourse = mainDashboardViewModel::removeGpaCourse,
            onUpdateGrade = mainDashboardViewModel::updateGpaEntryGrade,
            onDismiss = mainDashboardViewModel::dismissDialog,
        )
        null -> Unit
    }

    if (workspaceState.showProfileSheet) {
        ProfileAccountDialog(
            profile = profile,
            isGmailVerified = isGmailVerified,
            isPhoneVerified = isPhoneVerified,
            isSigningOut = formState.isSigningOut,
            onEditProfileClick = { profileViewModel.openEditDialog(profile) },
            onVerifyPhoneClick = profileViewModel::openLinkPhoneDialog,
            onSignOutClick = profileViewModel::requestSignOut,
            onDismiss = { mainDashboardViewModel.setProfileSheetVisible(false) },
        )
    }

    if (formState.isEditDialogOpen) {
        EditProfileDialog(
            formState = formState,
            onFullNameChanged = profileViewModel::onFullNameChanged,
            onUniversityChanged = profileViewModel::onUniversityChanged,
            onDepartmentChanged = profileViewModel::onDepartmentChanged,
            onSemesterSelected = profileViewModel::onSemesterSelected,
            onProfileImageChanged = profileViewModel::onProfileImageChanged,
            onDismiss = profileViewModel::dismissEditDialog,
            onSave = { profileViewModel.submitProfileUpdate(updatedSuccessMessage) },
        )
    }

    if (phoneLinkState.isDialogOpen) {
        LinkPhoneOtpDialog(
            state = phoneLinkState,
            onPhoneChanged = profileViewModel::onLinkPhoneChanged,
            onOtpChanged = profileViewModel::onLinkOtpChanged,
            onSendCode = { onSendLinkPhoneOtp(false) },
            onResendCode = { onSendLinkPhoneOtp(true) },
            onVerifyCode = { profileViewModel.verifyLinkPhoneOtp(phoneLinkedSuccessMessage) },
            onDismiss = profileViewModel::dismissLinkPhoneDialog,
        )
    }
}
