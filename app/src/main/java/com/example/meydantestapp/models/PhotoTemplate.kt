package com.example.meydantestapp.models

/**
 * تعريف قوالب شبكات الصور لصفحة **A4 عمودية**.
 *
 * ✳️ مبادئ مهمة:
 * - التعبئة تتم وفق ترتيب العناصر داخل [slots] من أعلى **يمين** إلى يسار (RTL) ثم الصف التالي.
 *   لهذا السبب تم ضبط قيم الأعمدة مسبقًا في القوالب لتبدأ من العمود الأيمن (index الأكبر) نزولًا للأيسر.
 * - المواضع هنا **شبكية** (row/col مع امتداد colSpan/rowSpan) وليست بكسلية؛
 *   التحويل إلى مستطيلات بكسلية يتم داخل طبقة التخطيط/التركيب (مثلاً PdfGridLayout أو ImageUtils.compose...).
 * - جميع القوالب مصممة لتناسب لوحة صفحة A4 عمودية؛ ارتفاع الخلية يحسب لاحقًا بافتراض نسبة صورة 16:9
 *   مع شريط تعليق أسفل كل خانة.
 */

enum class TemplateId {
    // Even
    E2, E4, E6, E8, E12,
    // Odd (تحتوي خانة/خانات بعرض صف كامل)
    O1, O3A, O3B, O5A, O5B, O7A, O7B, O9A, O9B
}

/** خانة ضمن الشبكة (إحداثيات شبكية لا بكسلية). */
data class SlotSpec(
    val index: Int,            // ترتيب الملء داخل الصفحة
    val row: Int,              // صف البداية (0..rows-1)
    val col: Int,              // عمود البداية (0..columns-1)
    val rowSpan: Int = 1,      // امتداد صفوف
    val colSpan: Int = 1       // امتداد أعمدة
)

/** قالب شبكة صور لصفحة واحدة. */
data class PhotoTemplate(
    val id: TemplateId,
    val displayName: String,
    val columns: Int,
    val rows: Int,
    val slots: List<SlotSpec>,
    val isOdd: Boolean = false
)

/**
 * تجميعة القوالب الجاهزة للاستخدام — كلها A4 عمودية.
 *
 * ملاحظة: ترتيب الأعمدة في التعريف التالي **LTR** (col=0 يسار)،
 * لكننا وفّرنا قيم col بحيث تعبئة الخانات تكون RTL منطقيًا: أعلى **يمين** ثم يسار.
 */
object PhotoTemplates {

