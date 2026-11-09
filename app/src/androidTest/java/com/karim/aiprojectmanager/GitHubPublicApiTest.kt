package com.karim.aiprojectmanager

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubPublicApiTest {
    @Test fun readsPublicMetadataWithAnUnauthenticatedHttpsGet() {
        val connection = FakeConnection(body = VALID)
        val api = GitHubPublicApi { url ->
            assertEquals("https://api.github.com/repos/octocat/Hello-World", url.toString())
            connection
        }
        assertEquals(
            GitHubResult.Success(PublicRepository("octocat/Hello-World", "master")),
            api.repository("octocat", "Hello-World")
        )
        assertEquals("GET", connection.requestMethod)
        assertNull(connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.connectTimeout > 0 && connection.readTimeout > 0 && connection.disconnected)
    }

    @Test fun reportsHttpErrorsWithoutReadingTheBody() {
        for (status in listOf(301, 403, 404, 429, 500)) {
            val connection = FakeConnection(status = status, failure = IOException("Must not read body"))
            assertEquals(GitHubResult.HttpError(status), check(connection))
            assertTrue(connection.disconnected)
        }
    }

    @Test fun distinguishesTimeoutAndNetworkFailure() {
        assertEquals(GitHubResult.Timeout, check(FakeConnection(failure = SocketTimeoutException())))
        assertEquals(GitHubResult.NetworkError, check(FakeConnection(failure = IOException())))
    }

    @Test fun rejectsInvalidOrNonPublicMetadata() {
        for (body in listOf(
            "{", "{}", VALID + "{}", VALID.replace("false", "true"),
            VALID.replace("octocat/Hello-World", "other/repo"), VALID.replace("\"master\"", "123"),
            VALID + " ".repeat(65_536)
        )) {
            assertEquals(GitHubResult.InvalidResponse, check(FakeConnection(body = body)))
        }
    }

    private fun check(connection: FakeConnection) =
        GitHubPublicApi { connection }.repository("octocat", "Hello-World")

    private class FakeConnection(
        private val status: Int = 200,
        private val body: String = "",
        private val failure: IOException? = null
    ) : HttpURLConnection(URL("https://api.github.com")) {
        var disconnected = false
            private set

        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = status
        override fun getInputStream(): ByteArrayInputStream {
            failure?.let { throw it }
            return body.byteInputStream()
        }
    }

    private companion object {
        const val VALID = """{"full_name":"octocat/Hello-World","default_branch":"master","private":false}"""
    }
}
