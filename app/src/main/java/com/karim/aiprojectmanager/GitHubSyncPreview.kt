package com.karim.aiprojectmanager

internal data class GitHubRemoteSnapshot(
    val repository: GitHubRepository,
    val head: String
)

internal enum class GitHubRemoteChange { METADATA, DEFAULT_BRANCH, HEAD }
internal enum class GitHubSyncDisposition { UNCHANGED, REMOTE_ONLY, LOCAL_ONLY, CONFLICT, UNLINKED }

internal data class GitHubSyncComparison(
    val changes: Set<GitHubRemoteChange>,
    val disposition: GitHubSyncDisposition
)

/** Compares snapshots for review only. A local project is linked by repository ID. */
internal fun compareGitHubSnapshots(
    baseline: GitHubRemoteSnapshot,
    current: GitHubRemoteSnapshot,
    localBaseline: List<ProjectItem>,
    localCurrent: List<ProjectItem>
): GitHubSyncComparison {
    val previous = baseline.repository
    val latest = current.repository
    if (previous.id != latest.id) return GitHubSyncComparison(emptySet(), GitHubSyncDisposition.CONFLICT)

    val changes = buildSet {
        if (previous.copy(defaultBranch = latest.defaultBranch) != latest) add(GitHubRemoteChange.METADATA)
        if (previous.defaultBranch != latest.defaultBranch) add(GitHubRemoteChange.DEFAULT_BRANCH)
        if (baseline.head != current.head) add(GitHubRemoteChange.HEAD)
    }
    val stableId = "github:${previous.id}"
    val before = localBaseline.filter { it.id == stableId }
    val after = localCurrent.filter { it.id == stableId }
    val duplicateCandidates = previewGitHubImport(latest, localCurrent)?.candidates.orEmpty()
    val disposition = when {
        before.size > 1 || after.size > 1 -> GitHubSyncDisposition.CONFLICT
        duplicateCandidates.any { it.id != stableId } -> GitHubSyncDisposition.CONFLICT
        before.isEmpty() && after.isEmpty() -> GitHubSyncDisposition.UNLINKED
        before.isEmpty() || after.isEmpty() -> GitHubSyncDisposition.CONFLICT
        before != after && changes.isNotEmpty() -> GitHubSyncDisposition.CONFLICT
        before != after -> GitHubSyncDisposition.LOCAL_ONLY
        changes.isNotEmpty() -> GitHubSyncDisposition.REMOTE_ONLY
        else -> GitHubSyncDisposition.UNCHANGED
    }
    return GitHubSyncComparison(changes, disposition)
}
