package com.karim.aiprojectmanager

import java.time.Instant
import kotlinx.coroutines.CancellationException

internal data class GitHubWorkflowRun(
    val id: Long,
    val name: String,
    val runNumber: Long,
    val status: String,
    val conclusion: String?,
    val createdAt: String,
    val updatedAt: String,
    val url: String
) {
    val state: GitHubWorkflowState
        get() = when (status.lowercase()) {
            "queued", "in_progress", "waiting", "requested", "pending" -> GitHubWorkflowState.PENDING
            "completed" -> when (conclusion?.lowercase()) {
                "success" -> GitHubWorkflowState.SUCCESS
                "failure", "cancelled", "timed_out", "action_required", "startup_failure" ->
                    GitHubWorkflowState.FAILURE
                else -> GitHubWorkflowState.UNKNOWN
            }
            else -> GitHubWorkflowState.UNKNOWN
        }
}

internal enum class GitHubWorkflowState { SUCCESS, FAILURE, PENDING, NO_WORKFLOWS, UNKNOWN }

internal data class GitHubWorkflowSnapshot(
    val totalCount: Int,
    val latest: GitHubWorkflowRun?
) {
    val state: GitHubWorkflowState
        get() = when {
            totalCount == 0 -> GitHubWorkflowState.NO_WORKFLOWS
            latest == null -> GitHubWorkflowState.UNKNOWN
            else -> latest.state
        }
}

internal data class GitHubIssue(
    val id: Long,
    val number: Long,
    val title: String,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
    val url: String
)

internal data class GitHubIssuesSnapshot(
    val openCount: Int,
    val closedCount: Int,
    val open: List<GitHubIssue>,
    val closed: List<GitHubIssue>
)

internal data class GitHubCommitComparison(val aheadBy: Int)

internal data class GitHubProjectSnapshot(
    val repository: GitHubRepository,
    val branch: GitHubBranchPreview,
    val workflows: GitHubWorkflowSnapshot,
    val issues: GitHubIssuesSnapshot,
    val refreshedAt: Long
)

internal data class GitHubSnapshotHistory(
    val previous: GitHubProjectSnapshot?,
    val current: GitHubProjectSnapshot,
    val newCommitCount: Int?
) {
    val changes: GitHubChangeSummary
        get() = summarizeGitHubChanges(previous, current, newCommitCount)
}

internal enum class GitHubMetadataField { NAME, DESCRIPTION, DEFAULT_BRANCH, VISIBILITY, ARCHIVED }
internal enum class GitHubIssueChangeKind { CREATED, CLOSED, REOPENED, UPDATED }

internal data class GitHubIssueChange(
    val number: Long,
    val title: String,
    val kind: GitHubIssueChangeKind,
    val previousState: String?,
    val currentState: String
)

internal data class GitHubWorkflowChange(
    val previousRunId: Long?,
    val currentRunId: Long?,
    val previousState: GitHubWorkflowState,
    val currentState: GitHubWorkflowState
)

internal data class GitHubChangeSummary(
    val hasPrevious: Boolean,
    val newCommitCount: Int?,
    val commitHeadChanged: Boolean,
    val metadataChanges: List<GitHubMetadataField>,
    val openIssueDelta: Int?,
    val closedIssueDelta: Int?,
    val issueChanges: List<GitHubIssueChange>,
    val workflowChange: GitHubWorkflowChange?
)

internal enum class GitHubRefreshSection { REPOSITORY, BRANCH, ACTIONS, ISSUES, HISTORY }

internal data class GitHubProjectRefreshResult(
    val repository: GitHubRepository?,
    val branch: GitHubBranchPreview?,
    val workflows: GitHubWorkflowSnapshot?,
    val issues: GitHubIssuesSnapshot?,
    val newCommitCount: Int?,
    val refreshedAt: Long,
    val errors: Map<GitHubRefreshSection, GitHubReadError>
) {
    val complete: Boolean
        get() = errors.isEmpty() && repository != null && branch != null &&
            workflows != null && issues != null &&
            (newCommitCount != null || branch.commitSha.isNotBlank())

    fun snapshotOrNull(): GitHubProjectSnapshot? {
        if (!complete) return null
        return GitHubProjectSnapshot(
            requireNotNull(repository),
            requireNotNull(branch),
            requireNotNull(workflows),
            requireNotNull(issues),
            refreshedAt
        )
    }
}

internal interface GitHubProjectRefreshSource : GitHubRepositoryPreviewSource {
    suspend fun repositoryById(repositoryId: Long): GitHubRepository
    suspend fun workflowSnapshot(repository: GitHubRepository): GitHubWorkflowSnapshot
    suspend fun issueSnapshot(repository: GitHubRepository): GitHubIssuesSnapshot
    suspend fun compareCommits(
        repository: GitHubRepository,
        baseSha: String,
        headSha: String
    ): GitHubCommitComparison
}

internal const val GITHUB_REFRESH_INTERVAL_MS = 15 * 60 * 1_000L

internal fun shouldAutomaticallyRefreshGitHubProject(lastAttemptAt: Long?, now: Long): Boolean =
    lastAttemptAt == null || now >= lastAttemptAt && now - lastAttemptAt >= GITHUB_REFRESH_INTERVAL_MS

