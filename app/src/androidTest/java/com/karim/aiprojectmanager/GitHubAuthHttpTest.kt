package com.karim.aiprojectmanager

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAuthHttpTest {
    @Test fun sendsDeviceParametersInThePostBodyOverHttps() = runBlocking {
        val connection = FakeConnection("""{"device_code":"synthetic","interval":5}""")
        val api = GitHubAuthHttp {
            assertEquals("https://github.com/login/device/code", it.toString())
            connection
        }

        val result = api.request(GitHubAuthEndpoint.DEVICE_CODE, mapOf("client_id" to "SyntheticClient"), null)

        assertEquals(5, result["interval"])
        assertEquals("POST", connection.requestMethod)
        assertEquals("client_id=SyntheticClient", connection.requestBody.toString("UTF-8"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects || connection.useCaches)
        assertTrue(connection.connectTimeout > 0 && connection.readTimeout > 0 && connection.disconnected)
    }

    @Test fun parsesOnlyTheExpectedFieldsAndUsesTheBearerForUserVerification() = runBlocking {
        val tokenConnection = FakeConnection("""{"access_token":"ghu_Fixture","refresh_token":"ghr_Fixture","token_type":"bearer","scope":""}""")
        val token = GitHubAuthHttp { tokenConnection }.request(GitHubAuthEndpoint.TOKEN, emptyMap(), null)
        assertEquals(setOf("access_token", "token_type", "scope"), token.keys)

        val userConnection = FakeConnection("""{"login":"octocat","email":"ignored"}""")
        val api = GitHubAuthHttp {
            assertEquals("https://api.github.com/user", it.toString())
            userConnection
        }
        assertEquals(mapOf("login" to "octocat"), api.request(GitHubAuthEndpoint.USER, emptyMap(), "ghu_Fixture"))
        assertEquals("GET", userConnection.requestMethod)
        assertTrue(userConnection.getRequestProperty("Authorization") == "Bearer ghu_Fixture")
        assertEquals(0, userConnection.requestBody.size())
    }

    @Test fun rejectsInvalidJsonAndOversizedBodies() {
        for (body in listOf(
            "{",
            """{"interval":"5"}""",
            """{"interval":5,"interval":10}""",
            "{}{}",
            "{}" + " ".repeat(65_536)
        )) {
            val connection = FakeConnection(body)
            assertFailure(GitHubAuthError.INVALID_RESPONSE, connection)
        }
    }

    @Test fun sanitizesHttpAndIoFailures() {
        assertFailure(GitHubAuthError.HTTP, FakeConnection(status = 302))
        assertFailure(GitHubAuthError.UNAUTHORIZED, FakeConnection(status = 401), GitHubAuthEndpoint.USER)
        assertFailure(GitHubAuthError.HTTP, FakeConnection(status = 503), GitHubAuthEndpoint.USER)
        assertFailure(GitHubAuthError.HTTP, FakeConnection(status = 403), GitHubAuthEndpoint.USER)
        assertFailure(GitHubAuthError.NETWORK, FakeConnection(failure = IOException("Fixture I/O error")))
        assertFailure(GitHubAuthError.TIMEOUT, FakeConnection(failure = SocketTimeoutException()))
    }

    @Test fun distinguishesDnsBeforeSendingThePostFromAResponseReadFailure() {
        val dns = UnknownHostException("fixture")
        assertFailure(GitHubAuthError.DNS, FakeConnection(outputFailure = dns), GitHubAuthEndpoint.TOKEN)
        assertFailure(GitHubAuthError.NETWORK, FakeConnection(failure = dns), GitHubAuthEndpoint.TOKEN)
    }

    private fun assertFailure(
        expected: GitHubAuthError,
        connection: FakeConnection,
        endpoint: GitHubAuthEndpoint = GitHubAuthEndpoint.DEVICE_CODE
    ) {
        val failure = assertThrows(GitHubAuthFailure::class.java) {
            runBlocking { GitHubAuthHttp { connection }.request(endpoint, emptyMap(), "ghu_Fixture") }
        }
        assertEquals(expected, failure.reason)
        assertNull(failure.cause)
        assertTrue(connection.disconnected)
    }

    private class FakeConnection(
        private val body: String = "",
        private val status: Int = 200,
        private val failure: IOException? = null,
        private val outputFailure: IOException? = null
    ) : HttpsURLConnection(URL("https://github.com")) {
        val requestBody = ByteArrayOutputStream()
        var disconnected = false
            private set

        override fun getCipherSuite() = "fixture"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = status
        override fun getOutputStream() = outputFailure?.let { throw it } ?: requestBody
        override fun getInputStream() = failure?.let { throw it } ?: body.byteInputStream()
    }
}
