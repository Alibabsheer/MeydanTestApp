/**
 * Cloud Function: verifyJoinCode (محميّة بـ App Check)
 * - تستقبل joinCode من التطبيق (callable function)
 * - تبحث داخل مجموعة organizations عن وثيقة joinCode مطبّعة
 * - تعيد organizationId و organizationName عند النجاح
 * - تعيد خطأ not-found عند عدم التطابق
 */

const { onCall } = require("firebase-functions/v2/https");
const { setGlobalOptions } = require("firebase-functions/v2/options");
const admin = require("firebase-admin");

// تهيئة Admin مرة واحدة
try { admin.initializeApp(); } catch (e) {}

// ضبط خيارات عامة (اختياري)
setGlobalOptions({
  region: "us-central1",   // إن كانت منطقتك مختلفة غيّرها هنا وهنا فقط
  maxInstances: 10,
});

/**
 * دالة قابلة للنداء من التطبيق ومحمية بـ App Check
 * ملاحظة: enforceAppCheck = true => لا تُنفَّذ إلا إذا كان الطلب يحمل توكن App Check صالح
 */
exports.verifyJoinCode = onCall(
  { enforceAppCheck: true },    // ✅ حماية App Check
  async (request) => {
    try {
      // استلام البيانات
      let code = request.data && request.data.joinCode ? String(request.data.joinCode) : "";
      // تطبيع الإدخال
      code = code.trim().toUpperCase().replace(/\s+/g, "");

      if (!code) {
        // invalid-argument = خطأ من العميل
        const err = new Error("JOIN_CODE_REQUIRED");
        err.code = "invalid-argument";
        throw err;
      }

      const db = admin.firestore();

      // نبحث داخل organizations عن وثيقة تحمل joinCode مطابق
      const snap = await db
        .collection("organizations")
        .where("joinCode", "==", code)
        .limit(1)
        .get();

      if (snap.empty) {
        const err = new Error("INVALID_JOIN_CODE");
        err.code = "not-found";
        throw err;
      }

      const doc = snap.docs[0];
      const organizationId = doc.id;
      const organizationName = doc.get("organizationName") || "Organization";

      // نتيجة النجاح التي سيستقبلها التطبيق
      return { organizationId, organizationName };
    } catch (e) {
      // تحويل الأخطاء لصيغة مفهومة للعميل
      // إن كان الخطأ يحمل code معروف من Functions V2، أعده كما هو
      const known = ["not-found", "invalid-argument", "failed-precondition", "permission-denied"];
      const code = known.includes(e.code) ? e.code : "internal";
      const message = e.message || "UNKNOWN_ERROR";
      // إلقاء خطأ بالصيغة القياسية لـ onCall V2
      throw new (class extends Error {
        constructor() {
          super(message);
          this.code = code;
        }
      })();
    }
  }
);
