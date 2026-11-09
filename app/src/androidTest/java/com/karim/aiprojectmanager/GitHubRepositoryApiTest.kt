package com.karim.aiprojectmanager

import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepositoryApiTest {
    @Test fun pagesInstallationsAndRepositoriesWithoutFollowingUntrustedLinks() = runBlocking {
        val connections = ArrayDeque(listOf(
            FakeGitHubReadConnection("""{"installations":[{"id":11}]}""", nextLink()),
            FakeGitHubReadConnection(repositoryPage(1, false), nextLink()),
            FakeGitHubReadConnection(repositoryPage(2, true)),
            FakeGitHubReadConnection("""{"installations":[{"id":22}]}"""),
            FakeGitHubReadConnection(repositoryPage(3, true))
        ))
        val urls = mutableListOf<String>()
        val api = GitHubRepositoryApi("test-token") { url ->
            assertEquals("https", url.protocol)
            assertEquals("api.github.com", url.host)
            urls.add(url.file)
            connections.removeFirst()
        }
        val first = loadGitHubRepositoryPage(api, GitHubRepositoryCursor())
        val second = loadGitHubRepositoryPage(api, requireNotNull(first.next))
        val third = loadGitHubRepositoryPage(api, requireNotNull(second.next))
        assertEquals(listOf(1L, 2L, 3L), listOf(first, second, third).map { it.repositories.single().id })
        assertFalse(first.repositories.single().isPrivate)
        assertTrue(second.repositories.single().isPrivate)
        assertNull(third.next)
        assertEquals(listOf(
            "/user/installations?per_page=30&page=1",
            "/user/installations/11/repositories?per_page=30&page=1",
            "/user/installations/11/repositories?per_page=30&page=2",
            "/user/installations?per_page=30&page=2",
            "/user/installations/22/repositories?per_page=30&page=1"
        ), urls)
    }

    @Test fun rateLimitPreventsAnotherRequestAndUnauthorizedRequiresReconnection() {
        val limited = FakeGitHubReadConnection("", mapOf("Retry-After" to "17"), status = 403)
        var calls = 0
        val api = GitHubRepositoryApi("test-token") { calls++; limited }
        repeat(2) {
            val result = failure(api)
            assertEquals(GitHubReadError.RATE_LIMIT, result.reason)
            assertEquals(17L, result.retryAfterSeconds)
        }
        assertEquals(1, calls)
        assertEquals(GitHubReadError.EXPIRED, failure(GitHubRepositoryApi("test-token") {
            FakeGitHubReadConnection("", status = 401)
        }).reason)
    }

    @Test fun handlesEmptyInstallationsAndSanitizesInvalidResponses() = runBlocking {
        val empty = FakeGitHubReadConnection("""{"installations":[]}""")
        val page = loadGitHubRepositoryPage(GitHubRepositoryApi("test-token") { empty }, GitHubRepositoryCursor())
        assertTrue(page.repositories.isEmpty())
        assertNull(page.next)
        assertTrue(empty.disconnected)
        assertFalse(empty.instanceFollowRedirects)
        assertEquals("GET", empty.requestMethod)
        assertTrue(empty.getRequestProperty("Authorization") == "Bearer test-token")
        assertEquals(GitHubReadError.INVALID_RESPONSE, failure(GitHubRepositoryApi("test-token") {
            FakeGitHubReadConnection("""{"installations":[{"id":-1}]}""")
        }).reason)
        assertEquals(GitHubReadError.FORBIDDEN, failure(GitHubRepositoryApi("test-token") {
            FakeGitHubReadConnection("", status = 403)
        }).reason)
    }

    private fun failure(api: GitHubRepositoryApi) = assertThrows(GitHubReadFailure::class.java) {
        runBlocking { loadGitHubRepositoryPage(api, GitHubRepositoryCursor()) }
    }

    private fun nextLink() = mapOf("Link" to """<https://example.invalid/untrusted>; rel="next"""")
    private fun repositoryPage(id: Long, private: Boolean) = """
        {"repositories":[{"id":$id,"full_name":"owner/repo-$id","description":null,
        "default_branch":"main","private":$private,"archived":false}]}
    """.trimIndent()
}

internal class FakeGitHubReadConnection(
    private val body: String,
    private val headers: Map<String, String> = emptyMap(),
    private val status: Int = 200
) : HttpsURLConnection(URL("https://api.github.com")) {
    var disconnected = false
        private set
    override fun connect() = Unit
    override fun disconnect() { disconnected = true }
    override fun usingProxy() = false
    override fun getCipherSuite() = "fixture"
    override fun getLocalCertificates(): Array<Certificate>? = null
    override fun getServerCertificates(): Array<Certificate>? = null
    override fun getResponseCode() = status
    override fun getHeaderField(name: String) = headers[name]
    override fun getInputStream() = ByteArrayInputStream(body.encodeToByteArray())
}
