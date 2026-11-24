package com.example.unimate.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unimate.R
import com.example.unimate.data.sync.NoOpSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.example.unimate.data.sync.SyncStatusUiState
import com.example.unimate.ui.ai.AiAssistantViewModel
import com.example.unimate.ui.assignments.AssignmentsViewModel
import com.example.unimate.ui.attendance.AttendanceViewModel
import com.example.unimate.ui.auth.AuthBannerType
import com.example.unimate.ui.auth.AuthStatusBanner
import com.example.unimate.ui.calendar.CalendarViewModel
import com.example.unimate.ui.components.UniMateLogoMark
import com.example.unimate.ui.courses.CoursesUiState
import com.example.unimate.ui.courses.CoursesViewModel
import com.example.unimate.ui.exams.ExamsViewModel
import com.example.unimate.ui.gpa.GpaCgpaViewModel
import com.example.unimate.ui.notes.NotesViewModel
import com.example.unimate.ui.notifications.NotificationsScreen
import com.example.unimate.ui.notifications.NotificationsViewModel
import com.example.unimate.ui.quizzes.QuizzesViewModel
import com.example.unimate.ui.reminders.RemindersViewModel
import com.example.unimate.ui.studyplanner.StudyPlannerViewModel
import com.example.unimate.ui.sync.GlobalSyncStatusBanner
import com.example.unimate.ui.sync.SyncStatusDetailsDialog
import com.example.unimate.ui.sync.UnsyncedDataSignOutDialog
import com.example.unimate.ui.theme.UniMatePurplePrimary
import com.example.unimate.ui.timetable.TimetableViewModel

