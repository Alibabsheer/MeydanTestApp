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
}
