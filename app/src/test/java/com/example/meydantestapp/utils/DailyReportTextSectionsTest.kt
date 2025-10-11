package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReportTextSectionsTest {

    @Test
    fun `sanitizeAndMapViberText maps labeled arabic and english sections`() {
        val input = """
            Activities: تركيب القواعد الخرسانية
            الآليات والمعدات – الرافعة البرجية
            العوائق والتحديات: لا يوجد
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("تركيب القواعد الخرسانية", sections.activities)
        assertEquals("الرافعة البرجية", sections.machines)
        assertEquals("لا يوجد", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText strips english label variants with punctuation`() {
        val input = """
            Obstacles and Challenges : None reported
            Machines & Equipment - Tower crane
            Activities : Pouring concrete for slab
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("Pouring concrete for slab", sections.activities)
        assertEquals("Tower crane", sections.machines)
        assertEquals("None reported", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText handles bullet prefixed arabic labels`() {
        val input = """
            • الأنشطة: تجهيز الموقع النهائي
            - الآليات والمعدات : حفارة صغيرة
            * العوائق والتحديات: لا يوجد
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("تجهيز الموقع النهائي", sections.activities)
        assertEquals("حفارة صغيرة", sections.machines)
        assertEquals("لا يوجد", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText falls back to line order when unlabeled`() {
        val input = """
            صب الأعمدة للطابق الرابع
            معدات: خلاطات اسمنت إضافية
            توقف للعمل بسبب الأمطار
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("صب الأعمدة للطابق الرابع", sections.activities)
        assertEquals("خلاطات اسمنت إضافية", sections.machines)
        assertEquals("توقف للعمل بسبب الأمطار", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText keeps placeholders after stripping labels`() {
        val input = """
            Activities: —
            Machines and Equipment: لا يوجد
            Obstacles: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("—", sections.activities)
        assertEquals("لا يوجد", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `resolveDailyReportSections prefers explicit text and keeps placeholders empty`() {
        val sections = resolveDailyReportSections(
            activitiesList = listOf("  تركيب الألواح  "),
            machinesList = emptyList(),
            obstaclesList = null,
            activitiesText = "  الأنشطة اليومية  ",
            machinesText = null,
            obstaclesText = null,
            viberText = "معدات: رافعة صغيرة\nعوائق: نقص مواد"
        )

        assertEquals("الأنشطة اليومية", sections.activities)
        assertEquals("رافعة صغيرة", sections.machines)
        assertEquals("نقص مواد", sections.obstacles)
        assertTrue(sections.activities.isNotEmpty())
    }

    @Test
    fun `sanitizeAndMapViberText attaches arabic header bullet lines`() {
        val input = """
            الأنشطة
            - تجهيز الموقع
            • متابعة الأعمال
            الآليات والمعدات: —
            العوائق والتحديات: لا يوجد
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("تجهيز الموقع\nمتابعة الأعمال", sections.activities)
        assertEquals("—", sections.machines)
        assertEquals("لا يوجد", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText strips inline english label`() {
        val input = """
            Activities: Inspect formwork
            Machines: —
            Obstacles: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("Inspect formwork", sections.activities)
        assertEquals("—", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText removes mixed bullet prefixes`() {
        val input = """
            - Task one
              • Task two
            — مهمة ثالثة
            Machines: —
            Obstacles: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("Task one\nTask two\nمهمة ثالثة", sections.activities)
        assertEquals("—", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText preserves explicit placeholders even with extras`() {
        val input = """
            Activities: —
            Machines: لا يوجد
            Obstacles: —
            ملاحظة إضافية بدون تصنيف
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("—", sections.activities)
        assertEquals("لا يوجد", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText applies placeholder when header has no content`() {
        val input = """
            Activities: Completed tasks
            Machines:
            Obstacles:
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("Completed tasks", sections.activities)
        assertEquals("—", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText keeps mid sentence challenges text`() {
        val input = """
            تمت معالجة مختلف التحديات اليوم داخل الموقع
            معدات: حفارة
            عوائق: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("تمت معالجة مختلف التحديات اليوم داخل الموقع", sections.activities)
        assertEquals("حفارة", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText removes zero width noise`() {
        val input = """
            الأنشطة:‏ تجهيزـ الموقع
            الآليات والمعدات:‌ رافعةـ
            العوائق والتحديات: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("تجهيز الموقع", sections.activities)
        assertEquals("رافعة", sections.machines)
        assertEquals("—", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText maps mixed arabic english labels`() {
        val input = """
            Activities: Pouring slab
            الآليات والمعدات: مضخة خرسانة
            Obstacles and Challenges: Weather delay
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals("Pouring slab", sections.activities)
        assertEquals("مضخة خرسانة", sections.machines)
        assertEquals("Weather delay", sections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText handles empty or malformed input`() {
        val emptySections = sanitizeAndMapViberText("   ")
        val nullSections = sanitizeAndMapViberText(null)

        assertEquals("", emptySections.activities)
        assertEquals("", emptySections.machines)
        assertEquals("", emptySections.obstacles)

        assertEquals("", nullSections.activities)
        assertEquals("", nullSections.machines)
        assertEquals("", nullSections.obstacles)
    }

    @Test
    fun `sanitizeAndMapViberText keeps long paragraphs intact`() {
        val longText = buildString { repeat(1000) { append('a') } }
        val input = """
            Activities: $longText
            Machines: —
            Obstacles: —
        """.trimIndent()

        val sections = sanitizeAndMapViberText(input)

        assertEquals(longText, sections.activities)
        assertEquals("—", sections.machines)
        assertEquals("—", sections.obstacles)
    }
}
