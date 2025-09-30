package com.example.meydantestapp.utils

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot

private const val TAG_MIGRATION = "FirestoreDateMigration"

fun DocumentSnapshot.migrateTimestampIfNeeded(
    fieldName: String,
    conversion: FirestoreTimestampConverter.ConversionResult
) {
    val timestamp = conversion.timestamp ?: return
    if (!conversion.migratedFromString) return

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