internal suspend fun refreshGitHubProject(
    source: GitHubProjectRefreshSource,
    repositoryId: Long,
    previous: GitHubSnapshotHistory?,
    refreshedAt: Long
): GitHubProjectRefreshResult {
    val errors = mutableMapOf<GitHubRefreshSection, GitHubReadError>()
    val repository = try {
        source.repositoryById(repositoryId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: GitHubReadFailure) {
        errors[GitHubRefreshSection.REPOSITORY] = failure.reason
        return GitHubProjectRefreshResult(null, null, null, null, null, refreshedAt, errors)
    }

    if (repository.id != repositoryId) {
        return GitHubProjectRefreshResult(
            null, null, null, null, null, refreshedAt,
            mapOf(GitHubRefreshSection.REPOSITORY to GitHubReadError.INVALID_RESPONSE)
        )
    }

    val branch = captureRefreshSection(GitHubRefreshSection.BRANCH, errors) {
        source.branch(repository)
    }
    val workflows = captureRefreshSection(GitHubRefreshSection.ACTIONS, errors) {
        source.workflowSnapshot(repository)
    }
    val issues = captureRefreshSection(GitHubRefreshSection.ISSUES, errors) {
        source.issueSnapshot(repository)
    }

    val newCommitCount = when {
        branch == null -> null
        previous == null -> null
        previous.current.branch.commitSha == branch.commitSha -> 0
        else -> captureRefreshSection(GitHubRefreshSection.HISTORY, errors) {
            source.compareCommits(repository, previous.current.branch.commitSha, branch.commitSha).aheadBy
        }
    }
    return GitHubProjectRefreshResult(
        repository, branch, workflows, issues, newCommitCount, refreshedAt, errors
    )
}

private suspend fun <T> captureRefreshSection(
    section: GitHubRefreshSection,
    errors: MutableMap<GitHubRefreshSection, GitHubReadError>,
    read: suspend () -> T
): T? = try {
    read()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: GitHubReadFailure) {
    errors[section] = failure.reason
    null
}

internal fun summarizeGitHubChanges(
    previous: GitHubProjectSnapshot?,
    current: GitHubProjectSnapshot,
    newCommitCount: Int?
): GitHubChangeSummary {
    if (previous == null) {
        return GitHubChangeSummary(false, null, false, emptyList(), null, null, emptyList(), null)
    }
    val oldRepository = previous.repository
    val repository = current.repository
    val metadataChanges = buildList {
        if (oldRepository.fullName != repository.fullName) add(GitHubMetadataField.NAME)
        if (oldRepository.description != repository.description) add(GitHubMetadataField.DESCRIPTION)
        if (oldRepository.defaultBranch != repository.defaultBranch) add(GitHubMetadataField.DEFAULT_BRANCH)
        if (oldRepository.isPrivate != repository.isPrivate) add(GitHubMetadataField.VISIBILITY)
        if (oldRepository.archived != repository.archived) add(GitHubMetadataField.ARCHIVED)
    }
    val oldIssues = (previous.issues.open + previous.issues.closed).associateBy { it.number }
    val currentIssues = (current.issues.open + current.issues.closed).associateBy { it.number }
    val issueChanges = currentIssues.values.mapNotNull { issue ->
        val old = oldIssues[issue.number]
        when {
            old == null && issue.createdAt.toEpochMillis() > previous.refreshedAt ->
                GitHubIssueChange(issue.number, issue.title, GitHubIssueChangeKind.CREATED, null, issue.state)
            old != null && old.state != issue.state -> GitHubIssueChange(
                issue.number,
                issue.title,
                if (issue.state == "closed") GitHubIssueChangeKind.CLOSED else GitHubIssueChangeKind.REOPENED,
                old.state,
                issue.state
            )
            old != null && (old.title != issue.title || old.updatedAt != issue.updatedAt) ->
                GitHubIssueChange(issue.number, issue.title, GitHubIssueChangeKind.UPDATED, old.state, issue.state)
            else -> null
        }
    }
    val oldRun = previous.workflows.latest
    val currentRun = current.workflows.latest
    val workflowChange = if (
        oldRun?.id != currentRun?.id ||
        previous.workflows.state != current.workflows.state ||
        oldRun?.status != currentRun?.status ||
        oldRun?.conclusion != currentRun?.conclusion
    ) {
        GitHubWorkflowChange(
            oldRun?.id, currentRun?.id, previous.workflows.state, current.workflows.state
        )
    } else null
    return GitHubChangeSummary(
        hasPrevious = true,
        newCommitCount = newCommitCount,
        commitHeadChanged = previous.branch.commitSha != current.branch.commitSha,
        metadataChanges = metadataChanges,
        openIssueDelta = current.issues.openCount - previous.issues.openCount,
        closedIssueDelta = current.issues.closedCount - previous.issues.closedCount,
        issueChanges = issueChanges,
        workflowChange = workflowChange
    )
}

private fun String.toEpochMillis(): Long = try {
    Instant.parse(this).toEpochMilli()
} catch (_: Exception) {
    0L
}
