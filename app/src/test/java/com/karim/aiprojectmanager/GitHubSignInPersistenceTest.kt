package com.karim.aiprojectmanager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubSignInPersistenceTest {
    @Test fun verifiedAuthenticationIsPersistedBeforeSessionAppears() = runBlocking {
        val saved = FakePersistence()
        val signIn = signIn(saved)
        val scope = testScope()
        try {
            signIn.connect(scope) {}
            await { signIn.session != null }
            assertEquals("octocat", signIn.session?.login)
            assertEquals("octocat", saved.credentials?.login)
            assertEquals(TOKEN, saved.credentials?.tokenForVerification())
        } finally { scope.cancel() }
    }

    @Test fun processRecreationRestoresOnlyAfterTokenVerification() = runBlocking {
        val saved = FakePersistence().apply { write("octocat", TOKEN) }
        val transport = FakeTransport()
        val signIn = signIn(saved, transport)
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { signIn.session != null }
            assertEquals("octocat", signIn.session?.login)
            assertEquals(listOf(GitHubAuthEndpoint.USER), transport.requests)
        } finally { scope.cancel() }
    }

    @Test fun aRestoredTokenIsVerifiedAgainstTheCurrentGitHubIdentity() = runBlocking {
        val saved = FakePersistence().apply { write("old-login", TOKEN) }
        val signIn = signIn(saved, FakeTransport(login = "new-login"))
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { signIn.session != null }
            assertEquals("new-login", signIn.session?.login)
            assertEquals("new-login", saved.credentials?.login)
        } finally { scope.cancel() }
    }

    @Test fun unauthorizedSavedTokenIsClearedAndNeverShownConnected() = runBlocking {
        val saved = FakePersistence().apply { write("octocat", TOKEN) }
        val signIn = signIn(saved, FakeTransport(failure = GitHubAuthFailure(GitHubAuthError.UNAUTHORIZED)))
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { !signIn.restoring }
            assertNull(signIn.session)
            assertNull(saved.credentials)
            assertTrue(signIn.sessionExpired)
        } finally { scope.cancel() }
    }

    @Test fun temporaryNetworkFailureKeepsCredentialsAndOffersRetry() = runBlocking {
        val saved = FakePersistence().apply { write("octocat", TOKEN) }
        val signIn = signIn(saved, FakeTransport(failure = GitHubAuthFailure(GitHubAuthError.NETWORK)))
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { !signIn.restoring }
            assertNull(signIn.session)
            assertEquals(TOKEN, saved.credentials?.tokenForVerification())
            assertTrue(signIn.canRetryRestore)
        } finally { scope.cancel() }
    }

    @Test fun corruptStoredCredentialsAreClearedSafely() = runBlocking {
        val saved = FakePersistence(readFailure = GitHubSessionStorageFailure(GitHubSessionStorageError.CORRUPT))
        val signIn = signIn(saved)
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { !signIn.restoring }
            assertNull(signIn.session)
            assertNull(saved.credentials)
            assertTrue(saved.clearCount > 0)
        } finally { scope.cancel() }
    }

    @Test fun transientKeystoreFailureRetainsCredentialsForRetry() = runBlocking {
        val saved = FakePersistence(readFailure = GitHubSessionStorageFailure(GitHubSessionStorageError.KEYSTORE))
            .apply { credentials = GitHubSessionCredentials("octocat", TOKEN) }
        val signIn = signIn(saved)
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { !signIn.restoring }
            assertNull(signIn.session)
            assertEquals(TOKEN, saved.credentials?.tokenForVerification())
            assertTrue(signIn.canRetryRestore)
            assertEquals(0, saved.clearCount)
        } finally { scope.cancel() }
    }

    @Test fun disconnectCancelsActiveStateAndDeletesStoredCredentials() = runBlocking {
        val saved = FakePersistence()
        val signIn = signIn(saved)
        val scope = testScope()
        try {
            signIn.connect(scope) {}
            await { signIn.session != null }
            signIn.disconnect(scope)
            await { !signIn.disconnecting }
            assertNull(signIn.session)
            assertNull(saved.credentials)
            assertFalse(signIn.disconnectFailed)
        } finally { scope.cancel() }
    }

    @Test fun disconnectRemainsDisconnectedWhenANewCoordinatorStarts() = runBlocking {
        val saved = FakePersistence().apply { write("octocat", TOKEN) }
        val signIn = signIn(saved)
        val scope = testScope()
        try {
            signIn.restore(scope)
            await { signIn.session != null }
            signIn.disconnect(scope)
            await { !signIn.disconnecting }
            val reopened = signIn(saved)
            reopened.restore(scope)
            await { !reopened.restoring }
            assertNull(reopened.session)
            assertNull(saved.credentials)
        } finally { scope.cancel() }
    }

    private fun signIn(
        persistence: FakePersistence,
        transport: FakeTransport = FakeTransport()
    ) = GitHubSignIn(
        GitHubDeviceAuth("SyntheticClient", transport, { 0L }, wait = {}),
        persistence
    )

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(5) }
    }

    private class FakePersistence(
        private val readFailure: GitHubSessionStorageFailure? = null
    ) : GitHubSessionPersistence {
        @Volatile var credentials: GitHubSessionCredentials? = null
        @Volatile var clearCount: Int = 0
        override fun read(): GitHubSessionCredentials? {
            readFailure?.let { throw it }
            return credentials
        }
        override fun write(login: String, accessToken: String) {
            credentials = GitHubSessionCredentials(login, accessToken)
        }
        override fun clear() { clearCount++; credentials = null }
    }

    private class FakeTransport(
        private val login: String = "octocat",
        private val failure: GitHubAuthFailure? = null
    ) : GitHubAuthTransport {
        val requests = mutableListOf<GitHubAuthEndpoint>()
        override suspend fun request(
            endpoint: GitHubAuthEndpoint,
            form: Map<String, String>,
            bearer: String?
        ): Map<String, Any> {
            synchronized(requests) { requests += endpoint }
            if (endpoint == GitHubAuthEndpoint.USER) failure?.let { throw it }
            return when (endpoint) {
                GitHubAuthEndpoint.DEVICE_CODE -> mapOf(
                    "device_code" to "d".repeat(40), "user_code" to "DEMO-CODE",
                    "verification_uri" to GITHUB_VERIFICATION_URL, "expires_in" to 900, "interval" to 5
                )
                GitHubAuthEndpoint.TOKEN -> mapOf("access_token" to TOKEN, "token_type" to "bearer", "scope" to "")
                GitHubAuthEndpoint.USER -> mapOf("login" to login)
            }
        }
    }

    private companion object { const val TOKEN = "ghu_SyntheticToken" }
}
