package com.karim.aiprojectmanager

internal data class GitHubChecklistEntry(val key: String, val title: String, val checked: Boolean)
internal data class GitHubChecklistPreview(val entries: List<GitHubChecklistEntry>) {
    val checkedCount: Int get() = entries.count { it.checked }
}

/** A checked repository box is a claim, not independent verification. */
internal fun parseGitHubChecklist(markdown: String, sourcePath: String = "PROJECT_CHECKLIST.md"): GitHubChecklistPreview {
    val entries = mutableListOf<GitHubChecklistEntry>()
    val pattern = Regex("""^\s*(?:[-*+] |\d+[.)] )\[([ xX])]\s+(.+?)\s*$""")
    var fenced = false
    var fenceChar = ' '
    var fenceLength = 0
    for ((index, line) in markdown.lineSequence().withIndex()) {
        val trimmed = line.trimStart()
        val first = trimmed.firstOrNull()
        if (first == '`' || first == '~') {
            val count = trimmed.takeWhile { it == first }.length
            if (count >= 3) {
                if (!fenced) { fenced = true; fenceChar = first; fenceLength = count }
                else if (first == fenceChar && count >= fenceLength) fenced = false
                continue
            }
        }
        if (fenced) continue
        val match = pattern.matchEntire(line) ?: continue
        val title = match.groupValues[2].trim()
        if (title.isEmpty()) continue
        if (entries.size >= 300) break
        entries.add(GitHubChecklistEntry(sourcePath + ":" + (index + 1), title.take(500), match.groupValues[1] != " "))
    }
    return GitHubChecklistPreview(entries)
}

/** Existing local titles and statuses are never overwritten. */
internal fun proposeGitHubChecklistTasks(localTasks: List<TaskItem>, remote: GitHubChecklistPreview): List<TaskItem> {
    val known = localTasks.map { it.title.trim().lowercase() }.toMutableSet()
    return remote.entries.mapNotNull { entry ->
        val title = entry.title.trim()
        if (title.isEmpty() || !known.add(title.lowercase())) null
        else TaskItem("github-checklist:" + entry.key, title,
            if (entry.checked) "IMPLEMENTED_UNVERIFIED" else "NOT_STARTED")
    }
}
