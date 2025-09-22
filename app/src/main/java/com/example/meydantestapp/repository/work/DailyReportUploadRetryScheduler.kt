package com.example.meydantestapp.repository.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Schedules background retries for failed Storage uploads using WorkManager.
 */
object DailyReportUploadRetryScheduler {

    data class PendingUploadTask(
        val localUri: String,
        val storagePath: String,
        val firestoreField: String,
        val mimeType: String
    )

    data class EnqueueResult(
        val uniqueWorkName: String,
        val workIds: List<UUID>
    )

    fun enqueue(
        context: Context,
        organizationId: String,
        projectId: String,
        reportId: String,
        pending: List<PendingUploadTask>
    ): EnqueueResult? {
        if (pending.isEmpty()) return null

        val workManager = WorkManager.getInstance(context)
        val requests = pending.map { task ->
            buildWorkRequest(task, organizationId, projectId, reportId)
        }

        if (requests.isEmpty()) return null

        val uniqueName = QueuedPhotoUploadWorker.uniqueWorkName(organizationId, projectId, reportId)
        var continuation = workManager.beginUniqueWork(
            uniqueName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            requests.first()
        )
        requests.drop(1).forEach { request ->
            continuation = continuation.then(request)
        }
        continuation.enqueue()

        return EnqueueResult(uniqueName, requests.map(OneTimeWorkRequest::getId))
    }

    private fun buildWorkRequest(
        task: PendingUploadTask,
        organizationId: String,
        projectId: String,
        reportId: String
    ): OneTimeWorkRequest {
        val input = workDataOf(
            QueuedPhotoUploadWorker.KEY_ORGANIZATION_ID to organizationId,
            QueuedPhotoUploadWorker.KEY_PROJECT_ID to projectId,
            QueuedPhotoUploadWorker.KEY_REPORT_ID to reportId,
            QueuedPhotoUploadWorker.KEY_STORAGE_PATH to task.storagePath,
            QueuedPhotoUploadWorker.KEY_LOCAL_URI to task.localUri,
            QueuedPhotoUploadWorker.KEY_FIELD_NAME to task.firestoreField,
            QueuedPhotoUploadWorker.KEY_MIME_TYPE to task.mimeType
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<QueuedPhotoUploadWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(QueuedPhotoUploadWorker.tagForReport(reportId))
            .build()
    }
}
