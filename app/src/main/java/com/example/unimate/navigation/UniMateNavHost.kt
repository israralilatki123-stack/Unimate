package com.example.unimate.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.unimate.di.UniMateAppContainer
import com.example.unimate.ui.ai.AiAssistantViewModel
import com.example.unimate.ui.assignments.AssignmentsViewModel
import com.example.unimate.ui.attendance.AttendanceViewModel
import com.example.unimate.ui.auth.LoginScreen
import com.example.unimate.ui.auth.LoginViewModel
import com.example.unimate.ui.calendar.CalendarViewModel
import com.example.unimate.ui.courses.CoursesViewModel
import com.example.unimate.ui.dashboard.DashboardRouteScreen
import com.example.unimate.ui.dashboard.MainDashboardViewModel
import com.example.unimate.ui.dashboard.ProfileDashboardViewModel
import com.example.unimate.ui.exams.ExamsViewModel
import com.example.unimate.ui.gpa.GpaCgpaViewModel
import com.example.unimate.ui.notes.NotesViewModel
import com.example.unimate.ui.notifications.NotificationsViewModel
import com.example.unimate.ui.onboarding.OnboardingDestination
import com.example.unimate.ui.onboarding.OnboardingScreen
import com.example.unimate.ui.onboarding.OnboardingViewModel
import com.example.unimate.ui.quizzes.QuizzesViewModel
import com.example.unimate.ui.reminders.RemindersViewModel
import com.example.unimate.ui.splash.SplashDestination
import com.example.unimate.ui.splash.SplashScreen
import com.example.unimate.ui.splash.SplashViewModel
import com.example.unimate.ui.studyplanner.StudyPlannerViewModel
import com.example.unimate.ui.timetable.TimetableViewModel

