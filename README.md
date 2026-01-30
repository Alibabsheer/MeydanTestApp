# Meydan - نظام إدارة المشاريع الإنشائية

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)
![License](https://img.shields.io/badge/License-Private-red.svg)

**نظام متكامل لإدارة المشاريع الإنشائية للمهندسين المدنيين ومؤسسات المقاولات**

[الميزات](#-الميزات) •
[المتطلبات](#-المتطلبات) •
[الإعداد](#-خطوات-الإعداد) •
[البناء](#-البناء-والتشغيل) •
[البنية](#-البنية-التقنية)

</div>

---

## 📋 نظرة عامة

**Meydan** هو تطبيق Android متكامل مصمم خصيصاً لإدارة المشاريع الإنشائية في الميدان. يوفر التطبيق مجموعة شاملة من الأدوات لتسهيل عمل المهندسين المدنيين والمقاولين، من إنشاء التقارير اليومية إلى إدارة جداول الكميات والمستخدمين.

### 🎯 الهدف الرئيسي

تسهيل إدارة المشاريع الإنشائية من خلال:
- توثيق العمل اليومي بالصور والتقارير
- تتبع تقدم المشروع في الوقت الفعلي
- إدارة فرق العمل والمستخدمين
- مشاركة التقارير الاحترافية بصيغة PDF

---

## ✨ الميزات

### 🔐 نظام المصادقة
- **تسجيل دخول آمن** باستخدام Firebase Authentication
- **نظام متعدد الأدوار:**
  - حسابات المؤسسات (Organization Accounts)
  - حسابات المستخدمين التابعين (Affiliated Users)
- **كود انضمام فريد** لكل مؤسسة
- **إدارة الجلسات** مع مهلة خمول ذكية (ساعة واحدة)

### 📊 إدارة المشاريع
- **إنشاء وتتبع المشاريع** الإنشائية
- **تحديد موقع المشروع** باستخدام Google Maps
- **معلومات تفصيلية** عن كل مشروع:
  - اسم المشروع
  - المالك
  - المقاول
  - الاستشاري
  - تواريخ البداية والنهاية
  - الموقع الجغرافي

### 📝 التقارير اليومية
- **إنشاء تقارير يومية** شاملة تتضمن:
  - صور المشروع (حتى 10 صور)
  - قوالب صور متعددة (1x1, 2x2, 3x3, 2x3)
  - تعليقات على كل صورة
  - معلومات الطقس التلقائية
  - موقع المشروع
  - نشاطات المشروع
  - الآلات والمعدات
  - العوائق والتحديات
- **تصدير PDF احترافي** قابل للمشاركة
- **ترقيم تلقائي** للتقارير (DailyReport-1, DailyReport-2, ...)
- **عرض وتصفح** جميع التقارير السابقة

### 📐 جداول الكميات والعقود
- **جداول الكميات** (Quantity Tables)
- **المقطوعيات** (Lump Sum Tables)
- **إدارة العقود** والاتفاقيات

### 👥 إدارة المستخدمين
- **إضافة مستخدمين** جدد للمؤسسة
- **نظام كود الانضمام** الآمن
- **عرض وإدارة** جميع المستخدمين
- **صلاحيات متعددة** حسب نوع الحساب

### 🎨 واجهة المستخدم
- **دعم الوضع الليلي** (Dark Mode)
- **دعم اللغة العربية** كاملاً (RTL)
- **تصميم Material Design** حديث
- **تجربة مستخدم سلسة** ومريحة

### 🌐 الاتصال والتزامن
- **العمل بدون إنترنت** (Offline Mode) مع Firestore
- **مزامنة تلقائية** عند توفر الاتصال
- **تحميل الصور** بجودة عالية إلى Firebase Storage
- **ضغط الصور** الذكي لتوفير المساحة

---

## 🛠 المتطلبات

### متطلبات التطوير

- **Android Studio:** Arctic Fox أو أحدث
- **JDK:** Java 17
- **Android SDK:**
  - Minimum SDK: 26 (Android 8.0)
  - Target SDK: 35 (Android 15)
  - Compile SDK: 35
- **Gradle:** 8.13 أو أحدث
- **Kotlin:** 1.9.0 أو أحدث

### متطلبات Firebase

يتطلب التطبيق إعداد مشروع Firebase مع الخدمات التالية:
- **Firebase Authentication** (Email/Password)
- **Cloud Firestore** (قاعدة البيانات)
- **Firebase Storage** (تخزين الصور)
- **Firebase App Check** (الأمان)

### متطلبات Google Cloud

- **Google Maps API Key** لعرض الخرائط وتحديد المواقع

---

## 🚀 خطوات الإعداد

### 1. استنساخ المستودع

```bash
git clone https://github.com/Alibabsheer/MeydanTestApp.git
cd MeydanTestApp
```

### 2. إعداد Firebase

#### أ. إنشاء مشروع Firebase

1. اذهب إلى [Firebase Console](https://console.firebase.google.com/)
2. أنشئ مشروع جديد أو استخدم مشروع موجود
3. أضف تطبيق Android بـ Package Name: `com.example.meydantestapp`

#### ب. تفعيل الخدمات المطلوبة

1. **Authentication:**
   - اذهب إلى **Authentication** > **Sign-in method**
   - فعّل **Email/Password**

2. **Firestore Database:**
   - اذهب إلى **Firestore Database**
   - أنشئ قاعدة بيانات في وضع **Production**
   - أضف القواعد الأمنية (انظر `firestore.rules`)

3. **Storage:**
   - اذهب إلى **Storage**
   - فعّل Firebase Storage
   - أضف قواعد الأمان (انظر `storage.rules`)

4. **App Check:**
   - اذهب إلى **App Check**
   - فعّل Play Integrity API

#### ج. تحميل google-services.json

1. من **Project Settings** > **General**
2. اضغط على **Download google-services.json**
3. ضع الملف في: `app/google-services.json`

### 3. إعداد Google Maps

#### أ. الحصول على API Key

1. اذهب إلى [Google Cloud Console](https://console.cloud.google.com/)
2. أنشئ مشروع جديد أو استخدم مشروع موجود
3. فعّل **Maps SDK for Android**
4. أنشئ API Key من **Credentials**

#### ب. إضافة المفتاح للمشروع

أنشئ ملف `local.properties` في جذر المشروع:

```properties
sdk.dir=/path/to/android/sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE
```

**⚠️ مهم:** لا ترفع ملف `local.properties` إلى Git (موجود في `.gitignore`)

### 4. بناء المشروع

افتح المشروع في Android Studio وانتظر حتى يكتمل Gradle Sync.

---

## 🏗 البناء والتشغيل

### البناء من Android Studio

1. افتح المشروع في Android Studio
2. اختر Build Variant (Debug أو Release)
3. اضغط على **Run** أو `Shift + F10`

### البناء من سطر الأوامر

#### Debug Build

```bash
./gradlew assembleDebug
```

الملف الناتج: `app/build/outputs/apk/debug/app-debug.apk`

#### Release Build

```bash
./gradlew assembleRelease
```

الملف الناتج: `app/build/outputs/apk/release/app-release.apk`

**ملاحظة:** Release Build يتطلب إعداد Keystore للتوقيع.

### إعداد Keystore للنشر

1. أنشئ Keystore:

```bash
keytool -genkey -v -keystore meydan-release.keystore -alias meydan -keyalg RSA -keysize 2048 -validity 10000
```

2. أضف معلومات Keystore إلى `local.properties`:

```properties
KEYSTORE_FILE=../meydan-release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=meydan
KEY_PASSWORD=your_key_password
```

---

## 🏛 البنية التقنية

### معمارية التطبيق

التطبيق يتبع معمارية **MVVM** (Model-View-ViewModel):

```
app/
├── adapters/          # RecyclerView Adapters
├── common/            # ملفات مشتركة
├── data/              # Data Layer
│   ├── model/         # Data Models
│   └── repository/    # Repositories
├── models/            # Domain Models
├── network/           # Network Layer
├── report/            # PDF Report Generation
├── repository/        # Repository Pattern
├── ui/                # UI Components
├── util/              # Utility Classes
├── utils/             # Helper Functions
└── view/              # Custom Views
```

### التقنيات المستخدمة

#### Core

- **Kotlin** - لغة البرمجة الأساسية
- **Coroutines** - للعمليات غير المتزامنة
- **Flow** - للبيانات التفاعلية
- **ViewBinding** - للوصول الآمن للـ Views

#### Architecture Components

- **ViewModel** - إدارة حالة UI
- **LiveData** - البيانات القابلة للمراقبة
- **Lifecycle** - إدارة دورة حياة المكونات

#### Firebase

- **Firebase Auth** - المصادقة
- **Cloud Firestore** - قاعدة البيانات
- **Firebase Storage** - تخزين الملفات
- **Firebase App Check** - الأمان

#### UI/UX

- **Material Design 3** - مكونات UI
- **RecyclerView** - القوائم
- **ViewPager2** - التمرير بين الصفحات
- **ConstraintLayout** - التخطيطات المرنة

#### Image Loading

- **Coil** - تحميل وعرض الصور

#### Networking

- **Retrofit** - HTTP Client
- **Gson** - JSON Parsing
- **OkHttp** - Network Layer

#### Other Libraries

- **Apache POI** - معالجة ملفات Excel
- **Google Maps** - الخرائط
- **Google Places** - البحث عن الأماكن
- **WorkManager** - المهام الخلفية
- **Exif Interface** - معالجة بيانات الصور

---

## 📁 هيكل قاعدة البيانات (Firestore)

### Collections الرئيسية

#### 1. organizations

```javascript
organizations/{organizationId}/
├── organizationName: string
├── activityType: string
├── email: string
├── joinCode: string (8 أحرف كبيرة)
├── createdAt: timestamp
└── ownerId: string
```

#### 2. organizations/{orgId}/users

```javascript
organizations/{orgId}/users/{userId}/
├── uid: string
├── name: string
├── email: string
├── organizationId: string
├── organizationName: string
├── accountType: "user"
├── role: "user"
└── createdAt: timestamp
```

#### 3. userslogin

```javascript
userslogin/{userId}/
├── uid: string
├── email: string
├── organizationId: string
└── createdAt: timestamp
```

#### 4. organizations/{orgId}/projects

```javascript
organizations/{orgId}/projects/{projectId}/
├── projectName: string
├── projectOwner: string
├── projectContractor: string
├── projectConsultant: string
├── startDate: timestamp
├── endDate: timestamp
├── location: geopoint
├── locationName: string
├── dailyReportSeq: number (عداد التقارير)
├── createdAt: timestamp
└── updatedAt: timestamp
```

#### 5. organizations/{orgId}/projects/{projectId}/dailyReports

```javascript
organizations/{orgId}/projects/{projectId}/dailyReports/{reportId}/
├── reportNumber: string (DailyReport-1)
├── reportIndex: number (1)
├── date: string (yyyy-MM-dd)
├── temperature: string
├── weatherStatus: string
├── projectLocation: string
├── activities: string
├── equipment: string
├── obstacles: string
├── createdBy: string
├── createdAt: timestamp
├── sitepages: array<string> (روابط صفحات PDF)
├── sitepagesmeta: array<object>
└── photos: array<string> (للتوافق القديم)
```

---

## 🔒 الأمان

### Firebase Security Rules

#### Firestore Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // السماح للمستخدمين المصادقين فقط
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
    
    // قواعد المؤسسات
    match /organizations/{orgId} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == orgId;
    }
    
    // قواعد المستخدمين
    match /organizations/{orgId}/users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == orgId;
    }
  }
}
```

#### Storage Rules

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null 
                   && request.resource.size < 10 * 1024 * 1024
                   && request.resource.contentType.matches('image/.*');
    }
  }
}
```

### App Check

التطبيق يستخدم Firebase App Check للحماية من:
- الطلبات غير المصرح بها
- إساءة استخدام الخدمات
- الهجمات الآلية

---

## 🧪 الاختبار

### تشغيل Unit Tests

```bash
./gradlew test
```

### تشغيل Instrumentation Tests

```bash
./gradlew connectedAndroidTest
```

---

## 📦 النشر

### إنشاء APK للنشر

1. تأكد من إعداد Keystore
2. قم ببناء Release:

```bash
./gradlew assembleRelease
```

3. الملف الناتج: `app/build/outputs/apk/release/app-release.apk`

### إنشاء App Bundle (AAB)

```bash
./gradlew bundleRelease
```

الملف الناتج: `app/build/outputs/bundle/release/app-release.aab`

**ملاحظة:** AAB هو الصيغة المطلوبة للنشر على Google Play Store.

---

## 🐛 المشاكل الشائعة وحلولها

### 1. خطأ "SDK location not found"

**الحل:** تأكد من وجود ملف `local.properties` مع مسار SDK:

```properties
sdk.dir=/path/to/android/sdk
```

### 2. خطأ "google-services.json not found"

**الحل:** تأكد من وجود الملف في `app/google-services.json`

### 3. خطأ "Failed to resolve: com.google.firebase"

**الحل:** تأكد من إضافة `google-services` plugin في `build.gradle`

### 4. الخرائط لا تعمل

**الحل:** تأكد من:
- إضافة `MAPS_API_KEY` في `local.properties`
- تفعيل Maps SDK في Google Cloud Console
- إضافة SHA-1 fingerprint في Firebase

---

## 📝 الترخيص

هذا المشروع خاص ومملوك لـ **المهندس علي**. جميع الحقوق محفوظة.

---

## 👨‍💻 المطور

**المهندس علي**
- المدير التنفيذي لمؤسسة المقاولات
- مهندس مدني

---

## 📞 الدعم

للإبلاغ عن مشاكل أو طلب ميزات جديدة، يرجى فتح Issue في المستودع.

---

## 🙏 شكر وتقدير

- **Firebase** - للبنية التحتية السحابية
- **Google** - للخرائط والخدمات
- **Kotlin** - للغة البرمجة الرائعة
- **Android Community** - للمكتبات مفتوحة المصدر

---

<div align="center">

**صُنع بـ ❤️ في السعودية**

</div>
