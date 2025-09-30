package com.example.meydantestapp.utils

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

private const val TAG_MIGRATION = "FirestoreDateMigration"

fun DocumentSnapshot.migrateTimestampIfNeeded(
    fieldName: String,
    originalValue: Any?,
    resolved: Timestamp?
) {
    val timestamp = resolved ?: return
    if (originalValue == null || originalValue is Timestamp) return

    reference.update(fieldName, timestamp)
        .addOnSuccessListener {
            Log.d(TAG_MIGRATION, "Migrated $fieldName for document $id to Timestamp")
        }
        .addOnFailureListener { error ->
            Log.w(
                TAG_MIGRATION,
                "Failed to migrate $fieldName for document $id: ${error.message}",
                error
            )
        }
}
