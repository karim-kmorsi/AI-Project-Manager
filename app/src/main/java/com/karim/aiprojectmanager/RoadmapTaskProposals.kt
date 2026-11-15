package com.karim.aiprojectmanager

import java.util.Locale

internal data class RoadmapTaskProposal(
    val sourceLine: Int,
    val phase: String?,
    val title: String,
    val alreadyExists: Boolean
)

internal data class RoadmapTaskAnalysis(
    val proposals: List<RoadmapTaskProposal>,
    val ignoredAmbiguousEntries: Int,
    val ignoredCompletedEntries: Int,
    val duplicateEntries: Int,
    val notShownEntries: Int,
    val sourceTooLarge: Boolean = false
)

private data class RoadmapCandidate(val sourceLine: Int, val phase: String?, val title: String)

private const val MAX_ROADMAP_PROPOSALS = 200
private const val MAX_PROPOSAL_TITLE_CHARS = 300
private const val MAX_ROADMAP_SOURCE_CHARS = 1_000_000
private val headingLine = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private val listLine = Regex("^\\s{0,8}(?:[-*+]\\s+|\\d+[.)]\\s+)(?:\\[([ xX])\\]\\s*)?(.+?)\\s*$")
private val numberedSection = Regex("^\\d+(?:\\.\\d+)*[.)]?\\s+(.+)$")
private val phasePrefixes = listOf(
    "phase", "milestone", "sprint", "stage", "release", "iteration",
    "مرحلة", "معلم", "محطة", "إصدار", "دفعة"
)
private val taskVerbs = listOf(
    "add", "build", "create", "configure", "define", "deploy", "design", "develop",
    "disable", "document", "enable", "ensure", "establish", "fix", "handle",
    "implement", "improve", "integrate", "install", "launch", "measure", "migrate",
    "optimize", "prepare", "publish", "refactor", "remove", "replace", "research",
    "review", "secure", "set up", "support", "test", "track", "update", "validate",
    "verify", "write", "أضف", "أزل", "أصلح", "أنشئ", "ابن", "اكتب", "اختبر",
    "ادمج", "إضافة", "إزالة", "إعداد", "إصلاح", "إنشاء", "بناء", "تأكد",
    "تحقق", "تطوير", "توثيق", "حدّث", "حسّن", "راجع", "صمم", "نفّذ", "وثّق",
    "دعم", "دمج", "تنفيذ", "تهيئة", "اختبار", "تحديث", "تحسين", "مراجعة"
)

internal fun analyzeRoadmapTasks(roadmap: String, existingTasks: List<TaskItem>): RoadmapTaskAnalysis {
    if (roadmap.length > MAX_ROADMAP_SOURCE_CHARS) {
        return RoadmapTaskAnalysis(emptyList(), 0, 0, 0, 0, sourceTooLarge = true)
    }
    val existingTitles = existingTasks.mapTo(HashSet()) { normalizeRoadmapTaskTitle(it.title) }
    val seenTitles = HashSet<String>()
    val candidates = mutableListOf<RoadmapCandidate>()
    var phase: String? = null
    var phaseLevel: Int? = null
    var ignoredAmbiguous = 0
    var ignoredCompleted = 0
    var duplicates = 0

    fun addCandidate(line: Int, title: String) {
        val normalized = normalizeRoadmapTaskTitle(title)
        if (normalized.isEmpty() || title.length > MAX_PROPOSAL_TITLE_CHARS) {
            ignoredAmbiguous++
        } else if (!seenTitles.add(normalized)) {
            duplicates++
        } else {
            candidates += RoadmapCandidate(line, phase, title)
        }
    }

    roadmap.lineSequence().forEachIndexed { index, raw ->
        val heading = headingLine.matchEntire(raw)
        if (heading != null) {
            val level = heading.groupValues[1].length
            val text = cleanRoadmapMarkup(heading.groupValues[2])
            val numbered = numberedSection.matchEntire(text)?.groupValues?.get(1)
            when {
                isPhaseHeading(text) -> {
                    phase = text
                    phaseLevel = level
                }
                numbered != null && !isRoadmapAction(numbered) -> {
                    phase = text
                    phaseLevel = level
                }
                isRoadmapAction(numbered ?: text) -> {
                    addCandidate(index + 1, numbered ?: text)
                }
                phaseLevel != null && level <= phaseLevel!! -> {
                    phase = null
                    phaseLevel = null
                }
            }
            return@forEachIndexed
        }

        val list = listLine.matchEntire(raw) ?: return@forEachIndexed
        val checkbox = list.groupValues[1]
        val text = cleanRoadmapMarkup(list.groupValues[2])
        if (checkbox.equals("x", ignoreCase = true)) {
            ignoredCompleted++
            return@forEachIndexed
        }
        if (isPhaseHeading(text)) {
            phase = text
            phaseLevel = phaseLevel ?: 7
            return@forEachIndexed
        }
        if (checkbox.isNotEmpty() || isRoadmapAction(text)) addCandidate(index + 1, text)
        else ignoredAmbiguous++
    }

    val notShown = (candidates.size - MAX_ROADMAP_PROPOSALS).coerceAtLeast(0)
    val proposals = candidates.take(MAX_ROADMAP_PROPOSALS).map { candidate ->
        RoadmapTaskProposal(
            sourceLine = candidate.sourceLine,
            phase = candidate.phase,
            title = candidate.title,
            alreadyExists = normalizeRoadmapTaskTitle(candidate.title) in existingTitles
        )
    }
    return RoadmapTaskAnalysis(proposals, ignoredAmbiguous, ignoredCompleted, duplicates, notShown)
}

internal fun duplicateRoadmapTaskProposalIndexes(
    proposedTitles: List<String>,
    existingTitles: List<String>
): Set<Int> {
    val existing = existingTitles.mapTo(HashSet()) { normalizeRoadmapTaskTitle(it) }
    val normalized = proposedTitles.map(::normalizeRoadmapTaskTitle)
    val counts = normalized.filter(String::isNotEmpty).groupingBy { it }.eachCount()
    return normalized.indices.filterTo(HashSet()) { index ->
        normalized[index].isEmpty() || normalized[index] in existing || counts.getValue(normalized[index]) > 1
    }
}

private fun normalizeRoadmapTaskTitle(title: String): String =
    title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

private fun isPhaseHeading(text: String): Boolean {
    val normalized = text.trim().lowercase(Locale.ROOT)
    return phasePrefixes.any { prefix ->
        normalized == prefix || normalized.startsWith("$prefix ") ||
            normalized.startsWith("$prefix:") || normalized.startsWith("$prefix-")
    }
}

private fun isRoadmapAction(text: String): Boolean {
    var candidate = text.trim().lowercase(Locale.ROOT)
    candidate = numberedSection.matchEntire(candidate)?.groupValues?.get(1) ?: candidate
    val labeledAction = candidate.substringAfter(": ", candidate)
        .takeIf { candidate.startsWith("task: ") || candidate.startsWith("action: ") ||
            candidate.startsWith("deliverable: ") } ?: candidate
    return taskVerbs.any { verb ->
        labeledAction == verb || labeledAction.startsWith("$verb ") || labeledAction.startsWith("$verb:")
    }
}

private fun cleanRoadmapMarkup(text: String): String = text
    .replace(Regex("!?\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("<[^>]+>"), " ")
    .replace(Regex("[*_~`]+"), "")
    .replace(Regex("\\s+"), " ")
    .trim()
