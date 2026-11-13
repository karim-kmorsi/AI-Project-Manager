package com.karim.aiprojectmanager

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubProjectRefreshTest {
    @Test fun summarizesCommitMetadataIssueAndWorkflowChangesWithoutInferringProgress() {
        val previous = snapshot(
            repository = GitHubRepository(42, "old-owner/repo", "Old", "main", false, false),
            issues = GitHubIssuesSnapshot(1, 0, listOf(issue(7, "open", "Old title", 1_000)), emptyList()),
            workflows = GitHubWorkflowSnapshot(1, run(100, "completed", "success"))
        )
        val current = snapshot(
            repository = GitHubRepository(42, "new-owner/repo", "New", "trunk", true, false),
            issues = GitHubIssuesSnapshot(
                1, 1,
                listOf(issue(8, "open", "Added", 2_000)),
                listOf(issue(7, "closed", "Old title", 1_000))
            ),
            workflows = GitHubWorkflowSnapshot(2, run(101, "completed", "failure"))
        ).copy(branch = GitHubBranchPreview(
            "trunk", false, "c".repeat(40), "d".repeat(40), "Commit", null
        ))

        val changes = summarizeGitHubChanges(previous, current, newCommitCount = 3)

        assertTrue(changes.hasPrevious)
        assertEquals(3, changes.newCommitCount)
        assertTrue(changes.commitHeadChanged)
        assertEquals(
            listOf(
                GitHubMetadataField.NAME,
                GitHubMetadataField.DESCRIPTION,
                GitHubMetadataField.DEFAULT_BRANCH,
                GitHubMetadataField.VISIBILITY
            ),
            changes.metadataChanges
        )
        assertEquals(0, changes.openIssueDelta)
        assertEquals(1, changes.closedIssueDelta)
        assertEquals(
            listOf(GitHubIssueChangeKind.CREATED, GitHubIssueChangeKind.CLOSED),
            changes.issueChanges.map { it.kind }
        )
        assertEquals(GitHubWorkflowState.FAILURE, changes.workflowChange?.currentState)
    }

    @Test fun incompleteRefreshKeepsSuccessfulSectionsButCannotProduceANewBaseline() = runBlocking {
        val repository = GitHubRepository(42, "owner/repo", null, "main", false, false)
        val previous = GitHubSnapshotHistory(null, snapshot(), null)
        val source = RefreshSource(
            repository = repository,
            workflows = { throw GitHubReadFailure(GitHubReadError.FORBIDDEN) }
        )

        val result = refreshGitHubProject(source, 42, previous, 3_000)

        assertEquals(GitHubReadError.FORBIDDEN, result.errors[GitHubRefreshSection.ACTIONS])
        assertTrue(result.branch != null)
        assertTrue(result.issues != null)
        assertNull(result.workflows)
        assertNull(result.snapshotOrNull())
        assertEquals(1_000L, previous.current.refreshedAt)
    }

    @Test fun automaticRefreshIsThrottledFromEveryRecordedAttempt() {
        val now = 100_000_000L
        assertTrue(shouldAutomaticallyRefreshGitHubProject(null, now))
        assertFalse(shouldAutomaticallyRefreshGitHubProject(now - GITHUB_REFRESH_INTERVAL_MS + 1, now))
        assertTrue(shouldAutomaticallyRefreshGitHubProject(now - GITHUB_REFRESH_INTERVAL_MS, now))
        assertFalse(shouldAutomaticallyRefreshGitHubProject(now + 1, now))
    }

    private fun snapshot(
        repository: GitHubRepository = GitHubRepository(42, "owner/repo", null, "main", false, false),
        issues: GitHubIssuesSnapshot = GitHubIssuesSnapshot(0, 0, emptyList(), emptyList()),
        workflows: GitHubWorkflowSnapshot = GitHubWorkflowSnapshot(0, null)
    ) = GitHubProjectSnapshot(
        repository,
        GitHubBranchPreview("main", false, "a".repeat(40), "b".repeat(40), "Commit", null),
        workflows,
        issues,
        1_000
    )

    private fun issue(number: Long, state: String, title: String, createdAt: Long) = GitHubIssue(
        id = number,
        number = number,
        title = title,
        state = state,
        createdAt = java.time.Instant.ofEpochMilli(createdAt).toString(),
        updatedAt = java.time.Instant.ofEpochMilli(createdAt).toString(),
        url = "https://github.com/owner/repo/issues/$number"
    )

    private fun run(id: Long, status: String, conclusion: String?) = GitHubWorkflowRun(
        id, "CI", id, status, conclusion, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
        "https://github.com/owner/repo/actions/runs/$id"
    )

    private class RefreshSource(
        private val repository: GitHubRepository,
        private val workflows: suspend () -> GitHubWorkflowSnapshot = { GitHubWorkflowSnapshot(0, null) }
    ) : GitHubProjectRefreshSource {
        override suspend fun repositoryById(repositoryId: Long) = repository
        override suspend fun branch(repository: GitHubRepository) =
            GitHubBranchPreview("main", false, "a".repeat(40), "b".repeat(40), "Commit", null)
        override suspend fun snapshot(repository: GitHubRepository) = GitHubRemoteSnapshot(repository, "a".repeat(40))
        override suspend fun readme(repository: GitHubRepository, commitSha: String) = GitHubReadmePreview("", false)
        override suspend fun tree(repository: GitHubRepository, treeSha: String) = GitHubTreePreview(emptyList(), false)
        override suspend fun workflowSnapshot(repository: GitHubRepository) = workflows()
        override suspend fun issueSnapshot(repository: GitHubRepository) = GitHubIssuesSnapshot(0, 0, emptyList(), emptyList())
        override suspend fun compareCommits(repository: GitHubRepository, baseSha: String, headSha: String) =
            GitHubCommitComparison(1)
    }
}
