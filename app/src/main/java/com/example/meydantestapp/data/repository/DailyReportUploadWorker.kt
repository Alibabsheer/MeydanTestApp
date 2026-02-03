package com.example.meydantestapp.repository

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.meydantestapp.utils.ImageUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import java.io.IOException

/**
 * Worker يقوم برفع صور التقرير اليومي مع إعادة المحاولة الآلية عند فشل الشبكة.
 */
class DailyReportUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_REPORT_ID = "reportId"
        const val KEY_URI_LIST = "uriList"
        const val KEY_UPLOAD_TARGET = "uploadTarget"
        const val KEY_PROGRESS = "progress"
        const val KEY_RESULT_URLS = "uploadedUrls"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        const val KEY_CLEANUP_URI_LIST = "cleanupUriList"

        private const val META_REPORT_ID = "report_id"
        private const val META_INDEX = "source_index"
    }

    private val storage by lazy { FirebaseStorage.getInstance() }

    override suspend fun doWork(): Result {
        val reportId = inputData.getString(KEY_REPORT_ID).orEmpty()
        if (reportId.isBlank()) {
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "missing-report-id"))
        }

        val target = inputData.getString(KEY_UPLOAD_TARGET)?.let {
            runCatching { DailyReportUploadTarget.valueOf(it) }.getOrNull()
        } ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "unknown-target"))

        val uriStrings = inputData.getStringArray(KEY_URI_LIST)?.toList().orEmpty()
        if (uriStrings.isEmpty()) {
            return Result.success(workDataOf(KEY_RESULT_URLS to emptyArray<String>()))
        }

        val baseRef = storage.reference.child("daily_reports/$reportId/${target.pathSegment}")
        val uploadedUrls = mutableListOf<String>()

        setProgressAsync(workDataOf(KEY_PROGRESS to 0))

        return try {
            uriStrings.forEachIndexed { index, uriString ->
                val uri = Uri.parse(uriString)
                val objectName = target.fileName(index)
                val ref = baseRef.child(objectName)

                val alreadyUploaded = resolveExistingUpload(ref, reportId, index)
                if (alreadyUploaded != null) {
                    uploadedUrls += alreadyUploaded
                    val after = (((index + 1).toDouble() / uriStrings.size) * 100.0).toInt().coerceIn(0, 100)
                    setProgressAsync(workDataOf(KEY_PROGRESS to after))
                    return@forEachIndexed
                }

                val metadata = StorageMetadata.Builder()
                    .setContentType(target.contentType)
                    .setCustomMetadata(META_REPORT_ID, reportId)
                    .setCustomMetadata(META_INDEX, index.toString())
                    .build()

                val uploadTask = ref.putFile(uri, metadata)
                uploadTask.addOnProgressListener { snap ->
                    val perFile = if (snap.totalByteCount > 0L) {
                        snap.bytesTransferred.toDouble() / snap.totalByteCount
                    } else {
                        0.0
                    }
                    val overall = (((index) + perFile) / uriStrings.size * 100.0).toInt().coerceIn(0, 99)
                    setProgressAsync(workDataOf(KEY_PROGRESS to overall))
                }

                uploadTask.await()
                val url = ref.downloadUrl.await().toString()
                uploadedUrls += url

                val after = (((index + 1).toDouble() / uriStrings.size) * 100.0).toInt().coerceIn(0, 100)
                setProgressAsync(workDataOf(KEY_PROGRESS to after))
            }

            runPostUploadCleanup(uriStrings)

            Result.success(
                workDataOf(
                    KEY_RESULT_URLS to uploadedUrls.toTypedArray(),
                    KEY_PROGRESS to 100,
                    KEY_CLEANUP_URI_LIST to uriStrings.toTypedArray()
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (shouldRetry(e)) {
                setProgressAsync(workDataOf(KEY_PROGRESS to 0))
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "upload-failed")))
            }
        }
    }

    private fun runPostUploadCleanup(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        ImageUtils.cleanupCacheUris(applicationContext, uriStrings)
        DailyReportCacheCleanupWorker.enqueue(applicationContext)
    }

    private fun shouldRetry(e: Exception): Boolean {
        return when (e) {
            is FirebaseNetworkException -> true
            is IOException -> true
            is StorageException -> e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED
            else -> false
        }
    }

    private suspend fun resolveExistingUpload(
        ref: StorageReference,
        reportId: String,
        index: Int
    ): String? {
        return try {
            val meta = ref.metadata.await()
            val metaReportId = meta.getCustomMetadata(META_REPORT_ID)
            val metaIndex = meta.getCustomMetadata(META_INDEX)?.toIntOrNull()
            val matchesReport = metaReportId == null || metaReportId == reportId
            val matchesIndex = metaIndex == null || metaIndex == index
            if (matchesReport && matchesIndex) {
                ref.downloadUrl.await().toString()
            } else {
                null
            }
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }
}

/**
 * عمل دوري لتنظيف ملفات cache القديمة التي لم تُحذف بعد عمليات الرفع.
 */
class DailyReportCacheCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val UNIQUE_NAME = "daily-report-cache-cleanup"
        private const val MAX_FILE_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val INTERVAL_HOURS = 24L

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyReportCacheCleanupWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(6L, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val deleted = ImageUtils.cleanupOldCacheFiles(applicationContext, MAX_FILE_AGE_MILLIS)
        return Result.success(workDataOf("deleted" to deleted))
    }
}
