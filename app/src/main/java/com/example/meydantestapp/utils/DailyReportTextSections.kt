package com.example.meydantestapp.utils

import java.util.Locale

data class DailyReportTextSections(
    val activities: String,
    val machines: String,
    val obstacles: String
) {
    fun fillMissingFrom(other: DailyReportTextSections): DailyReportTextSections = DailyReportTextSections(
        activities = if (activities.isNotBlank()) activities else other.activities,
        machines = if (machines.isNotBlank()) machines else other.machines,
        obstacles = if (obstacles.isNotBlank()) obstacles else other.obstacles
    )

    companion object {
        val EMPTY = DailyReportTextSections("", "", "")
    }
}

fun sanitizeAndMapViberText(raw: String?): DailyReportTextSections {
    if (raw.isNullOrBlank()) return DailyReportTextSections.EMPTY

    val normalized = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .map { ch ->
            when {
                ch == '\n' -> '\n'
                ch == '\t' -> ' '
                ch.code < 32 || ch.code in 127..159 -> ' '
                ch == '\u00A0' || ch == '\u202F' -> ' '
                ch == '\u200F' || ch == '\u200E' -> ' '
                else -> ch
            }
        }
        .joinToString(separator = "")

    val lines = normalized
        .split('\n')
        .map { line ->
            line
                .replace(BULLET_PREFIX_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
        }
        .filter { it.isNotEmpty() }

    if (lines.isEmpty()) return DailyReportTextSections.EMPTY

    val assigned = mutableMapOf<SectionKey, String>()
    val unlabeled = ArrayDeque<String>()

    lines.forEach { line ->
        val labeled = extractLabeledSection(line)
        if (labeled != null) {
            val (section, value) = labeled
            if (value.isNotEmpty() && assigned[section].isNullOrEmpty()) {
                assigned[section] = value
            }
        } else {
            unlabeled += line
        }
    }

    SectionKey.values().forEach { section ->
        if (assigned[section].isNullOrEmpty()) {
            val fallback = unlabeled.removeFirstOrNull()
            if (!fallback.isNullOrEmpty()) {
                assigned[section] = normalizeSectionValue(fallback)
            } else {
                assigned[section] = normalizeSectionValue("")
            }
        }
    }

    return DailyReportTextSections(
        activities = assigned[SectionKey.ACTIVITIES] ?: normalizeSectionValue(""),
        machines = assigned[SectionKey.MACHINES] ?: normalizeSectionValue(""),
        obstacles = assigned[SectionKey.OBSTACLES] ?: normalizeSectionValue("")
    )
}

fun resolveDailyReportSections(
    activitiesList: List<String>?,
    machinesList: List<String>?,
    obstaclesList: List<String>?,
    activitiesText: String? = null,
    machinesText: String? = null,
    obstaclesText: String? = null,
    viberText: String? = null
): DailyReportTextSections {
    val activitiesValue = sanitizeFreeText(activitiesText).ifBlank { sanitizeList(activitiesList) }
    val machinesValue = sanitizeFreeText(machinesText).ifBlank { sanitizeList(machinesList) }
    val obstaclesValue = sanitizeFreeText(obstaclesText).ifBlank { sanitizeList(obstaclesList) }

    var result = DailyReportTextSections(
        activities = activitiesValue,
        machines = machinesValue,
        obstacles = obstaclesValue
    )

    val needsFallback = result.activities.isBlank() || result.machines.isBlank() || result.obstacles.isBlank()
    if (!needsFallback) {
        return result
    }

    val fallbackRaw = viberText?.takeIf { it.isNotBlank() } ?: buildString {
        if (result.activities.isBlank()) appendJoined(activitiesList)
        if (result.machines.isBlank()) appendJoined(machinesList)
        if (result.obstacles.isBlank()) appendJoined(obstaclesList)
    }.takeIf { it.isNotBlank() }

    if (!fallbackRaw.isNullOrBlank()) {
        result = result.fillMissingFrom(sanitizeAndMapViberText(fallbackRaw))
    }

    return result
}

private fun StringBuilder.appendJoined(values: List<String>?) {
    if (values.isNullOrEmpty()) return
    val normalized = sanitizeList(values)
    if (normalized.isNotEmpty()) {
        if (isNotEmpty()) append('\n')
        append(normalized)
    }
}

private fun sanitizeFreeText(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return sanitizeLines(value)
}

private fun sanitizeList(values: List<String>?): String {
    if (values.isNullOrEmpty()) return ""
    val sanitized = values.mapNotNull { sanitizeLines(it).takeIf { text -> text.isNotEmpty() } }
    if (sanitized.isEmpty()) return ""
    return sanitized.joinToString(separator = "\n")
}

private fun sanitizeLines(value: String): String {
    val cleaned = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .map { ch ->
            when {
                ch == '\n' -> '\n'
                ch == '\t' -> ' '
                ch.code < 32 || ch.code in 127..159 -> ' '
                ch == '\u00A0' || ch == '\u202F' -> ' '
                ch == '\u200F' || ch == '\u200E' -> ' '
                ch == '\u0640' -> ' '
                else -> ch
            }
        }
        .joinToString(separator = "")

    return cleaned
        .split('\n')
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "\n")
}

private fun extractLabeledSection(line: String): Pair<SectionKey, String>? {
    SECTION_LABEL_STRIP_PATTERNS.forEach { (section, pattern) ->
        val match = pattern.matchEntire(line)
        if (match != null) {
            val value = normalizeSectionValue(match.groupValues.getOrNull(1).orEmpty())
            return section to value
        }
    }

    val separatorIndex = findSeparatorIndex(line)
    if (separatorIndex <= 0 || separatorIndex >= line.length - 1) return null

    val labelPart = line.substring(0, separatorIndex).trim(*TRIM_CHARS)
    val valuePart = normalizeSectionValue(line.substring(separatorIndex + 1))
    if (labelPart.isEmpty() || valuePart.isEmpty()) return null

    val canonical = canonicalize(labelPart)
    val section = SECTION_KEYWORDS.entries.firstOrNull { (_, keywords) -> canonical in keywords }?.key
        ?: return null

    return section to valuePart
}