private data class NavDestinationSpec(
    val tab: DashboardTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val dashboardDestinations = listOf(
    NavDestinationSpec(
        tab = DashboardTab.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_item_home",
    ),
    NavDestinationSpec(
        tab = DashboardTab.PLANNER,
        labelRes = R.string.nav_planner,
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        testTag = "nav_item_planner",
    ),
    NavDestinationSpec(
        tab = DashboardTab.TASKS,
        labelRes = R.string.nav_tasks,
        selectedIcon = Icons.Rounded.TaskAlt,
        unselectedIcon = Icons.Outlined.TaskAlt,
        testTag = "nav_item_tasks",
    ),
    NavDestinationSpec(
        tab = DashboardTab.ACADEMICS,
        labelRes = R.string.nav_academics,
        selectedIcon = Icons.Rounded.School,
        unselectedIcon = Icons.Outlined.School,
        testTag = "nav_item_academics",
    ),
    NavDestinationSpec(
        tab = DashboardTab.AI_ASSISTANT,
        labelRes = R.string.nav_ai,
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_item_ai",
    ),
)

@Composable
fun DashboardRouteScreen(
    viewModel: ProfileDashboardViewModel,
    mainDashboardViewModel: MainDashboardViewModel,
    timetableViewModel: TimetableViewModel,
    coursesViewModel: CoursesViewModel,
    assignmentsViewModel: AssignmentsViewModel,
    quizzesViewModel: QuizzesViewModel,
    examsViewModel: ExamsViewModel,
    calendarViewModel: CalendarViewModel,
    remindersViewModel: RemindersViewModel,
    notificationsViewModel: NotificationsViewModel,
    attendanceViewModel: AttendanceViewModel,
    gpaCgpaViewModel: GpaCgpaViewModel,
    notesViewModel: NotesViewModel,
    studyPlannerViewModel: StudyPlannerViewModel,
    aiAssistantViewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier,
    syncCoordinator: SyncCoordinator = NoOpSyncCoordinator,
) {
    val profileUiState by viewModel.profileUiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val syncState by syncCoordinator.syncState.collectAsStateWithLifecycle()
    var showSyncDetailsDialog by rememberSaveable { mutableStateOf(false) }

    when (val state = profileUiState) {
        ProfileUiState.Loading -> {
            DashboardLoadingContent(modifier = modifier)
        }

        ProfileUiState.NeedsProfileSetup -> {
            CompleteProfileScreen(
                formState = formState,
                onFullNameChanged = viewModel::onFullNameChanged,
                onEmailChanged = viewModel::onEmailChanged,
                onUniversityChanged = viewModel::onUniversityChanged,
                onDepartmentChanged = viewModel::onDepartmentChanged,
                onSemesterSelected = viewModel::onSemesterSelected,
                onProfileImageChanged = viewModel::onProfileImageChanged,
                onSubmitProfile = viewModel::submitInitialProfile,
                onSignOutClick = viewModel::requestSignOut,
                modifier = modifier,
            )
        }

        is ProfileUiState.Error -> {
            DashboardErrorContent(
                message = state.message,
                isSigningOut = formState.isSigningOut,
                onSignOutClick = viewModel::requestSignOut,
                modifier = modifier,
            )
        }

        is ProfileUiState.Ready -> {
            ReadyDashboardContainer(
                profile = state.profile,
                formState = formState,
                syncState = syncState,
                onOpenSyncDetails = { showSyncDetailsDialog = true },
                onSyncNow = syncCoordinator::syncNow,
                onDismissJustSynced = syncCoordinator::dismissJustSyncedBanner,
                profileViewModel = viewModel,
                mainDashboardViewModel = mainDashboardViewModel,
                timetableViewModel = timetableViewModel,
                coursesViewModel = coursesViewModel,
                assignmentsViewModel = assignmentsViewModel,
                quizzesViewModel = quizzesViewModel,
                examsViewModel = examsViewModel,
                calendarViewModel = calendarViewModel,
                remindersViewModel = remindersViewModel,
                notificationsViewModel = notificationsViewModel,
                attendanceViewModel = attendanceViewModel,
                gpaCgpaViewModel = gpaCgpaViewModel,
                notesViewModel = notesViewModel,
                studyPlannerViewModel = studyPlannerViewModel,
                aiAssistantViewModel = aiAssistantViewModel,
                modifier = modifier,
            )
        }
    }

    if (showSyncDetailsDialog) {
        SyncStatusDetailsDialog(
            syncState = syncState,
            onSyncNow = syncCoordinator::syncNow,
            onResolveKeepLocal = syncCoordinator::resolveKeepLocal,
            onResolveUseCloud = syncCoordinator::resolveUseCloud,
            onResolveKeepBoth = syncCoordinator::resolveKeepBoth,
            onDismiss = { showSyncDetailsDialog = false },
        )
    }

    if (formState.showUnsyncedSignOutWarning) {
        UnsyncedDataSignOutDialog(
            pendingCount = formState.unsyncedPendingCount,
            conflictCount = formState.unsyncedConflictCount,
            isOnline = syncState.isOnline,
            isSyncing = formState.isSyncingBeforeSignOut,
            errorMessage = formState.signOutSyncError,
            onSyncAndSignOut = viewModel::syncAndSignOut,
            onStaySignedIn = viewModel::dismissSignOutWarning,
            onSignOutAnyway = viewModel::confirmSignOutAnyway,
        )
    }
}

@Composable
private fun ReadyDashboardContainer(
    profile: com.example.unimate.data.model.UserProfile,
    formState: ProfileFormState,
    syncState: SyncStatusUiState,
    onOpenSyncDetails: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissJustSynced: () -> Unit,
    profileViewModel: ProfileDashboardViewModel,
    mainDashboardViewModel: MainDashboardViewModel,
    timetableViewModel: TimetableViewModel,
    coursesViewModel: CoursesViewModel,
    assignmentsViewModel: AssignmentsViewModel,
    quizzesViewModel: QuizzesViewModel,
    examsViewModel: ExamsViewModel,
    calendarViewModel: CalendarViewModel,
    remindersViewModel: RemindersViewModel,
    notificationsViewModel: NotificationsViewModel,
    attendanceViewModel: AttendanceViewModel,
    gpaCgpaViewModel: GpaCgpaViewModel,
    notesViewModel: NotesViewModel,
    studyPlannerViewModel: StudyPlannerViewModel,
    aiAssistantViewModel: AiAssistantViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val phoneLinkState by profileViewModel.phoneLinkState.collectAsStateWithLifecycle()
    val workspaceState by mainDashboardViewModel.uiState.collectAsStateWithLifecycle()
    val coursesState by coursesViewModel.uiState.collectAsStateWithLifecycle()

    val updatedSuccessMessage = stringResource(R.string.dashboard_profile_updated)
    val phoneLinkedSuccessMessage = stringResource(R.string.dashboard_phone_linked_success)
    val isGmailVerified = profileViewModel.authUser.providerIds.contains("google.com")
    val isPhoneVerified = profileViewModel.authUser.providerIds.contains("phone") ||
        profile.phoneNumber.isNotBlank()
    val enrolledCourses = (coursesState as? CoursesUiState.Success)?.courses.orEmpty()

    BackHandler(
        enabled = workspaceState.activeTab != DashboardTab.HOME && !workspaceState.showNotificationsSheet,
    ) {
        mainDashboardViewModel.selectTab(DashboardTab.HOME)
    }

    if (workspaceState.showNotificationsSheet) {
        NotificationsSheetHost(
            notificationsViewModel = notificationsViewModel,
            remindersViewModel = remindersViewModel,
            onBack = { mainDashboardViewModel.setNotificationsSheetVisible(false) },
            modifier = modifier,
        )
    } else {
        UniMateMainShell(
            activeTab = workspaceState.activeTab,
            onSelectTab = mainDashboardViewModel::selectTab,
            syncState = syncState,
            onOpenSyncDetails = onOpenSyncDetails,
            onSyncNow = onSyncNow,
            onDismissJustSynced = onDismissJustSynced,
            modifier = modifier,
        ) {
            when (workspaceState.activeTab) {
                DashboardTab.HOME -> {
                    HomeTabHost(
                        profile = profile,
                        workspaceState = workspaceState,
                        statusBannerMessage = formState.statusBannerMessage,
                        onDismissStatusBanner = profileViewModel::dismissStatusBanner,
                        mainDashboardViewModel = mainDashboardViewModel,
                        coursesViewModel = coursesViewModel,
                        timetableViewModel = timetableViewModel,
                        assignmentsViewModel = assignmentsViewModel,
                        examsViewModel = examsViewModel,
                        remindersViewModel = remindersViewModel,
                        notesViewModel = notesViewModel,
                    )
                }

                DashboardTab.PLANNER -> {
                    PlannerTabHost(
                        workspaceState = workspaceState,
                        enrolledCourses = enrolledCourses,
                        mainDashboardViewModel = mainDashboardViewModel,
                        timetableViewModel = timetableViewModel,
                        calendarViewModel = calendarViewModel,
                        remindersViewModel = remindersViewModel,
                        studyPlannerViewModel = studyPlannerViewModel,
                    )
                }

                DashboardTab.TASKS -> {
                    TasksTabHost(
                        workspaceState = workspaceState,
                        mainDashboardViewModel = mainDashboardViewModel,
                        assignmentsViewModel = assignmentsViewModel,
                        quizzesViewModel = quizzesViewModel,
                        examsViewModel = examsViewModel,
                    )
                }

                DashboardTab.ACADEMICS -> {
                    AcademicsTabHost(
                        profile = profile,
                        workspaceState = workspaceState,
                        coursesState = coursesState,
                        mainDashboardViewModel = mainDashboardViewModel,
                        coursesViewModel = coursesViewModel,
                        attendanceViewModel = attendanceViewModel,
                        gpaCgpaViewModel = gpaCgpaViewModel,
                        notesViewModel = notesViewModel,
                    )
                }

                DashboardTab.AI_ASSISTANT -> {
                    AiAssistantTabHost(
                        profile = profile,
                        workspaceState = workspaceState,
                        aiAssistantViewModel = aiAssistantViewModel,
                    )
                }
            }
        }
    }

    WorkspaceDialogsHost(
        profile = profile,
        workspaceState = workspaceState,
        formState = formState,
        phoneLinkState = phoneLinkState,
        isGmailVerified = isGmailVerified,
        isPhoneVerified = isPhoneVerified,
        mainDashboardViewModel = mainDashboardViewModel,
        profileViewModel = profileViewModel,
        updatedSuccessMessage = updatedSuccessMessage,
        phoneLinkedSuccessMessage = phoneLinkedSuccessMessage,
        onSendLinkPhoneOtp = { isResend ->
            profileViewModel.sendLinkPhoneOtp(
                activityContext = context,
                successMessage = phoneLinkedSuccessMessage,
                isResend = isResend,
            )
        },
    )

    ModuleDialogsHost(
        enrolledCourses = enrolledCourses,
        timetableViewModel = timetableViewModel,
        coursesViewModel = coursesViewModel,
        assignmentsViewModel = assignmentsViewModel,
        quizzesViewModel = quizzesViewModel,
        examsViewModel = examsViewModel,
        calendarViewModel = calendarViewModel,
        remindersViewModel = remindersViewModel,
        attendanceViewModel = attendanceViewModel,
        gpaCgpaViewModel = gpaCgpaViewModel,
    )

    NotesAndStudyPlannerDialogsHost(
        notesViewModel = notesViewModel,
        studyPlannerViewModel = studyPlannerViewModel,
        enrolledCourses = enrolledCourses,
    )
}

@Composable
private fun NotificationsSheetHost(
    notificationsViewModel: NotificationsViewModel,
    remindersViewModel: RemindersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    val notificationsActionMessage by notificationsViewModel.actionMessage.collectAsStateWithLifecycle()
    val notificationsMutating by notificationsViewModel.isMutating.collectAsStateWithLifecycle()

    NotificationsScreen(
        uiState = notificationsState,
        actionMessage = notificationsActionMessage,
        isMutating = notificationsMutating,
        onSelectFilter = notificationsViewModel::selectFilter,
        onMarkAsRead = notificationsViewModel::markAsRead,
        onMarkAllAsRead = notificationsViewModel::markAllAsRead,
        onDeleteNotification = notificationsViewModel::deleteNotification,
        onClearReadNotifications = notificationsViewModel::clearReadNotifications,
        onClearAllNotifications = notificationsViewModel::clearAllNotifications,
        onCreateReminderClick = { remindersViewModel.openAddDialog() },
        onRetry = notificationsViewModel::retry,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun UniMateMainShell(
    activeTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit,
    syncState: SyncStatusUiState,
    onOpenSyncDetails: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissJustSynced: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_route_screen"),
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("dashboard_bottom_navigation"),
                    ) {
                        dashboardDestinations.forEach { dest ->
                            val selected = activeTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                alwaysShowLabel = true,
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("dashboard_navigation_rail"),
                    ) {
                        dashboardDestinations.forEach { dest ->
                            val selected = activeTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onSelectTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    GlobalSyncStatusBanner(
                        syncState = syncState,
                        onOpenDetails = onOpenSyncDetails,
                        onSyncNow = onSyncNow,
                        onDismissJustSynced = onDismissJustSynced,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardLoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .testTag("dashboard_loading_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UniMateLogoMark(
                size = 76.dp,
                borderColor = UniMatePurplePrimary.copy(alpha = 0.25f),
            )
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Text(
                text = stringResource(R.string.dashboard_loading_profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DashboardErrorContent(
    message: String,
    isSigningOut: Boolean,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .testTag("dashboard_error_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                UniMateLogoMark(size = 64.dp)
                Text(
                    text = stringResource(R.string.dashboard_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                AuthStatusBanner(
                    message = message,
                    type = AuthBannerType.ERROR,
                )
                OutlinedButton(
                    onClick = onSignOutClick,
                    enabled = !isSigningOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize(),
                ) {
                    Text(
                        text = if (isSigningOut) {
                            stringResource(R.string.dashboard_signing_out)
                        } else {
                            stringResource(R.string.dashboard_sign_out)
                        },
                    )
                }
            }
        }
    }
}
