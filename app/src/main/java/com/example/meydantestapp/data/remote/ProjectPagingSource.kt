package com.example.meydantestapp.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.meydantestapp.data.model.Project
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

class ProjectPagingSource(
    private val db: FirebaseFirestore,
    private val orgId: String
) : PagingSource<QuerySnapshot, Project>() {

    override fun getRefreshKey(state: PagingState<QuerySnapshot, Project>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, Project> {
        return try {
            val currentPage = params.key ?: db.collection("projects")
                .whereEqualTo("organizationId", orgId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(params.loadSize.toLong())
                .get()
                .await()

            val lastVisible = currentPage.documents[currentPage.size() - 1]
            val nextQuery = db.collection("projects")
                .whereEqualTo("organizationId", orgId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .startAfter(lastVisible)
                .limit(params.loadSize.toLong())

            val nextSnapshot = if (currentPage.size() < params.loadSize) null else nextQuery.get().await()

            LoadResult.Page(
                data = currentPage.toObjects(Project::class.java),
                prevKey = null,
                nextKey = nextSnapshot
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