private fun canonicalize(input: String): String {
    return input
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{Punct}\\u00A0\\u202F\\u200F\\u200E]+"), "")
        .replace("\u0640", "")
}

private fun findSeparatorIndex(text: String): Int {
    text.forEachIndexed { index, ch ->
        if (ch in SEPARATOR_CHARS) {
            return index
        }
    }
    return -1
}

private enum class SectionKey { ACTIVITIES, MACHINES, OBSTACLES }

private val TRIM_CHARS = charArrayOf(' ', '\t', '-', '–', '—', ':', '：', '،')

private val TRIM_CHAR_SET = TRIM_CHARS.toSet()

private val SEPARATOR_CHARS = charArrayOf(':', '：', '-', '–', '—', '﹘', '﹣', '‒', '−', '،')

private const val PLACEHOLDER_TEXT = "—"

private const val PLACEHOLDER_CHAR = '—'

private val WHITESPACE_REGEX = Regex("""\s+""")

private val BULLET_PREFIX_REGEX = Regex(
    """^[\s•◦▪‣·*\u2022\u25CF\u25A0\u25E6\u2219\u2023\u2043\u2219–—-]+"""
)

private val SECTION_LABEL_VARIANTS: Map<SectionKey, Set<String>> = mapOf(
    SectionKey.ACTIVITIES to buildSet {
        addAll(
            listOf(
                "Activities",
                "Activity",
                "Project Activities",
                "Project Activity"
            )
        )
        addAll(
            listOf(
                "الأنشطة",
                "أنشطة",
                "نشاطات",
                "نشاطات المشروع",
                "أنشطة المشروع",
                "الأعمال",
                "الاعمال",
                "أعمال",
                "اعمال",
                "اعمال المشروع",
                "أعمال المشروع"
            )
        )
    },
    SectionKey.MACHINES to buildSet {
        addAll(
            listOf(
                "Machines",
                "Machinery",
                "Equipment",
                "Machines & Equipment",
                "Equipment & Machines",
                "Machines and Equipment",
                "Equipment and Machines"
            )
        )
        addAll(
            listOf(
                "الآليات والمعدات",
                "معدات وآليات",
                "المعدات والآليات",
                "الآلات والمعدات",
                "الآليات",
                "آليات",
                "الآلات",
                "الات",
                "المعدات",
                "معدات"
            )
        )
    },
    SectionKey.OBSTACLES to buildSet {
        addAll(
            listOf(
                "Obstacles",
                "Obstacles & Challenges",
                "Obstacles and Challenges",
                "Challenges"
            )
        )
        addAll(
            listOf(
                "العوائق والتحديات",
                "العوائق",
                "المعوقات",
                "المعيقات",
                "التحديات",
                "الصعوبات"
            )
        )
    }
)

private val SECTION_LABEL_STRIP_PATTERNS: Map<SectionKey, Regex> = SECTION_LABEL_VARIANTS.mapValues { (_, labels) ->
    val alternation = labels
        .map { Regex.escape(it.trim()) }
        .sortedByDescending { it.length }
        .joinToString(separator = "|")
    Regex(
        """^\s*(?:$alternation)\s*(?:[:：\-–—﹘﹣‒−،]\s*)?(.*)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
}

private val SECTION_KEYWORDS: Map<SectionKey, Set<String>> = mapOf(
    SectionKey.ACTIVITIES to (
        setOf(
            "activities",
            "activity",
            "projectactivities",
            "projectactivity",
            "نشاطات",
            "نشاطاتالمشروع",
            "انشطة",
            "الانشطة",
            "الأعمال",
            "الاعمال",
            "اعمال",
            "اعمالالمشروع",
            "الأنشطة",
            "أنشطة",
            "أنشطةالمشروع"
        ) + SECTION_LABEL_VARIANTS.getValue(SectionKey.ACTIVITIES)
    ).map { canonicalize(it) }.toSet(),
    SectionKey.MACHINES to (
        setOf(
            "machines",
            "machinery",
            "equipment",
            "machinesequipment",
            "machinesandequipment",
            "equipmentandmachines",
            "الالات",
            "الات",
            "الآلات",
            "آلات",
            "المعدات",
            "معدات",
            "الالاتوالمعدات",
            "الاتوالمعدات",
            "الآلاتوالمعدات"
        ) + SECTION_LABEL_VARIANTS.getValue(SectionKey.MACHINES)
    ).map { canonicalize(it) }.toSet(),
    SectionKey.OBSTACLES to (
        setOf(
            "obstacles",
            "issues",
            "challenges",
            "problems",
            "العوائق",
            "عوائق",
            "المعوقات",
            "معوقات",
            "التحديات",
            "تحديات",
            "الصعوبات",
            "صعوبات"
        ) + SECTION_LABEL_VARIANTS.getValue(SectionKey.OBSTACLES)
    ).map { canonicalize(it) }.toSet()
)

private fun normalizeSectionValue(raw: String): String {
    if (raw.isEmpty()) return PLACEHOLDER_TEXT

    val collapsed = raw.replace(WHITESPACE_REGEX, " ")
    val trimmed = collapsed.trim { ch ->
        ch.isWhitespace() || (ch in TRIM_CHAR_SET && ch != PLACEHOLDER_CHAR)
    }

    if (trimmed.isNotEmpty()) {
        return trimmed
    }

    return PLACEHOLDER_TEXT
}