    /** جميع القوالب (Even + Odd). */
    val all: List<PhotoTemplate> by lazy {
        buildList {
            // =====================
            // Even Templates
            // =====================
            add(
                PhotoTemplate(
                    id = TemplateId.E2,
                    displayName = "E2 (1×2)",
                    columns = 1, rows = 2,
                    slots = listOf(
                        SlotSpec(0, row = 0, col = 0),
                        SlotSpec(1, row = 1, col = 0)
                    ),
                    isOdd = false
                )
            )

            add(
                PhotoTemplate(
                    id = TemplateId.E4,
                    displayName = "E4 (2×2)",
                    columns = 2, rows = 2,
                    // ترتيب الملء RTL: أعلى يمين -> أعلى يسار -> أسفل يمين -> أسفل يسار
                    slots = listOf(
                        SlotSpec(0, row = 0, col = 1),
                        SlotSpec(1, row = 0, col = 0),
                        SlotSpec(2, row = 1, col = 1),
                        SlotSpec(3, row = 1, col = 0)
                    ),
                    isOdd = false
                )
            )

            add(
                PhotoTemplate(
                    id = TemplateId.E6,
                    displayName = "E6 (3×2)",
                    columns = 3, rows = 2,
                    // صف1 (يمين->يسار): 2,1,0  ثم صف2: 2,1,0
                    slots = listOf(
                        SlotSpec(0, row = 0, col = 2),
                        SlotSpec(1, row = 0, col = 1),
                        SlotSpec(2, row = 0, col = 0),
                        SlotSpec(3, row = 1, col = 2),
                        SlotSpec(4, row = 1, col = 1),
                        SlotSpec(5, row = 1, col = 0)
                    ),
                    isOdd = false
                )
            )

            add(
                PhotoTemplate(
                    id = TemplateId.E8,
                    displayName = "E8 (4×2)",
                    columns = 4, rows = 2,
                    slots = listOf(
                        // صف1 RTL: 3,2,1,0
                        SlotSpec(0, row = 0, col = 3),
                        SlotSpec(1, row = 0, col = 2),
                        SlotSpec(2, row = 0, col = 1),
                        SlotSpec(3, row = 0, col = 0),
                        // صف2 RTL: 3,2,1,0
                        SlotSpec(4, row = 1, col = 3),
                        SlotSpec(5, row = 1, col = 2),
                        SlotSpec(6, row = 1, col = 1),
                        SlotSpec(7, row = 1, col = 0)
                    ),
                    isOdd = false
                )
            )

            add(
                PhotoTemplate(
                    id = TemplateId.E12,
                    displayName = "E12 (4×3)",
                    columns = 4, rows = 3,
                    slots = listOf(
                        // صف1 RTL: 3,2,1,0
                        SlotSpec(0, row = 0, col = 3),
                        SlotSpec(1, row = 0, col = 2),
                        SlotSpec(2, row = 0, col = 1),
                        SlotSpec(3, row = 0, col = 0),
                        // صف2 RTL
                        SlotSpec(4, row = 1, col = 3),
                        SlotSpec(5, row = 1, col = 2),
                        SlotSpec(6, row = 1, col = 1),
                        SlotSpec(7, row = 1, col = 0),
                        // صف3 RTL
                        SlotSpec(8, row = 2, col = 3),
                        SlotSpec(9, row = 2, col = 2),
                        SlotSpec(10, row = 2, col = 1),
                        SlotSpec(11, row = 2, col = 0)
                    ),
                    isOdd = false
                )
            )

            // =====================
            // Odd Templates (صورة كبيرة بعرض كامل صف)
            // =====================
            add(
                PhotoTemplate(
                    id = TemplateId.O1,
                    displayName = "O1 (1 كبير)",
                    columns = 1, rows = 1,
                    slots = listOf(
                        SlotSpec(0, row = 0, col = 0, colSpan = 1)
                    ),
                    isOdd = true
                )
            )

            // O3-A: (صغير+صغير) ثم كبير بعرض كامل
            add(
                PhotoTemplate(
                    id = TemplateId.O3A,
                    displayName = "O3-A (2 صغار أعلى + كبير أسفل)",
                    columns = 2, rows = 2,
                    slots = listOf(
                        // صف1 RTL: يمين ثم يسار
                        SlotSpec(0, row = 0, col = 1),
                        SlotSpec(1, row = 0, col = 0),
                        // صف2: كبير بعرض كامل
                        SlotSpec(2, row = 1, col = 0, colSpan = 2)
                    ),
                    isOdd = true
                )
            )

            // O3-B: كبير أعلى ثم (صغير+صغير)
            add(
                PhotoTemplate(
                    id = TemplateId.O3B,
                    displayName = "O3-B (كبير أعلى + 2 صغار أسفل)",
                    columns = 2, rows = 2,
                    slots = listOf(
                        // صف1: كبير بعرض كامل
                        SlotSpec(0, row = 0, col = 0, colSpan = 2),
                        // صف2 RTL
                        SlotSpec(1, row = 1, col = 1),
                        SlotSpec(2, row = 1, col = 0)
                    ),
                    isOdd = true
                )
            )

            // O5-A: (2 صغار) + (2 صغار) + (كبير)
            add(
                PhotoTemplate(
                    id = TemplateId.O5A,
                    displayName = "O5-A (4 صغار أعلى/وسط + كبير أسفل)",
                    columns = 2, rows = 3,
                    slots = listOf(
                        // صف1 RTL
                        SlotSpec(0, row = 0, col = 1),
                        SlotSpec(1, row = 0, col = 0),
                        // صف2 RTL
                        SlotSpec(2, row = 1, col = 1),
                        SlotSpec(3, row = 1, col = 0),
                        // صف3: كبير بعرض كامل
                        SlotSpec(4, row = 2, col = 0, colSpan = 2)
                    ),
                    isOdd = true
                )
            )

            // O5-B: (كبير) + (2 صغار) + (2 صغار)
            add(
                PhotoTemplate(
                    id = TemplateId.O5B,
                    displayName = "O5-B (كبير أعلى + 4 صغار أسفل)",
                    columns = 2, rows = 3,
                    slots = listOf(
                        // صف1: كبير
                        SlotSpec(0, row = 0, col = 0, colSpan = 2),
                        // صف2 RTL
                        SlotSpec(1, row = 1, col = 1),
                        SlotSpec(2, row = 1, col = 0),
                        // صف3 RTL
                        SlotSpec(3, row = 2, col = 1),
                        SlotSpec(4, row = 2, col = 0)
                    ),
                    isOdd = true
                )
            )

            // O7-A: (3 صغار) + (3 صغار) + (كبير)
            add(
                PhotoTemplate(
                    id = TemplateId.O7A,
                    displayName = "O7-A (6 صغار أعلى/وسط + كبير أسفل)",
                    columns = 3, rows = 3,
                    slots = listOf(
                        // صف1 RTL: 2,1,0
                        SlotSpec(0, row = 0, col = 2),
                        SlotSpec(1, row = 0, col = 1),
                        SlotSpec(2, row = 0, col = 0),
                        // صف2 RTL
                        SlotSpec(3, row = 1, col = 2),
                        SlotSpec(4, row = 1, col = 1),
                        SlotSpec(5, row = 1, col = 0),
                        // صف3 كبير بعرض كامل (3 أعمدة)
                        SlotSpec(6, row = 2, col = 0, colSpan = 3)
                    ),
                    isOdd = true
                )
            )

            // O7-B: (كبير) + (3 صغار) + (3 صغار)
            add(
                PhotoTemplate(
                    id = TemplateId.O7B,
                    displayName = "O7-B (كبير أعلى + 6 صغار أسفل)",
                    columns = 3, rows = 3,
                    slots = listOf(
                        // صف1 كبير
                        SlotSpec(0, row = 0, col = 0, colSpan = 3),
                        // صف2 RTL
                        SlotSpec(1, row = 1, col = 2),
                        SlotSpec(2, row = 1, col = 1),
                        SlotSpec(3, row = 1, col = 0),
                        // صف3 RTL
                        SlotSpec(4, row = 2, col = 2),
                        SlotSpec(5, row = 2, col = 1),
                        SlotSpec(6, row = 2, col = 0)
                    ),
                    isOdd = true
                )
            )

            // O9-A: (4 صغار) + (4 صغار) + (كبير)
            add(
                PhotoTemplate(
                    id = TemplateId.O9A,
                    displayName = "O9-A (8 صغار أعلى/وسط + كبير أسفل)",
                    columns = 4, rows = 3,
                    slots = listOf(
                        // صف1 RTL: 3,2,1,0
                        SlotSpec(0, row = 0, col = 3),
                        SlotSpec(1, row = 0, col = 2),
                        SlotSpec(2, row = 0, col = 1),
                        SlotSpec(3, row = 0, col = 0),
                        // صف2 RTL
                        SlotSpec(4, row = 1, col = 3),
                        SlotSpec(5, row = 1, col = 2),
                        SlotSpec(6, row = 1, col = 1),
                        SlotSpec(7, row = 1, col = 0),
                        // صف3 كبير بعرض كامل (4 أعمدة)
                        SlotSpec(8, row = 2, col = 0, colSpan = 4)
                    ),
                    isOdd = true
                )
            )

            // O9-B: (كبير) + (4 صغار) + (4 صغار)
            add(
                PhotoTemplate(
                    id = TemplateId.O9B,
                    displayName = "O9-B (كبير أعلى + 8 صغار أسفل)",
                    columns = 4, rows = 3,
                    slots = listOf(
                        // صف1 كبير
                        SlotSpec(0, row = 0, col = 0, colSpan = 4),
                        // صف2 RTL
                        SlotSpec(1, row = 1, col = 3),
                        SlotSpec(2, row = 1, col = 2),
                        SlotSpec(3, row = 1, col = 1),
                        SlotSpec(4, row = 1, col = 0),
                        // صف3 RTL
                        SlotSpec(5, row = 2, col = 3),
                        SlotSpec(6, row = 2, col = 2),
                        SlotSpec(7, row = 2, col = 1),
                        SlotSpec(8, row = 2, col = 0)
                    ),
                    isOdd = true
                )
            )
        }
    }

    fun even(): List<PhotoTemplate> = all.filter { !it.isOdd }
    fun odd(): List<PhotoTemplate> = all.filter { it.isOdd }
    fun byId(id: TemplateId): PhotoTemplate = all.first { it.id == id }
}
