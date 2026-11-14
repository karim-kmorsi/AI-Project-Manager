package com.karim.aiprojectmanager

import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPlanningDocumentImportTest {
    @Test fun readsAtThePreviewedCommitAndRejectsOversizedOrMissingDocuments() = runBlocking {
        val sha = "a".repeat(40)
        val path = GitHubPlanningFile.BLUEPRINT
        val content = "Planning document"
        val connection = DocumentConnection(content)
        var requestedPath = ""
        val api = GitHubRepositoryApi("fixture", elapsedRealtime = { 0L }) { url ->
            requestedPath = url.file
            connection
        }

        assertEquals(content, api.planningDocument(repository, path, sha).text)
        assertEquals("/repos/owner/repo/contents/BLUEPRINT.md?ref=$sha", requestedPath)
        assertEquals("GET", connection.requestMethod)
        assertEquals("application/vnd.github.raw+json", connection.getRequestProperty("Accept"))
        assertTrue(connection.disconnected)

        val oversized = GitHubRepositoryApi("fixture", elapsedRealtime = { 0L }) {
            DocumentConnection("x".repeat(64 * 1024 + 1))
        }
        val tooLarge = assertThrows(GitHubReadFailure::class.java) {
            runBlocking { oversized.planningDocument(repository, path, sha) }
        }
        assertEquals(GitHubReadError.TOO_LARGE, tooLarge.reason)

        val missing = GitHubRepositoryApi("fixture", elapsedRealtime = { 0L }) {
            DocumentConnection("", status = 404)
        }
        val notFound = assertThrows(GitHubReadFailure::class.java) {
            runBlocking { missing.planningDocument(repository, path, sha) }
        }
        assertEquals(GitHubReadError.NOT_FOUND, notFound.reason)
    }

    @Test fun previewLoadsOnlyRootDocumentsAndPassesTheirContentToTheProjectMapping() = runBlocking {
        val requested = mutableListOf<Pair<GitHubPlanningFile, String>>()
        val source = object : GitHubRepositoryPreviewSource {
            override suspend fun snapshot(repository: GitHubRepository) = error("Not used")
            override suspend fun branch(repository: GitHubRepository) =
                GitHubBranchPreview("main", false, SHA, TREE, "Documentation", null)
            override suspend fun readme(repository: GitHubRepository, commitSha: String): GitHubReadmePreview {
                assertEquals(SHA, commitSha)
                return GitHubReadmePreview("README stays in the preview", false)
            }
            override suspend fun tree(repository: GitHubRepository, treeSha: String) =
                GitHubTreePreview(
                    listOf(
                        GitHubTreeEntry("README.md", false),
                        GitHubTreeEntry("BLUEPRINT.md", false),
                        GitHubTreeEntry("ROADMAP.md", false),
                        GitHubTreeEntry("docs/BLUEPRINT.md", false)
                    ),
                    false,
                    setOf("README.md", "BLUEPRINT.md", "ROADMAP.md")
                )
            override suspend fun planningDocument(
                repository: GitHubRepository,
                document: GitHubPlanningFile,
                commitSha: String
            ): GitHubPlanningDocument {
                requested += document to commitSha
                return GitHubPlanningDocument(document.fileName)
            }
        }

        val preview = loadGitHubRepositoryPreview(source, repository)
        val project = requireNotNull(previewGitHubImport(
            repository, emptyList(), preview.planningDocuments
        )).project

        assertEquals(setOf("README.md", "BLUEPRINT.md", "ROADMAP.md"),
            (preview.tree as GitHubPreviewSection.Available).value.rootFiles)
        assertEquals(
            "README stays in the preview",
            (preview.readme as GitHubPreviewSection.Available).value.text
        )
        assertEquals(setOf(GitHubPlanningFile.BLUEPRINT to SHA, GitHubPlanningFile.ROADMAP to SHA), requested.toSet())
        assertEquals("BLUEPRINT.md", project.blueprint)
        assertEquals("ROADMAP.md", project.roadmap)
        assertTrue(project.description.contains("https://github.com/owner/repo"))
        assertTrue(project.tasks.isEmpty())
    }

    private class DocumentConnection(
        body: String,
        private val status: Int = 200
    ) : HttpsURLConnection(URL("https://api.github.com")) {
        private val input = ByteArrayInputStream(body.encodeToByteArray())
        var disconnected = false
            private set

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
        override fun getResponseCode() = status
        override fun getInputStream() = input
    }

    private companion object {
        val repository = GitHubRepository(24, "owner/repo", "About", "main", true, false)
        val SHA = "a".repeat(40)
        val TREE = "b".repeat(40)
    }
}
