package com.example.meydantestapp.utils

/**
 * ثوابت المشروع العامة – Meydan
 *
 * ملاحظة: تم الإبقاء على القيم والأسماء السابقة لضمان التوافق العكسي،
 * مع إضافة ثوابت مطلوبة لعرض/مشاركة تقارير الـ PDF ودعم الشاشات الجديدة.
 */
object Constants {

    // ======================
    // Firebase Collections
    // ======================
    // Top-level
    const val COLLECTION_ORGANIZATIONS = "organizations"
    const val COLLECTION_USERSLOGIN = "userslogin"       // مرآة دخول عليا لشاشة Login
    const val COLLECTION_PROJECTS = "projects"          // إن استُخدم كمجموعة علوية مستقلة

    // داخل المؤسسة: organizations/{orgId}/users
    const val COLLECTION_USERS = "users"                 // مجلد داخلي (لشاشات الإدارة)

    // تقارير يومية (قديمة/للخلفية)
    const val COLLECTION_DAILY_REPORTS = "daily_reports"

    // Subcollections تحت كيان المشروع
    const val SUBCOLLECTION_DAILY_REPORTS = "dailyReports"

    // ===============
    // SharedPrefs
    // ===============
    const val PREFS_NAME = "MeydanPrefs"
    const val KEY_USER_ID = "user_id"
    const val KEY_ORGANIZATION_ID = "organization_id"
    const val KEY_USER_TYPE = "user_type"
    const val KEY_IS_LOGGED_IN = "is_logged_in"

    // ===============
    // User Types
    // ===============
    const val USER_TYPE_ORGANIZATION = "organization"
    const val USER_TYPE_AFFILIATED = "affiliated"

    // ===============
    // Intent Extras
    // ===============
    const val EXTRA_ORGANIZATION_ID = "organization_id"
    const val EXTRA_ORGANIZATION_NAME = "organization_name"
    const val EXTRA_PROJECT_ID = "project_id"
    const val EXTRA_PROJECT_NAME = "project_name"
    const val EXTRA_REPORT = "report"
    const val EXTRA_JOIN_CODE = "join_code"

    // ===============
    // Request Codes
    // ===============
    const val REQUEST_CODE_SELECT_LOCATION = 1001
    const val REQUEST_CODE_PICK_IMAGE = 1002

    // ===============
    // Validation
    // ===============
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_ORGANIZATION_NAME_LENGTH = 50
    const val MAX_PROJECT_NAME_LENGTH = 50

    // ===============
    // Date Formats
    // ===============
    const val DATE_FORMAT_DISPLAY = "yyyy-MM-dd"
    const val DATE_FORMAT_TIMESTAMP = "yyyy-MM-dd HH:mm:ss"

    // ===============================
    // Daily Reports Numbering (جديد)
    // ===============================
    // حقل عدّاد تسلسلي داخل مستند المشروع: organizations/{orgId}/projects/{projectId}
    const val FIELD_DAILY_REPORT_SEQ = "dailyReportSeq"

    // الحقول المخزّنة داخل مستند التقرير اليومي
    const val FIELD_REPORT_NUMBER = "reportNumber"   // مثال: DailyReport-1
    const val FIELD_REPORT_INDEX = "reportIndex"     // مثال: 1 (قيمة رقمية للفرز)

    // ===============================
    // PDF / مشاركة الملفات (جديد)
    // ===============================
    // مجلد تقارير PDF داخل cacheDir (يتوافق مع file_paths.xml المقترح)
    const val PDF_CACHE_DIR_NAME = "reports"

    // نوع MIME لملفات PDF
    const val MIME_TYPE_PDF = "application/pdf"

    // لاحقة الـ FileProvider الافتراضية للتطبيق
    const val FILE_PROVIDER_SUFFIX = ".fileprovider"
}
