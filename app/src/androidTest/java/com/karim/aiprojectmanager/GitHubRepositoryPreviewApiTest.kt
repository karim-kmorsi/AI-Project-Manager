package com.karim.aiprojectmanager

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryPreviewApiTest {
    @Test fun pinsReadmeAndRootTreeToTheDisplayedCommitAndLimitsBoth() = runBlocking {
        val paths = mutableListOf<String>()
        val readme = FakeGitHubReadConnection("a".repeat(70_000))
        val entries = (1..40).joinToString(",") { """{"path":"file-$it","type":"blob"}""" }
        val tree = FakeGitHubReadConnection("""{"tree":[$entries],"truncated":false}""")
        val responses = ArrayDeque(listOf(FakeGitHubReadConnection(BRANCH), readme, tree))
        val api = GitHubRepositoryApi("fixture") { url ->
            paths += url.file
            responses.removeFirst()
        }

        val result = loadGitHubRepositoryPreview(api, REPOSITORY)
        val branch = (result.branch as GitHubPreviewSection.Available).value
        val document = (result.readme as GitHubPreviewSection.Available).value
        val root = (result.tree as GitHubPreviewSection.Available).value

        assertEquals(COMMIT, branch.commitSha)
        assertEquals("First line", branch.message)
        assertTrue(branch.protected)
        assertEquals(64 * 1024, document.text.length)
        assertTrue(document.truncated)
        assertEquals(30, root.entries.size)
        assertTrue(root.truncated)
        assertEquals("application/vnd.github.raw+json", readme.getRequestProperty("Accept"))
        assertEquals(listOf(
            "/repos/owner/repo/branches/release%2Fnext",
            "/repos/owner/repo/readme?ref=$COMMIT",
            "/repos/owner/repo/git/trees/$TREE"
        ), paths)
        assertFalse(readme.instanceFollowRedirects)
    }

    @Test fun missingReadmeAndForbiddenTreeLeaveBranchInformationAvailable() = runBlocking {
        val responses = ArrayDeque(listOf(
            FakeGitHubReadConnection(BRANCH),
            FakeGitHubReadConnection("", status = 404),
            FakeGitHubReadConnection("", status = 403)
        ))
        val api = GitHubRepositoryApi("fixture") { responses.removeFirst() }
        val result = loadGitHubRepositoryPreview(api, REPOSITORY)
        assertTrue(result.branch is GitHubPreviewSection.Available)
        assertEquals(GitHubPreviewSection.Unavailable(GitHubReadError.NOT_FOUND), result.readme)
        assertEquals(GitHubPreviewSection.Unavailable(GitHubReadError.FORBIDDEN), result.tree)
    }

    @Test fun unavailableBranchDoesNotFetchUnpinnedContent() = runBlocking {
        var requests = 0
        val api = GitHubRepositoryApi("fixture") {
            requests++
            FakeGitHubReadConnection("", status = 409)
        }
        val result = loadGitHubRepositoryPreview(api, REPOSITORY)
        assertEquals(GitHubPreviewSection.Unavailable(GitHubReadError.EMPTY), result.branch)
        assertNull(result.readme)
        assertNull(result.tree)
        assertEquals(1, requests)
    }

    private companion object {
        val REPOSITORY = GitHubRepository(1, "owner/repo", null, "release/next", true, false)
        val COMMIT = "a".repeat(40)
        val TREE = "b".repeat(40)
        val BRANCH = """
            {"name":"release/next","protected":true,"commit":{"sha":"$COMMIT","commit":{
            "message":"First line\nSecond line","tree":{"sha":"$TREE"},"committer":{"date":"2026-11-10T12:00:00Z"}}}}
        """.trimIndent()
    }
}
