package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubProjectRefreshApiTest {
    @Test fun resolvesRepositoryByStableIdAndReadsMetadataActionsIssuesAndCommitComparison() = runBlocking {
        val paths = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                FakeGitHubReadConnection(
                    """{"id":42,"full_name":"new-owner/renamed","description":"Updated",
                        "default_branch":"trunk","private":true,"archived":false}"""
                ),
                FakeGitHubReadConnection(branchBody("trunk")),
                FakeGitHubReadConnection(
                    """{"total_count":1,"workflow_runs":[{"id":900,"name":"CI","run_number":7,
                        "status":"completed","conclusion":"success","created_at":"2026-11-01T10:00:00Z",
                        "updated_at":"2026-11-01T10:02:00Z","html_url":"https://github.com/new-owner/renamed/actions/runs/900"}]}"""
                ),
                FakeGitHubReadConnection(
                    searchBody(
                        1,
                        issueJson(11, 1, "Open issue", "open") +
                            """,{"id":12,"number":2,"title":"Pull request","state":"open",
                                "created_at":"2026-11-02T00:00:00Z","updated_at":"2026-11-02T00:00:00Z",
                                "html_url":"https://github.com/new-owner/renamed/pull/2",
                                "pull_request":{"url":"https://api.github.com/repos/new-owner/renamed/pulls/2"}}"""
                    )
                ),
                FakeGitHubReadConnection(searchBody(1, issueJson(13, 3, "Closed issue", "closed"))),
                FakeGitHubReadConnection("""{"status":"ahead","ahead_by":4}""")
            )
        )
        val api = GitHubRepositoryApi("synthetic-token") { url ->
            paths += url.file
            responses.removeFirst()
        }

        val repository = api.repositoryById(42)
        val branch = api.branch(repository)
        val workflows = api.workflowSnapshot(repository)
        val issues = api.issueSnapshot(repository)
        val comparison = api.compareCommits(repository, "a".repeat(40), "c".repeat(40))

        assertEquals("new-owner/renamed", repository.fullName)
        assertEquals(42, repository.id)
        assertEquals("trunk", branch.name)
        assertEquals(GitHubWorkflowState.SUCCESS, workflows.state)
        assertEquals(1, issues.openCount)
        assertEquals(listOf(1L), issues.open.map { it.number })
        assertEquals(1, issues.closedCount)
        assertEquals(4, comparison.aheadBy)
        assertEquals("/repositories/42", paths[0])
        assertEquals(
            "/repos/new-owner/renamed/branches/trunk",
            paths[1]
        )
        assertTrue(paths[2].endsWith("/actions/runs?per_page=1"))
        assertTrue(paths[3].contains("is%3Aissue+is%3Aopen"))
        assertTrue(paths[4].contains("is%3Aissue+is%3Aclosed"))
        assertTrue(paths[5].endsWith("/compare/${"a".repeat(40)}...${"c".repeat(40)}"))
        assertTrue(responses.isEmpty())
    }

    @Test fun incompleteOrDeniedSectionsDoNotProduceACompleteRefresh() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                FakeGitHubReadConnection(
                    """{"id":42,"full_name":"owner/repo","description":null,
                        "default_branch":"main","private":false,"archived":false}"""
                ),
                FakeGitHubReadConnection(branchBody("main")),
                FakeGitHubReadConnection("", status = 403),
                FakeGitHubReadConnection(searchBody(0, "")),
                FakeGitHubReadConnection(searchBody(0, ""))
            )
        )
        val api = GitHubRepositoryApi("synthetic-token") { responses.removeFirst() }

        val result = refreshGitHubProject(api, 42, previous = null, refreshedAt = 10_000)

        assertEquals(GitHubReadError.FORBIDDEN, result.errors[GitHubRefreshSection.ACTIONS])
        assertTrue(result.branch != null)
        assertTrue(result.issues != null)
        assertNull(result.workflows)
        assertNull(result.snapshotOrNull())
    }

    @Test fun noWorkflowsAndZeroIssuesAreReportedAsEmpty() = runBlocking {
        val api = GitHubRepositoryApi("synthetic-token") { url ->
            when {
                url.file.endsWith("/actions/runs?per_page=1") ->
                    FakeGitHubReadConnection("""{"total_count":0,"workflow_runs":[]}""")
                url.file.contains("is%3Aissue+is%3Aopen") ->
                    FakeGitHubReadConnection(searchBody(0, ""))
                else -> FakeGitHubReadConnection(searchBody(0, ""))
            }
        }
        val repository = GitHubRepository(42, "owner/repo", null, "main", false, false)

        val workflows = api.workflowSnapshot(repository)
        val issues = api.issueSnapshot(repository)

        assertEquals(GitHubWorkflowState.NO_WORKFLOWS, workflows.state)
        assertEquals(0, issues.openCount)
        assertEquals(0, issues.closedCount)
        assertTrue(issues.open.isEmpty())
        assertTrue(issues.closed.isEmpty())
        assertFalse(workflows.state == GitHubWorkflowState.SUCCESS)
    }

    private fun branchBody(name: String) = """
        {"name":"$name","protected":false,"commit":{"sha":"${"c".repeat(40)}","commit":{
        "message":"Latest commit\nDetails","tree":{"sha":"${"d".repeat(40)}"},
        "committer":{"date":"2026-11-01T10:00:00Z"}}}}
    """.trimIndent()

    private fun issueJson(id: Long, number: Long, title: String, state: String) = """
        {"id":$id,"number":$number,"title":"$title","state":"$state",
         "created_at":"2026-11-01T00:00:00Z","updated_at":"2026-11-02T00:00:00Z",
         "html_url":"https://github.com/new-owner/renamed/issues/$number"}
    """.trimIndent()

    private fun searchBody(count: Int, items: String) = """{"total_count":$count,
        "incomplete_results":false,"items":[$items]}"""
}
