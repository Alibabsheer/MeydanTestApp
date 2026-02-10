package com.example.meydantestapp.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.meydantestapp.data.model.DailyReport
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.tasks.await

class ReportPagingSource(
    private val db: FirebaseFirestore,
    private val projectId: String
) : PagingSource<QuerySnapshot, DailyReport>() {

    override fun getRefreshKey(state: PagingState<QuerySnapshot, DailyReport>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, DailyReport> {
        return try {
            val currentPage = params.key ?: db.collection("daily_reports")
                .whereEqualTo("projectId", projectId)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(params.loadSize.toLong())
                .get()
                .await()

            val lastVisible = if (currentPage.isEmpty) null else currentPage.documents[currentPage.size() - 1]
            
            val nextSnapshot = if (lastVisible == null || currentPage.size() < params.loadSize) {
                null
            } else {
                db.collection("daily_reports")
                    .whereEqualTo("projectId", projectId)
                    .orderBy("date", Query.Direction.DESCENDING)
                    .startAfter(lastVisible)
                    .limit(params.loadSize.toLong())
                    .get()
                    .await()
            }

            LoadResult.Page(
                data = currentPage.toObjects(DailyReport::class.java),
                prevKey = null,
                nextKey = nextSnapshot
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
