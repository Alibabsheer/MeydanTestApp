package com.example.meydantestapp.repository.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meydantestapp.utils.Constants
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.FileNotFoundException
import java.io.IOException

class QueuedPhotoUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val organizationId = inputData.getString(KEY_ORGANIZATION_ID).orEmpty()
        val projectId = inputData.getString(KEY_PROJECT_ID).orEmpty()
        val reportId = inputData.getString(KEY_REPORT_ID).orEmpty()
        val storagePath = inputData.getString(KEY_STORAGE_PATH)
        val uriString = inputData.getString(KEY_LOCAL_URI)
        val fieldName = inputData.getString(KEY_FIELD_NAME)
        val mimeType = inputData.getString(KEY_MIME_TYPE) ?: DEFAULT_MIME

        if (
            organizationId.isBlank() || projectId.isBlank() || reportId.isBlank() ||
            storagePath.isNullOrBlank() || uriString.isNullOrBlank() || fieldName.isNullOrBlank()
        ) {
            return Result.failure()
        }

        val storage = FirebaseStorage.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
        val uri = Uri.parse(uriString)

        return try {
            val ref = storage.reference.child(storagePath)
            ref.putFile(uri, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
                .document(organizationId)
                .collection(Constants.COLLECTION_PROJECTS)
                .document(projectId)
                .collection(Constants.SUBCOLLECTION_DAILY_REPORTS)
                .document(reportId)
                .update(fieldName, FieldValue.arrayUnion(downloadUrl))
                .await()

            Result.success()
        } catch (ex: Exception) {
            when (ex) {
                is FirebaseNetworkException -> Result.retry()
                is FirebaseFirestoreException -> when (ex.code) {
                    FirebaseFirestoreException.Code.NOT_FOUND,
                    FirebaseFirestoreException.Code.ABORTED,
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> Result.retry()
                    else -> Result.failure()
                }
                is StorageException -> when (ex.errorCode) {
                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
                    StorageException.ERROR_UNKNOWN,
                    StorageException.ERROR_NOT_AUTHENTICATED,
                    StorageException.ERROR_NOT_AUTHORIZED,
                    StorageException.ERROR_NETWORK_UNAVAILABLE -> Result.retry()
                    else -> Result.failure()
                }
                is IOException, is FileNotFoundException -> Result.retry()
                else -> Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ORGANIZATION_ID = "organization_id"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_REPORT_ID = "report_id"
        const val KEY_STORAGE_PATH = "storage_path"
        const val KEY_LOCAL_URI = "local_uri"
        const val KEY_FIELD_NAME = "field_name"
        const val KEY_MIME_TYPE = "mime_type"

        private const val DEFAULT_MIME = "image/jpeg"

        fun uniqueWorkName(organizationId: String, projectId: String, reportId: String): String =
            "daily-report-upload-$organizationId-$projectId-$reportId"

        fun tagForReport(reportId: String): String = "daily-report-upload-tag-$reportId"
    }
}