@Composable
fun UniMateNavHost(
    appContainer: UniMateAppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = UniMateRoutes.SPLASH,
        enterTransition = { fadeIn(animationSpec = tween(240)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        modifier = modifier,
    ) {
        composable(route = UniMateRoutes.SPLASH) {
            val splashViewModel: SplashViewModel = viewModel(
                factory = SplashViewModel.factory(
                    onboardingPreferencesRepository = appContainer.onboardingPreferencesRepository,
                    authStatusRepository = appContainer.authStatusRepository,
                ),
            )

            SplashScreen(
                viewModel = splashViewModel,
                onNavigate = { destination ->
                    val targetRoute = when (destination) {
                        SplashDestination.ONBOARDING -> UniMateRoutes.ONBOARDING
                        SplashDestination.LOGIN -> UniMateRoutes.LOGIN
                        SplashDestination.DASHBOARD -> UniMateRoutes.DASHBOARD
                    }
                    navController.navigate(targetRoute) {
                        popUpTo(UniMateRoutes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = UniMateRoutes.ONBOARDING) {
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.factory(
                    onboardingPreferencesRepository = appContainer.onboardingPreferencesRepository,
                    authStatusRepository = appContainer.authStatusRepository,
                ),
            )

            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigate = { destination ->
                    val targetRoute = when (destination) {
                        OnboardingDestination.LOGIN -> UniMateRoutes.LOGIN
                        OnboardingDestination.DASHBOARD -> UniMateRoutes.DASHBOARD
                    }
                    navController.navigate(targetRoute) {
                        popUpTo(UniMateRoutes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = UniMateRoutes.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(
                    authRepository = appContainer.authRepository,
                    sessionPreferencesRepository = appContainer.sessionPreferencesRepository,
                ),
            )

            LoginScreen(
                viewModel = loginViewModel,
                onNavigateToDashboard = {
                    navController.navigate(UniMateRoutes.DASHBOARD) {
                        popUpTo(UniMateRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(route = UniMateRoutes.DASHBOARD) {
            val authUser by appContainer.authRepository.authStateFlow.collectAsStateWithLifecycle(
                initialValue = appContainer.authRepository.currentAuthenticatedUser(),
            )

            LaunchedEffect(authUser) {
                if (authUser == null) {
                    navController.navigate(UniMateRoutes.LOGIN) {
                        popUpTo(UniMateRoutes.DASHBOARD) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            val currentUser = authUser
            if (currentUser != null) {
                key(currentUser.uid) {
                    val dashboardViewModel: ProfileDashboardViewModel = viewModel(
                        key = "profile-${currentUser.uid}",
                        factory = ProfileDashboardViewModel.factory(
                            uid = currentUser.uid,
                            authUser = currentUser,
                            userProfileRepository = appContainer.userProfileRepository,
                            authRepository = appContainer.authRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val mainDashboardViewModel: MainDashboardViewModel = viewModel(
                        key = "workspace-${currentUser.uid}",
                        factory = MainDashboardViewModel.factory(
                            uid = currentUser.uid,
                            workspaceRepository = appContainer.workspaceRepository,
                            academicModulesRepository = appContainer.academicModulesRepository,
                            calendarRemindersRepository = appContainer.calendarRemindersRepository,
                            attendanceAndGpaRepository = appContainer.attendanceAndGpaRepository,
                            notesAndStudyPlannerRepository = appContainer.notesAndStudyPlannerRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val timetableViewModel: TimetableViewModel = viewModel(
                        key = "timetable-${currentUser.uid}",
                        factory = TimetableViewModel.factory(
                            uid = currentUser.uid,
                            workspaceRepository = appContainer.workspaceRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val coursesViewModel: CoursesViewModel = viewModel(
                        key = "courses-${currentUser.uid}",
                        factory = CoursesViewModel.factory(
                            uid = currentUser.uid,
                            workspaceRepository = appContainer.workspaceRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val assignmentsViewModel: AssignmentsViewModel = viewModel(
                        key = "assignments-${currentUser.uid}",
                        factory = AssignmentsViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.academicModulesRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val quizzesViewModel: QuizzesViewModel = viewModel(
                        key = "quizzes-${currentUser.uid}",
                        factory = QuizzesViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.academicModulesRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val examsViewModel: ExamsViewModel = viewModel(
                        key = "exams-${currentUser.uid}",
                        factory = ExamsViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.academicModulesRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val calendarViewModel: CalendarViewModel = viewModel(
                        key = "calendar-${currentUser.uid}",
                        factory = CalendarViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.calendarRemindersRepository,
                            workspaceRepository = appContainer.workspaceRepository,
                            academicModulesRepository = appContainer.academicModulesRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val remindersViewModel: RemindersViewModel = viewModel(
                        key = "reminders-${currentUser.uid}",
                        factory = RemindersViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.calendarRemindersRepository,
                            scheduler = appContainer.reminderScheduler,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val notificationsViewModel: NotificationsViewModel = viewModel(
                        key = "notifications-${currentUser.uid}",
                        factory = NotificationsViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.calendarRemindersRepository,
                        ),
                    )
                    val attendanceViewModel: AttendanceViewModel = viewModel(
                        key = "attendance-${currentUser.uid}",
                        factory = AttendanceViewModel.factory(
                            uid = currentUser.uid,
                            workspaceRepository = appContainer.workspaceRepository,
                            attendanceAndGpaRepository = appContainer.attendanceAndGpaRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val gpaCgpaViewModel: GpaCgpaViewModel = viewModel(
                        key = "gpa-${currentUser.uid}",
                        factory = GpaCgpaViewModel.factory(
                            uid = currentUser.uid,
                            workspaceRepository = appContainer.workspaceRepository,
                            attendanceAndGpaRepository = appContainer.attendanceAndGpaRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val notesViewModel: NotesViewModel = viewModel(
                        key = "notes-${currentUser.uid}",
                        factory = NotesViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.notesAndStudyPlannerRepository,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val studyPlannerViewModel: StudyPlannerViewModel = viewModel(
                        key = "study-planner-${currentUser.uid}",
                        factory = StudyPlannerViewModel.factory(
                            uid = currentUser.uid,
                            repository = appContainer.notesAndStudyPlannerRepository,
                            localCache = appContainer.studyPlannerLocalCache,
                            syncCoordinator = appContainer.syncCoordinator,
                        ),
                    )
                    val aiAssistantViewModel: AiAssistantViewModel = viewModel(
                        key = "ai-${currentUser.uid}",
                        factory = AiAssistantViewModel.factory(
                            networkMonitor = appContainer.networkMonitor,
                        ),
                    )
                    DashboardRouteScreen(
                        viewModel = dashboardViewModel,
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
                        syncCoordinator = appContainer.syncCoordinator,
                    )
                }
            }
        }
    }
}
