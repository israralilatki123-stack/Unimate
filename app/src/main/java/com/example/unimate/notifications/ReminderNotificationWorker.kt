package com.example.unimate.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.unimate.R
import com.example.unimate.data.model.CalendarReminderMappers
import com.example.unimate.data.model.CreateAppNotificationRequest
import com.example.unimate.data.model.NotificationSourceType
import com.example.unimate.data.model.ReminderCategory
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReminderNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID).orEmpty()
        val reminderId = inputData.getString(KEY_REMINDER_ID).orEmpty().ifBlank { id.toString() }
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "UniMate Reminder" }
        val rawBody = inputData.getString(KEY_BODY).orEmpty()
        val categoryWire = inputData.getString(KEY_CATEGORY)
        val category = ReminderCategory.fromWire(categoryWire)
        val body = rawBody.ifBlank { "${category.label} in UniMate" }

        ReminderNotificationHelper.showReminderNotification(
            context = applicationContext,
            reminderId = reminderId,
            title = title,
            body = body,
            categoryLabel = category.label,
        )

        recordInAppNotificationIfSignedIn(
            uid = uid,
            reminderId = reminderId,
            title = title,
            body = body,
            category = category,
        )

        return Result.success()
    }

    private suspend fun recordInAppNotificationIfSignedIn(
        uid: String,
        reminderId: String,
        title: String,
        body: String,
        category: ReminderCategory,
    ) {
        if (uid.isBlank()) return
        runCatching {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
            }
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            if (currentUid != uid) return
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            val notifDocId = "rem_${reminderId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}_${System.currentTimeMillis()}"
                .take(120)
            val payload = CalendarReminderMappers.toCreateNotificationPayload(
                uid = uid,
                request = CreateAppNotificationRequest(
                    title = title,
                    body = body,
                    category = category,
                    sourceType = NotificationSourceType.REMINDER,
                    sourceId = reminderId,
                    deliveredAtMillis = System.currentTimeMillis(),
                ),
            )
            db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notifDocId)
                .set(payload)
                .await()
        }
    }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_CATEGORY = "category"
    }
}
