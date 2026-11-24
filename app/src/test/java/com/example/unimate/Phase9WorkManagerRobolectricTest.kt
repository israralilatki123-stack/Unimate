package com.example.unimate

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.unimate.data.model.ReminderCategory
import com.example.unimate.data.model.ScheduledReminder
import com.example.unimate.notifications.ReminderNotificationHelper
import com.example.unimate.notifications.ReminderNotificationWorker
import com.example.unimate.notifications.WorkManagerReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase9WorkManagerRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `workmanager scheduler enqueues and cancels unique local reminder work`() {
        val scheduler = WorkManagerReminderScheduler(context)
        val now = 1_700_000_000_000L
        val reminder = ScheduledReminder(
            id = "rem-os-midterm",
            uid = "uid-1",
            title = "OS Midterm",
            notes = "Bring student ID",
            category = ReminderCategory.EXAM.wireValue,
            triggerAtMillis = now + 3_600_000L,
            leadMinutes = 15L,
            enabled = true,
            completed = false,
        )

        val workId = scheduler.scheduleReminder("uid-1", reminder, now)
        assertTrue(workId.isNotBlank())

        val workInfosBefore = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerReminderScheduler.UNIQUE_WORK_PREFIX + reminder.id)
            .get()
        assertEquals(1, workInfosBefore.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfosBefore.first().state)

        scheduler.cancelReminder(reminder.id)
        val workInfosAfter = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkManagerReminderScheduler.UNIQUE_WORK_PREFIX + reminder.id)
            .get()
        assertEquals(WorkInfo.State.CANCELLED, workInfosAfter.first().state)
    }

    @Test
    fun `reminder notification helper creates channel and worker succeeds safely`() = runBlocking {
        ReminderNotificationHelper.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertNotNull(manager.getNotificationChannel(ReminderNotificationHelper.CHANNEL_ID))
        }

        // Permission defaults false in fresh SDK 36 Robolectric sandbox without crashing
        ReminderNotificationHelper.hasNotificationPermission(context)
        ReminderNotificationHelper.canScheduleExactAlarmsSafely(context)

        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(context)
            .setInputData(
                workDataOf(
                    ReminderNotificationWorker.KEY_UID to "",
                    ReminderNotificationWorker.KEY_REMINDER_ID to "rem-test-1",
                    ReminderNotificationWorker.KEY_TITLE to "Algorithms Lecture",
                    ReminderNotificationWorker.KEY_BODY to "Starts in 15 minutes",
                    ReminderNotificationWorker.KEY_CATEGORY to ReminderCategory.CLASS.wireValue,
                ),
            )
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
