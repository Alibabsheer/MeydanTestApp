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

    val normalized = normalizeCharacters(raw)
    val rawLines = normalized.split('\n')

    val accumulators = SectionKey.values().associateWith { SectionAccumulator() }
    val expectContentAfterLabel = mutableSetOf<SectionKey>()
    var activeSection: SectionKey? = null

    rawLines.forEach { rawLine ->
        val collapsed = WHITESPACE_REGEX.replace(rawLine, " ")
        val detectionCandidate = prepareForLabelDetection(collapsed)
        val labeled = extractLabeledSection(detectionCandidate)
        if (labeled != null) {
            val (section, valueRaw) = labeled
            val accumulator = accumulators.getValue(section)
            if (!accumulator.hasRealContent()) {
                accumulator.reset()
            }
            val sanitizedValue = sanitizeLines(valueRaw)
            if (sanitizedValue.isNotEmpty()) {
                accumulator.appendValue(sanitizedValue)
                expectContentAfterLabel.remove(section)
            }
            if (sanitizedValue.isEmpty()) {
                expectContentAfterLabel.add(section)
            }
            activeSection = section
            return@forEach
        }

        val sanitizedLine = sanitizeLine(collapsed)
        if (sanitizedLine.content == null) {
            activeSection?.let { accumulators.getValue(it).appendLine(sanitizedLine) }
            return@forEach
        }

        val currentSection = activeSection
        if (currentSection != null) {
            val accumulator = accumulators.getValue(currentSection)
            val expecting = expectContentAfterLabel.contains(currentSection)
            val allow = expecting || accumulator.hasRealContent() || sanitizedLine.hadBullet
            if (allow) {
                accumulator.appendLine(sanitizedLine)
                if (expecting) {
                    expectContentAfterLabel.remove(currentSection)
                }
                return@forEach
            }
            activeSection = null
        }

        val fallbackSection = findFallbackSection(accumulators)
        if (fallbackSection != null) {
            val fallbackAccumulator = accumulators.getValue(fallbackSection)
            fallbackAccumulator.appendLine(sanitizedLine)
            expectContentAfterLabel.remove(fallbackSection)
            activeSection = if (sanitizedLine.hadBullet) fallbackSection else null
        }
    }

    return DailyReportTextSections(
        activities = accumulators.getValue(SectionKey.ACTIVITIES).finalizeValue(),
        machines = accumulators.getValue(SectionKey.MACHINES).finalizeValue(),
        obstacles = accumulators.getValue(SectionKey.OBSTACLES).finalizeValue()
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
    if (value.isEmpty()) return ""
    val normalized = normalizeCharacters(value)
    val rawLines = normalized.split('\n')
    if (rawLines.isEmpty()) return ""

    val sanitized = mutableListOf<String>()
    var previousBlank = false
    rawLines.forEach { rawLine ->
        val sanitizedLine = sanitizeLine(rawLine)
        when {
            sanitizedLine.content == null -> {
                if (sanitized.isNotEmpty() && !previousBlank) {
                    sanitized += ""
                    previousBlank = true
                }
            }
            else -> {
                sanitized += sanitizedLine.content
                previousBlank = false
            }
        }
    }

    while (sanitized.firstOrNull()?.isEmpty() == true) {
        sanitized.removeAt(0)
    }
    while (sanitized.lastOrNull()?.isEmpty() == true) {
        sanitized.removeAt(sanitized.lastIndex)
    }

    if (sanitized.isEmpty()) {
        return ""
    }

    return sanitized.joinToString(separator = "\n")
}

