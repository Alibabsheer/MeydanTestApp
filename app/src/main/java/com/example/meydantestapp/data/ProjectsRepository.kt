package com.example.meydantestapp.data

import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.utils.Constants
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
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        val projects = snapshot.documents.map { doc -> Project.from(doc) }

        if (projects.isEmpty()) {
            return projects
        }

        return projects.sortedWith(
            compareByDescending<Project> { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                .thenByDescending { it.updatedAt?.nanoseconds ?: it.createdAt?.nanoseconds ?: 0 }
                .thenBy { it.projectId }
        )
    }
}
