package com.example.meydantestapp

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// ----------------------------------------
// نماذج البيانات (Data Models)
// ----------------------------------------

// بند في جدول الكميات
@Parcelize
data class QuantityItem(
    var itemNumber: String? = null,
    var description: String? = null,
    var unit: String? = null,
    var quantity: Double? = null,
    var unitPrice: Double? = null,
    var totalValue: Double? = null
) : Parcelable

// بند في جدول المقطوعية
@Parcelize
data class LumpSumItem(
    var itemNumber: String? = null,
    var description: String? = null,
    var totalValue: Double? = null
) : Parcelable

// معلومات المشروع الأساسية
@IgnoreExtraProperties
@Parcelize
data class Project(
    @get:Exclude var id: String? = null,
    var projectName: String? = null,
    var projectNumber: String? = null,
    var location: String? = null,
    var projectLocation: String? = null,
    var addressText: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var plusCode: String? = null,
    var googleMapsUrl: String? = null,
    var workType: String? = null,
    var startDate: Timestamp? = null,
    var endDate: Timestamp? = null,
    var contractValue: Double? = null,
    var quantitiesTable: List<QuantityItem>? = null,
    var lumpSumTable: List<LumpSumItem>? = null,
    var createdAt: Timestamp? = null,
    var updatedAt: Timestamp? = null,
    var createdBy: String? = null
) : Parcelable

// تقرير يومي
// مع الإبقاء على photos (متقادم) للتوافق مع تقارير قديمة.
@Parcelize
data class DailyReport(
    var id: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val ownerName: String? = null,
    val contractorName: String? = null,
    val consultantName: String? = null,
    val date: Long? = null,                 // timestamp(ms)
    val temperature: String? = null,
    val weatherStatus: String? = null,
    val skilledLabor: Int? = null,
    val unskilledLabor: Int? = null,
    val totalLabor: Int? = null,
    val dailyActivities: List<String>? = null,
    val resourcesUsed: List<String>? = null,
    val challenges: List<String>? = null,
    val notes: List<String>? = null,

    @Deprecated("Use sitepages")
    val photos: List<String>? = null,

    // --- الحقول الجديدة الخاصة بالصفحات المركّبة ---
    val sitepages: List<String>? = null,
    val sitepagesmeta: List<@RawValue Map<String, Any?>>? = null,

    val reportNumber: String? = null,
    val createdBy: String? = null,
    val createdByName: String? = null,      // اسم مُنشئ التقرير (إن توفر)
    val projectLocation: String? = null,    // موقع المشروع لعرضه في الشاشة/الـ PDF
    val googleMapsUrl: String? = null,      // رابط خرائط Google للموقع (اختياري)
    val organizationName: String? = null,
    val isArchived: Boolean? = false        // لأرشفة التقارير بعد إنشاء الأسبوعي
) : Parcelable

// مورد
@Parcelize
data class Resource(
    val id: String,
    val name: String,
    val type: String,
    val quantity: Double,
    val unit: String
) : Parcelable
