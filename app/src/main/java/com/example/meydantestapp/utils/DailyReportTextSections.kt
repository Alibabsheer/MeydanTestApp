package com.example.meydantestapp.utils

private enum class SectionKey { ACTIVITIES, MACHINES, OBSTACLES }

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
    val collectors = SectionKey.values().associateWith { SectionCollector() }
    val fallbackOrder = SectionKey.values().toMutableList()
    val expectingContent = mutableSetOf<SectionKey>()
    var currentSection: SectionKey? = null
    var currentSource = SectionSource.NONE

    normalized.split("\n").forEach { rawLine ->
        val collapsed = WHITESPACE_REGEX.replace(rawLine, " ")
        val bulletResult = stripBulletPrefix(collapsed)

        if (currentSource == SectionSource.BULLET && !bulletResult.hadBullet) {
            currentSection = null
            currentSource = SectionSource.NONE
        }

        val detectionTarget = bulletResult.text.trimStart()
        val labelMatch = detectSectionLabel(detectionTarget)
        if (labelMatch != null) {
            val collector = collectors.getValue(labelMatch.section)
            removeFromFallback(fallbackOrder, labelMatch.section)

            val inlineValue = labelMatch.inlineValue
            val sanitizedInline = inlineValue?.let { sanitizeLine(it) }
            if (!inlineValue.isNullOrBlank()) {
                sanitizedInline?.let { collector.appendLine(it) }
            }

            val inlineIsPlaceholder = sanitizedInline?.isPlaceholder == true
            val inlineHadBullet = sanitizedInline?.hadBullet == true
            val expectsMore = when {
                sanitizedInline == null -> true
                sanitizedInline.content == null -> true
                inlineHadBullet -> true
                inlineIsPlaceholder -> false
                else -> false
            }

            if (expectsMore) {
                expectingContent.add(labelMatch.section)
            } else {
                expectingContent.remove(labelMatch.section)
            }

            val nextSource = when {
                inlineHadBullet -> SectionSource.BULLET
                expectsMore -> SectionSource.HEADER
                else -> SectionSource.NONE
            }

            currentSection = if (nextSource == SectionSource.NONE) null else labelMatch.section
            currentSource = nextSource
            return@forEach
        }

        val sanitizedLine = sanitizeFromStripped(bulletResult)
        if (sanitizedLine.content == null) {
            currentSection?.let { collectors.getValue(it).appendBlank() }
            if (currentSource == SectionSource.BULLET) {
                currentSection = null
                currentSource = SectionSource.NONE
            }
            return@forEach
        }

        var activeSection = currentSection
        if (activeSection != null) {
            val collector = collectors.getValue(activeSection)
            val allowCurrent = when (currentSource) {
                SectionSource.HEADER -> expectingContent.contains(activeSection) || collector.hasRealContent()
                SectionSource.BULLET -> true
                else -> collector.hasRealContent()
            }
            if (!allowCurrent) {
                activeSection = null
                currentSection = null
                currentSource = SectionSource.NONE
            }
        }

        val targetSection = activeSection ?: nextFallbackSection(fallbackOrder, collectors)
        if (targetSection == null) {
            return@forEach
        }

        val collector = collectors.getValue(targetSection)
        collector.appendLine(sanitizedLine)
        if (sanitizedLine.content != null && !sanitizedLine.isPlaceholder) {
            expectingContent.remove(targetSection)
        }

        if (currentSection == null) {
            if (sanitizedLine.hadBullet) {
                currentSection = targetSection
                currentSource = SectionSource.BULLET
            } else {
                currentSection = null
                currentSource = SectionSource.NONE
            }
        }
    }

    return DailyReportTextSections(
        activities = collectors.getValue(SectionKey.ACTIVITIES).finalizeValue(),
        machines = collectors.getValue(SectionKey.MACHINES).finalizeValue(),
        obstacles = collectors.getValue(SectionKey.OBSTACLES).finalizeValue()
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
        if (isNotEmpty()) append("\n")
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
    val rawLines = normalized.split("\n")
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

private fun sanitizeLine(line: String): LineSanitization {
    if (line.isEmpty()) return LineSanitization(null, false, false)
    val collapsed = WHITESPACE_REGEX.replace(line, " ")
    val bulletResult = stripBulletPrefix(collapsed)
    return sanitizeFromStripped(bulletResult)
}

private fun sanitizeFromStripped(result: BulletStripResult): LineSanitization {
    val trimmed = result.text.trim(*TRIM_CHARS)
    if (trimmed.isEmpty()) {
        return LineSanitization(null, false, result.hadBullet)
    }
    val isPlaceholder = isPlaceholderText(trimmed)
    return LineSanitization(trimmed, isPlaceholder, result.hadBullet)
}

private fun detectSectionLabel(line: String): LabelMatch? {
    SectionKey.values().forEach { section ->
        val pattern = SECTION_LABEL_PATTERNS.getValue(section)
        val match = pattern.matchEntire(line)
        if (match != null) {
            val inlineValue = match.groupValues.getOrNull(1)
            return LabelMatch(section, inlineValue)
        }
    }
    return null
}

private fun nextFallbackSection(
    fallbackOrder: MutableList<SectionKey>,
    collectors: Map<SectionKey, SectionCollector>
): SectionKey? {
    val iterator = fallbackOrder.iterator()
    while (iterator.hasNext()) {
        val section = iterator.next()
        val collector = collectors.getValue(section)
        if (collector.canAcceptFallback()) {
            iterator.remove()
            return section
        }
        iterator.remove()
    }
    return collectors.entries.firstOrNull { it.value.canAcceptFallback() }?.key
}

private fun removeFromFallback(fallbackOrder: MutableList<SectionKey>, section: SectionKey) {
    val index = fallbackOrder.indexOf(section)
    if (index >= 0) {
        fallbackOrder.removeAt(index)
    }
}

private enum class SectionSource { NONE, HEADER, BULLET }

private data class LabelMatch(val section: SectionKey, val inlineValue: String?)

private data class BulletStripResult(val text: String, val hadBullet: Boolean)

private data class LineSanitization(
    val content: String?,
    val isPlaceholder: Boolean,
    val hadBullet: Boolean
)

private class SectionCollector {
    private val lines = mutableListOf<String>()
    private var hasRealContentFlag = false
    private var placeholder: String? = null
    private var lastWasBlank = false

    fun canAcceptFallback(): Boolean = !hasRealContentFlag && placeholder == null

    fun hasRealContent(): Boolean = hasRealContentFlag

    fun appendLine(line: LineSanitization) {
        when {
            line.content == null -> appendBlank()
            line.isPlaceholder && !hasRealContentFlag -> {
                if (placeholder == null) {
                    placeholder = line.content
                }
                lastWasBlank = false
            }
            else -> appendContent(line.content ?: return)
        }
    }

    fun appendBlank() {
        if (!hasRealContentFlag) return
        if (!lastWasBlank) {
            lines.add("")
            lastWasBlank = true
        }
    }

    fun finalizeValue(): String {
        if (hasRealContentFlag) {
            while (lines.firstOrNull()?.isEmpty() == true) {
                lines.removeAt(0)
            }
            while (lines.lastOrNull()?.isEmpty() == true) {
                lines.removeAt(lines.lastIndex)
            }
            if (lines.isEmpty()) {
                return placeholder ?: PLACEHOLDER_TEXT
            }
            return lines.joinToString(separator = "\n")
        }
        return placeholder ?: PLACEHOLDER_TEXT
    }

    private fun appendContent(value: String) {
        if (!hasRealContentFlag) {
            hasRealContentFlag = true
            placeholder = null
            lines.clear()
        }
        if (value.isEmpty()) {
            appendBlank()
            return
        }
        lines.add(value)
        lastWasBlank = false
    }
}

private fun stripBulletPrefix(text: String): BulletStripResult {
    val match = BULLET_PREFIX_REGEX.find(text)
    if (match == null || match.range.first != 0) {
        return BulletStripResult(text, false)
    }
    val prefix = match.value
    val hadBullet = prefix.any { !it.isWhitespace() }
    val stripped = text.substring(match.range.last + 1)
    return BulletStripResult(stripped, hadBullet)
}

private fun isPlaceholderText(value: String): Boolean {
    val trimmed = value.trim(*TRIM_CHARS)
    return trimmed == PLACEHOLDER_TEXT || trimmed == AR_PLACEHOLDER_TEXT
}

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

private val TRIM_CHARS = charArrayOf(' ', '\t', '-', '–', '—', ':', '：', '،', '؛')

private const val PLACEHOLDER_TEXT = "—"

private const val AR_PLACEHOLDER_TEXT = "لا يوجد"

private val WHITESPACE_REGEX = Regex("""\s+""")

private val BULLET_PREFIX_REGEX = Regex(
    """^[\s•◦▪‣·*\u2022\u25CF\u25A0\u25E6\u2219\u2023\u2043–—-]+"""
)

private val SECTION_LABEL_VARIANTS: Map<SectionKey, Set<String>> = mapOf(
    SectionKey.ACTIVITIES to setOf(
        "Activities",
        "Activity",
        "Project Activities",
        "Project Activity",
        "الأنشطة",
        "أنشطة",
        "نشاط",
        "نشاطات",
        "نشاطات المشروع",
        "أنشطة المشروع",
        "الأعمال",
        "الاعمال",
        "أعمال",
        "اعمال",
        "اعمال المشروع",
        "أعمال المشروع"
    ),
    SectionKey.MACHINES to setOf(
        "Machines",
        "Machine",
        "Machinery",
        "Equipment",
        "Machines & Equipment",
        "Machines and Equipment",
        "Equipment & Machines",
        "Equipment and Machines",
        "الآليات والمعدات",
        "المعدات والآليات",
        "الآلات والمعدات",
        "معدات",
        "الآليات",
        "آليات",
        "الآلات",
        "الات",
        "المعدات"
    ),
    SectionKey.OBSTACLES to setOf(
        "Obstacles",
        "Obstacle",
        "Obstacles & Challenges",
        "Obstacles and Challenges",
        "Challenges",
        "Challenge",
        "العوائق والتحديات",
        "العوائق",
        "المعوقات",
        "المعيقات",
        "التحديات",
        "الصعوبات"
    )
)

private val SECTION_LABEL_PATTERNS: Map<SectionKey, Regex> = SECTION_LABEL_VARIANTS.mapValues { (_, labels) ->
    val alternation = labels
        .map { Regex.escape(it.trim()) }
        .sortedByDescending { it.length }
        .joinToString(separator = "|")
    Regex(
        """^(?:$alternation)(?:\s*[：:؛\-–—]\s*)?(.*)$""",
        setOf(RegexOption.IGNORE_CASE)
    )
}

