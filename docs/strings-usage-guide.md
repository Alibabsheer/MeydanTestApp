# 📝 دليل استخدام Strings في تطبيق Meydan

## 🎯 الهدف

توضيح الطريقة الصحيحة لاستخدام `strings.xml` في التطبيق وتجنب Hardcoded Strings.

---

## ❌ الطريقة الخاطئة (Hardcoded Strings)

### في ViewModels:
```kotlin
class LoginViewModel : ViewModel() {
    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _errorMessage.value = "البريد الإلكتروني مطلوب"  // ❌ خطأ
        }
    }
}
```

### المشاكل:
1. صعوبة الترجمة
2. صعوبة الصيانة
3. تكرار النصوص
4. عدم الاحترافية

---

## ✅ الطريقة الصحيحة

### 1. في `strings.xml`:
```xml
<resources>
    <string name="validation_email_required">البريد الإلكتروني مطلوب</string>
    <string name="error_login_failed">فشل تسجيل الدخول: %s</string>
</resources>
```

### 2. في ViewModels (إرجاع Resource IDs):
```kotlin
class LoginViewModel : BaseViewModel() {
    
    // استخدام sealed class للرسائل
    sealed class UiMessage {
        data class StringResource(val resId: Int) : UiMessage()
        data class StringResourceWithArgs(val resId: Int, val args: Array<Any>) : UiMessage()
        data class RawString(val text: String) : UiMessage()  // للحالات الاستثنائية فقط
    }
    
    private val _uiMessage = MutableLiveData<UiMessage?>()
    val uiMessage: LiveData<UiMessage?> = _uiMessage
    
    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _uiMessage.value = UiMessage.StringResource(R.string.validation_email_required)
            return
        }
        
        launchWithResult(
            onSuccess = { userId ->
                // نجح
            },
            onError = { error ->
                _uiMessage.value = UiMessage.StringResourceWithArgs(
                    R.string.error_login_failed,
                    arrayOf(error.message ?: "")
                )
            }
        ) {
            authRepository.loginUser(email, password)
        }
    }
}
```

### 3. في Activities/Fragments (تحويل إلى نصوص):
```kotlin
class LoginActivity : AppCompatActivity() {
    
    private val viewModel: LoginViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel.uiMessage.observe(this) { message ->
            when (message) {
                is LoginViewModel.UiMessage.StringResource -> {
                    Toast.makeText(this, getString(message.resId), Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.UiMessage.StringResourceWithArgs -> {
                    Toast.makeText(this, getString(message.resId, *message.args), Toast.LENGTH_SHORT).show()
                }
                is LoginViewModel.UiMessage.RawString -> {
                    Toast.makeText(this, message.text, Toast.LENGTH_SHORT).show()
                }
                null -> {}
            }
        }
    }
}
```

---

## 🏗️ البنية الموصى بها

### إنشاء `UiMessage.kt` مشترك:

```kotlin
package com.example.meydantestapp.common

import androidx.annotation.StringRes

sealed class UiMessage {
    /**
     * رسالة من strings.xml
     */
    data class StringResource(@StringRes val resId: Int) : UiMessage()
    
    /**
     * رسالة من strings.xml مع معاملات
     * مثال: getString(R.string.error_with_reason, "سبب الخطأ")
     */
    data class StringResourceWithArgs(
        @StringRes val resId: Int,
        val args: Array<Any>
    ) : UiMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as StringResourceWithArgs
            
            if (resId != other.resId) return false
            if (!args.contentEquals(other.args)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.contentHashCode()
            return result
        }
    }
    
    /**
     * نص مباشر (استخدم فقط في الحالات الاستثنائية)
     * مثل: رسائل من API خارجي
     */
    data class RawString(val text: String) : UiMessage()
}
```

### تحديث `BaseViewModel`:

```kotlin
abstract class BaseViewModel : ViewModel() {
    
    private val _uiMessage = MutableLiveData<UiMessage?>()
    val uiMessage: LiveData<UiMessage?> = _uiMessage
    
    /**
     * عرض رسالة من strings.xml
     */
    protected fun showMessage(@StringRes resId: Int) {
        _uiMessage.value = UiMessage.StringResource(resId)
    }
    
    /**
     * عرض رسالة من strings.xml مع معاملات
     */
    protected fun showMessage(@StringRes resId: Int, vararg args: Any) {
        _uiMessage.value = UiMessage.StringResourceWithArgs(resId, args.toList().toTypedArray())
    }
    
    /**
     * عرض نص مباشر (استخدم بحذر)
     */
    protected fun showRawMessage(text: String) {
        _uiMessage.value = UiMessage.RawString(text)
    }
    
    /**
     * مسح الرسائل
     */
    fun clearMessage() {
        _uiMessage.value = null
    }
}
```

