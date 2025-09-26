package com.example.meydantestapp

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
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
@Parcelize
data class Project(
    @get:Exclude var id: String? = null,
    var projectName: String? = null,
    var projectNumber: String? = null,
    var location: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    @get:PropertyName("lat") @set:PropertyName("lat") var lat: Double? = null,
    @get:PropertyName("lng") @set:PropertyName("lng") var lng: Double? = null,
    @get:PropertyName("plus_code") @set:PropertyName("plus_code") var plusCode: String? = null,
    @get:PropertyName("address_text") @set:PropertyName("address_text") var addressText: String? = null,
    @get:PropertyName("locality_hint") @set:PropertyName("locality_hint") var localityHint: String? = null,
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
    @get:PropertyName("address_text") val projectAddressText: String? = null,
    @get:PropertyName("plus_code") val projectPlusCode: String? = null,
    @get:PropertyName("lat") val projectLat: Double? = null,
    @get:PropertyName("lng") val projectLng: Double? = null,
    @get:PropertyName("locality_hint") val projectLocalityHint: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
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
