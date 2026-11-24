package com.example.unimate.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.unimate.data.model.ScheduledReminder
import java.util.concurrent.TimeUnit

interface ReminderScheduler {
    fun scheduleReminder(uid: String, reminder: ScheduledReminder, nowMillis: Long = System.currentTimeMillis()): String
    fun cancelReminder(reminderId: String)
}

class WorkManagerReminderScheduler(
    context: Context,
) : ReminderScheduler {
    private val appContext = context.applicationContext

    override fun scheduleReminder(
        uid: String,
        reminder: ScheduledReminder,
        nowMillis: Long,
    ): String {
        if (reminder.id.isBlank()) return ""
        if (!reminder.enabled || reminder.completed) {
            cancelReminder(reminder.id)
            return ""
        }

        val delayMillis = (reminder.effectiveScheduleMillis() - nowMillis).coerceAtLeast(0L)
        val inputData = Data.Builder()
            .putString(ReminderNotificationWorker.KEY_UID, uid)
            .putString(ReminderNotificationWorker.KEY_REMINDER_ID, reminder.id)
            .putString(ReminderNotificationWorker.KEY_TITLE, reminder.title)
            .putString(ReminderNotificationWorker.KEY_BODY, reminder.notes)
            .putString(ReminderNotificationWorker.KEY_CATEGORY, reminder.resolvedCategory().wireValue)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag(UNIQUE_WORK_PREFIX + reminder.id)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_PREFIX + reminder.id,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id.toString()
    }

    override fun cancelReminder(reminderId: String) {
        if (reminderId.isBlank()) return
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_PREFIX + reminderId)
    }

    companion object {
        const val UNIQUE_WORK_PREFIX = "unimate_rem_"
    }
}
