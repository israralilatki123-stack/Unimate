package com.example.unimate.di

import android.content.Context
import com.example.unimate.R
import com.example.unimate.data.repository.AcademicModulesRepository
import com.example.unimate.data.repository.AttendanceAndGpaRepository
import com.example.unimate.data.repository.AuthRepository
import com.example.unimate.data.repository.AuthStatusRepository
import com.example.unimate.data.repository.CalendarRemindersRepository
import com.example.unimate.data.repository.DataStoreOnboardingPreferencesRepository
import com.example.unimate.data.repository.DataStoreSessionPreferencesRepository
import com.example.unimate.data.repository.DataStoreStudyPlannerLocalCache
import com.example.unimate.data.repository.FirebaseAuthRepository
import com.example.unimate.data.repository.FirebaseAuthStatusRepository
import com.example.unimate.data.repository.FirestoreAcademicModulesRepository
import com.example.unimate.data.repository.FirestoreAttendanceAndGpaRepository
import com.example.unimate.data.repository.FirestoreCalendarRemindersRepository
import com.example.unimate.data.repository.FirestoreNotesAndStudyPlannerRepository
import com.example.unimate.data.repository.FirestoreUserProfileRepository
import com.example.unimate.data.repository.FirestoreWorkspaceRepository
import com.example.unimate.data.repository.NotesAndStudyPlannerRepository
import com.example.unimate.data.repository.OnboardingPreferencesRepository
import com.example.unimate.data.repository.SessionPreferencesRepository
import com.example.unimate.data.repository.StudyPlannerLocalCache
import com.example.unimate.data.repository.UserProfileRepository
import com.example.unimate.data.repository.WorkspaceRepository
import com.example.unimate.data.sync.AndroidConnectivityNetworkMonitor
import com.example.unimate.data.sync.DataStoreSyncMetadataStore
import com.example.unimate.data.sync.FirebaseFirestoreSyncBackend
import com.example.unimate.data.sync.NetworkMonitor
import com.example.unimate.data.sync.OfflineSyncCoordinator
import com.example.unimate.data.sync.SyncCoordinator
import com.example.unimate.notifications.ReminderScheduler
import com.example.unimate.notifications.WorkManagerReminderScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings

interface UniMateAppContainer {
    val onboardingPreferencesRepository: OnboardingPreferencesRepository
    val sessionPreferencesRepository: SessionPreferencesRepository
    val authStatusRepository: AuthStatusRepository
    val authRepository: AuthRepository
    val networkMonitor: NetworkMonitor
    val syncCoordinator: SyncCoordinator
    val userProfileRepository: UserProfileRepository
    val workspaceRepository: WorkspaceRepository
    val academicModulesRepository: AcademicModulesRepository
    val calendarRemindersRepository: CalendarRemindersRepository
    val attendanceAndGpaRepository: AttendanceAndGpaRepository
    val studyPlannerLocalCache: StudyPlannerLocalCache
    val notesAndStudyPlannerRepository: NotesAndStudyPlannerRepository
    val reminderScheduler: ReminderScheduler
}

class DefaultUniMateAppContainer(
    context: Context,
) : UniMateAppContainer {

    private val appContext = context.applicationContext

    private val firestoreInstance: FirebaseFirestore by lazy {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id)).apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            }
        }
    }

    private val firebaseAuthInstance: FirebaseAuth by lazy {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        FirebaseAuth.getInstance()
    }

    override val networkMonitor: NetworkMonitor by lazy {
        AndroidConnectivityNetworkMonitor(appContext)
    }

    override val syncCoordinator: SyncCoordinator by lazy {
        val coordinator = OfflineSyncCoordinator(
            networkMonitor = networkMonitor,
            metadataStore = DataStoreSyncMetadataStore(appContext),
            backend = FirebaseFirestoreSyncBackend(firestoreInstance),
        )
        coordinator.setActiveUser(firebaseAuthInstance.currentUser?.uid)
        firebaseAuthInstance.addAuthStateListener { auth ->
            coordinator.setActiveUser(auth.currentUser?.uid)
        }
        coordinator
    }

    override val onboardingPreferencesRepository: OnboardingPreferencesRepository by lazy {
        DataStoreOnboardingPreferencesRepository(appContext)
    }

    override val sessionPreferencesRepository: SessionPreferencesRepository by lazy {
        DataStoreSessionPreferencesRepository(appContext)
    }

    override val authStatusRepository: AuthStatusRepository by lazy {
        FirebaseAuthStatusRepository(appContext)
    }

    override val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(appContext)
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        FirestoreUserProfileRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            syncCoordinator = syncCoordinator,
        )
    }

    override val workspaceRepository: WorkspaceRepository by lazy {
        FirestoreWorkspaceRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            syncCoordinator = syncCoordinator,
        )
    }

    override val academicModulesRepository: AcademicModulesRepository by lazy {
        FirestoreAcademicModulesRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            syncCoordinator = syncCoordinator,
        )
    }

    override val calendarRemindersRepository: CalendarRemindersRepository by lazy {
        FirestoreCalendarRemindersRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            syncCoordinator = syncCoordinator,
        )
    }

    override val attendanceAndGpaRepository: AttendanceAndGpaRepository by lazy {
        FirestoreAttendanceAndGpaRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            syncCoordinator = syncCoordinator,
        )
    }

    override val studyPlannerLocalCache: StudyPlannerLocalCache by lazy {
        DataStoreStudyPlannerLocalCache(appContext)
    }

    override val notesAndStudyPlannerRepository: NotesAndStudyPlannerRepository by lazy {
        FirestoreNotesAndStudyPlannerRepository(
            db = firestoreInstance,
            auth = firebaseAuthInstance,
            localCache = studyPlannerLocalCache,
            syncCoordinator = syncCoordinator,
        )
    }

    override val reminderScheduler: ReminderScheduler by lazy {
        WorkManagerReminderScheduler(appContext)
    }
}