private fun extractLabeledSection(line: String): Pair<SectionKey, String>? {
    SECTION_LABEL_STRIP_PATTERNS.forEach { (section, pattern) ->
        val match = pattern.matchEntire(line)
        if (match != null) {
            val value = match.groupValues.getOrNull(1).orEmpty()
            return section to value
        }
    }

    val separatorIndex = findSeparatorIndex(line)
    if (separatorIndex <= 0 || separatorIndex >= line.length - 1) return null

    val labelPart = line.substring(0, separatorIndex).trim(*TRIM_CHARS)
    val valuePart = line.substring(separatorIndex + 1)
    if (labelPart.isEmpty() || valuePart.isEmpty()) return null

    val canonical = canonicalize(labelPart)
    val section = SECTION_KEYWORDS.entries.firstOrNull { (_, keywords) -> canonical in keywords }?.key
        ?: return null

    return section to valuePart
}

private fun canonicalize(input: String): String {
    return normalizeCharacters(input)
        .lowercase(Locale.ROOT)
        .replace(ZERO_WIDTH_AND_TATWEEL_REGEX, "")
        .replace(Regex("[\\s\\p{Punct}\\u00A0\\u202F]+"), "")
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

private val TRIM_CHARS = charArrayOf(' ', '\t', '-', '–', '—', ':', '：', '،', '؛')

private val SEPARATOR_CHARS = charArrayOf(':', '：', '-', '–', '—', '﹘', '﹣', '‒', '−', '،', '؛')

private const val PLACEHOLDER_TEXT = "—"

private const val AR_PLACEHOLDER_TEXT = "لا يوجد"

private val WHITESPACE_REGEX = Regex("""\s+""")

private val BULLET_PREFIX_CHARS = setOf(
    '•', '◦', '▪', '‣', '·', '*', '\u2022', '\u25CF', '\u25A0', '\u25E6', '\u2219', '\u2023', '\u2043', '-', '–', '—', '﹘', '﹣', '‒', '−'
)

private val ZERO_WIDTH_AND_TATWEEL_REGEX = Regex("""[\u200C\u200D\u200E\u200F\u0640]""")

private fun normalizeCharacters(value: String): String {
    if (value.isEmpty()) return ""
    val prepared = value.replace("\r\n", "\n").replace('\r', '\n')
    val builder = StringBuilder(prepared.length)
    prepared.forEach { ch ->
        when {
            ch == '\n' -> builder.append('\n')
            ch == '\t' -> builder.append(' ')
            ch == '\u00A0' || ch == '\u202F' -> builder.append(' ')
            ch == '\u200C' || ch == '\u200D' || ch == '\u200E' || ch == '\u200F' -> {}
            ch == '\u0640' -> {}
            ch.code < 32 || ch.code in 127..159 -> builder.append(' ')
            else -> builder.append(ch)
        }
    }
    return builder.toString()
}

private fun prepareForLabelDetection(line: String): String {
    return stripBulletPrefix(line).text.trimStart()
}

private fun sanitizeLine(line: String): LineSanitization {
    if (line.isEmpty()) return LineSanitization(null, false, false)
    val collapsed = WHITESPACE_REGEX.replace(line, " ")
    val withoutPrefix = stripBulletPrefix(collapsed)
    val trimmed = withoutPrefix.text.trim()
    if (trimmed.isEmpty()) {
        return LineSanitization(null, false, withoutPrefix.removed)
    }
    val isPlaceholder = isPlaceholderText(trimmed)
    return LineSanitization(trimmed, isPlaceholder, withoutPrefix.removed)
}

private data class BulletStripResult(val text: String, val removed: Boolean)

private fun stripBulletPrefix(text: String): BulletStripResult {
    var index = 0
    val length = text.length
    var removed = false
    while (index < length) {
        val ch = text[index]
        if (ch == ' ' || ch == '\t') {
            index++
            continue
        }
        if (ch in BULLET_PREFIX_CHARS) {
            index++
            removed = true
            continue
        }
        break
    }
    return if (index == 0) {
        BulletStripResult(text, removed)
    } else {
        BulletStripResult(text.substring(index), removed)
    }
}

private fun isPlaceholderText(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed == PLACEHOLDER_TEXT || trimmed == AR_PLACEHOLDER_TEXT
}

private fun stripSectionLabel(section: SectionKey, value: String): String {
    if (value.isEmpty()) return value
    val lines = value.split('\n')
    if (lines.isEmpty()) return value
    val pattern = SECTION_LABEL_STRIP_PATTERNS[section] ?: return value
    val first = lines.first()
    val match = pattern.matchEntire(first)
    if (match != null) {
        val remainderValue = sanitizeLines(match.groupValues.getOrNull(1).orEmpty())
        val remainderLines = lines.drop(1).toMutableList()
        while (remainderLines.firstOrNull()?.isEmpty() == true) {
            remainderLines.removeAt(0)
        }
        val combined = mutableListOf<String>()
        if (remainderValue.isNotEmpty()) {
            val shouldSkipPlaceholder = isPlaceholderText(remainderValue) && remainderLines.any { it.isNotEmpty() }
            if (!shouldSkipPlaceholder) {
                combined.add(remainderValue)
            }
        }
        combined.addAll(remainderLines)
        return combined.joinToString(separator = "\n").trim('\n')
    }
    return value
}

private data class LineSanitization(
    val content: String?,
    val isPlaceholder: Boolean,
    val hadBullet: Boolean
)

private class SectionAccumulator {
    private val lines = mutableListOf<String>()
    private var placeholder: String? = null
    private var realContent: Boolean = false

    fun reset() {
        lines.clear()
        placeholder = null
        realContent = false
    }

    fun hasRealContent(): Boolean = realContent

    fun hasExplicitPlaceholder(): Boolean = placeholder != null

    fun shouldAcceptFallback(): Boolean = !realContent && placeholder == null

    fun appendValue(value: String) {
        if (value.isEmpty()) return
        value.split('\n').forEach { line ->
            when {
                line.isEmpty() -> appendBlank()
                isPlaceholderText(line) && !realContent -> setPlaceholderIfAbsent(line)
                else -> appendContent(line)
            }
        }
    }

    fun appendLine(line: LineSanitization) {
        when {
            line.content == null -> appendBlank()
            line.isPlaceholder && !realContent -> setPlaceholderIfAbsent(line.content)
            else -> appendContent(line.content ?: return)
        }
    }

    fun finalizeValue(): String {
        if (realContent) {
            while (lines.lastOrNull()?.isEmpty() == true) {
                lines.removeAt(lines.lastIndex)
            }
            return lines.joinToString(separator = "\n")
        }
        return placeholder ?: PLACEHOLDER_TEXT
    }

    private fun appendContent(value: String) {
        if (!realContent) {
            lines.clear()
            placeholder = null
            realContent = true
        }
        lines.add(value)
    }

    private fun appendBlank() {
        if (!realContent) return
        if (lines.isEmpty() || lines.last().isEmpty()) return
        lines.add("")
    }

    private fun setPlaceholderIfAbsent(value: String) {
        if (placeholder == null) {
            placeholder = value
        }
    }
}

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
        """^\s*(?:$alternation)\s*(?:[:：\-–—﹘﹣‒−،؛]\s*)?(.*)$""",
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
    val sanitized = sanitizeLines(raw)
    if (sanitized.isEmpty()) return PLACEHOLDER_TEXT
    if (isPlaceholderText(sanitized)) return sanitized
    return sanitized
}

private fun normalizeFallbackValue(section: SectionKey, raw: String): String {
    val sanitized = sanitizeLines(raw)
    if (sanitized.isEmpty()) return PLACEHOLDER_TEXT
    val stripped = stripSectionLabel(section, sanitized)
    if (stripped.isEmpty()) return PLACEHOLDER_TEXT
    if (isPlaceholderText(stripped)) return stripped
    return stripped
}

private fun findFallbackSection(accumulators: Map<SectionKey, SectionAccumulator>): SectionKey? {
    SectionKey.values().forEach { section ->
        val accumulator = accumulators.getValue(section)
        if (accumulator.shouldAcceptFallback()) {
            return section
        }
    }
    return null
}
