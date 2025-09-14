package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import android.net.Uri

class OrganizationRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getOrganizationDoc(userId: String): Map<String, Any>? {
        val doc = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(userId).get().await()
        return doc.data
    }

    suspend fun findOrganizationIdByName(name: String): String? {
        val snap = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .whereEqualTo("name", name)
            .limit(1)
            .get().await()
        val doc = snap.documents.firstOrNull()
        if (doc != null) return doc.id

        val alt = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .whereEqualTo("organizationName", name)
            .limit(1)
            .get().await()
        return alt.documents.firstOrNull()?.id
    }

    suspend fun updateOrganizationFields(
        userId: String,
        name: String? = null,
        activityType: String? = null,
        joinCode: String? = null,
        logoUrl: String? = null
    ) {
        val updates = mutableMapOf<String, Any>()
        name?.let { updates["organizationName"] = it }
        activityType?.let { updates["activityType"] = it }
        joinCode?.let { updates["joinCode"] = it.uppercase() }
        logoUrl?.let { updates["logoUrl"] = it }
        if (updates.isNotEmpty()) {
            firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
                .document(userId).update(updates as Map<String, Any>).await()
        }
    }

    suspend fun setLogoUrl(userId: String, url: String) {
        firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(userId)
            .update(mapOf("logoUrl" to url)).await()
    }

    suspend fun uploadOrganizationLogo(userId: String, imageUri: Uri): String {
        val ref = storage.reference.child("organization_logos/$userId.png")
        ref.putFile(imageUri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun deleteOrganization(userId: String) {
        firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(userId).delete().await()
        // حذف الشعار اختيارياً
        try {
            storage.reference.child("organization_logos/$userId.png").delete().await()
        } catch (_: Exception) { /* ignore if not exists */ }
    }
}