---

## 📖 أمثلة عملية

### مثال 1: رسالة بسيطة
```kotlin
// ViewModel
showMessage(R.string.success_project_created)

// Activity
viewModel.uiMessage.observe(this) { message ->
    message?.let { showToast(it) }
}

fun showToast(message: UiMessage) {
    val text = when (message) {
        is UiMessage.StringResource -> getString(message.resId)
        is UiMessage.StringResourceWithArgs -> getString(message.resId, *message.args)
        is UiMessage.RawString -> message.text
    }
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
```

### مثال 2: رسالة مع معاملات
```kotlin
// strings.xml
<string name="error_project_creation_failed">فشل في إنشاء المشروع: %s</string>

// ViewModel
catch (e: Exception) {
    showMessage(R.string.error_project_creation_failed, e.message ?: "")
}
```

### مثال 3: رسالة من API خارجي
```kotlin
// ViewModel
val apiErrorMessage = response.errorMessage  // من API
showRawMessage(apiErrorMessage)  // استثناء: نص من خارج التطبيق
```

---

## 🎯 الفوائد

### 1. سهولة الترجمة 🌍
```
res/
├── values/              # العربية (الافتراضي)
│   └── strings.xml
└── values-en/           # الإنجليزية
    └── strings.xml
```

### 2. صيانة أسهل 🔧
- تعديل النص في مكان واحد
- يؤثر على كل التطبيق

### 3. عدم تكرار 📝
- كل نص له اسم فريد
- يُستخدم في أماكن متعددة

### 4. احترافية ✨
- توافق مع أفضل الممارسات
- جاهز للنشر على Play Store

---

## 📚 قائمة Strings المتوفرة

### رسائل الأخطاء (Error Messages):
- `error_account_deletion_failed`
- `error_account_type_unknown`
- `error_end_date_before_start`
- `error_excel_import_failed`
- `error_fill_all_fields`
- `error_load_org_failed`
- `error_load_users_failed`
- `error_location_not_available`
- `error_logo_update_failed`
- `error_lumpsum_delete_failed`
- `error_map_not_ready`
- `error_org_not_found`
- `error_project_creation_failed`
- `error_project_data_incomplete`
- `error_project_details_not_ready`
- `error_project_not_found`
- `error_project_update_failed`
- `error_quantity_table_delete_failed`
- `error_report_save_failed`
- `error_save_changes_failed`
- `error_select_work_type_first`
- `error_user_deletion_failed`
- `error_user_not_logged_in`

### رسائل النجاح (Success Messages):
- `success_account_created`
- `success_changes_saved`
- `success_code_copied`
- `success_logo_updated`
- `success_lumpsum_imported`
- `success_project_created`
- `success_project_deleted`
- `success_project_updated`
- `success_quantity_table_deleted`
- `success_quantity_table_imported`
- `success_report_saved`
- `success_user_deleted`

### رسائل التحقق (Validation Messages):
- `validation_email_invalid`
- `validation_email_required`
- `validation_join_code_required`
- `validation_org_name_required`
- `validation_org_name_too_long`
- `validation_password_required`
- `validation_password_too_short`
- `validation_project_name_required`
- `validation_project_name_too_long`
- `validation_username_required`

---

## ✅ خطة التنفيذ

### المرحلة 1: إنشاء البنية الأساسية ✅
- [x] إنشاء `UiMessage.kt`
- [x] تحديث `BaseViewModel`
- [x] إنشاء extension functions للـ Activities

### المرحلة 2: تحديث ViewModels (تدريجياً)
- [ ] LoginViewModel
- [ ] RegisterViewModel
- [ ] CreateProjectViewModel
- [ ] CreateDailyReportViewModel
- [ ] ... باقي ViewModels

### المرحلة 3: تحديث Activities (تدريجياً)
- [ ] LoginActivity
- [ ] RegisterActivity
- [ ] CreateProjectActivity
- [ ] ... باقي Activities

---

## 📌 ملاحظات مهمة

### ⚠️ تجنب Context في ViewModels
```kotlin
// ❌ خطأ
class MyViewModel(private val context: Context) : ViewModel() {
    fun doSomething() {
        val text = context.getString(R.string.message)  // خطأ!
    }
}

// ✅ صحيح
class MyViewModel : BaseViewModel() {
    fun doSomething() {
        showMessage(R.string.message)  // صحيح!
    }
}
```

### 💡 متى تستخدم RawString؟
- رسائل من APIs خارجية
- رسائل ديناميكية من Firebase
- حالات استثنائية فقط

---

**تم إعداده بواسطة:** Manus AI  
**التاريخ:** 1 فبراير 2026
