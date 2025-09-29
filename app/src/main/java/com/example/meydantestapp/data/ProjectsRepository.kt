package com.example.meydantestapp.data

import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.utils.Constants
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProjectsRepository(
    private val firestore: FirebaseFirestore,
    private val organizationId: String
) {
    suspend fun fetchProjectsFirstPage(limit: Int = 20): List<Project> {
        val snapshot = firestore
            .collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(organizationId)
            .collection(Constants.COLLECTION_PROJECTS)
            .orderBy(FieldPath.documentId())
            .limit(limit.toLong())
            .get()
            .await()

        return snapshot.documents.map { doc -> Project.from(doc) }
    }
}
