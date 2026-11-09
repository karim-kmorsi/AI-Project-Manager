package com.karim.aiprojectmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDeviceAuthTest {
    @Test fun pollsAtTheRequiredIntervalAndVerifiesTheAuthenticatedUser() = runBlocking {
        val fixture = Fixture(
            mapOf("error" to "authorization_pending"),
            mapOf("error" to "slow_down", "interval" to 12),
            mapOf("error" to "slow_down"),
            TOKEN_REPLY
        )
        val codes = mutableListOf<String?>()
        val session = fixture.auth.connect { codes.add(it) }

        assertEquals("octocat", session.login)
        assertTrue(codes.size == 2 && codes.first() == USER_CODE && codes.last() == null)
        assertEquals(listOf(5_000L, 5_000L, 12_000L, 17_000L), fixture.delays)
        assertEquals(4, fixture.polls)
        assertEquals(1, fixture.verifications)
        assertEquals(
            listOf(GitHubAuthEndpoint.DEVICE_CODE) + List(4) { GitHubAuthEndpoint.TOKEN } + GitHubAuthEndpoint.USER,
            fixture.requests
        )
        assertFalse(session.toString().contains(TOKEN))
    }

    @Test fun expiresWithoutPollingAtOrAfterTheDeadline() {
        val fixture = Fixture(expiresIn = 7, replies = arrayOf(mapOf("error" to "authorization_pending")))
        assertFailure(GitHubAuthError.EXPIRED, fixture, GitHubAuthEndpoint.TOKEN)
        assertEquals(listOf(5_000L, 2_000L), fixture.delays)
        assertEquals(1, fixture.polls)
        assertEquals(0, fixture.verifications)
    }

    @Test fun neverVerifiesATokenReceivedAtTheDeviceDeadline() {
        val fixture = Fixture(TOKEN_REPLY, expiresIn = 6, pollDurationMillis = 1_000)
        assertFailure(GitHubAuthError.EXPIRED, fixture, GitHubAuthEndpoint.TOKEN)
        assertEquals(0, fixture.verifications)
    }

    @Test fun reportsOAuthDenialExpirationAndConfigurationErrors() {
        for ((error, reason) in mapOf(
            "access_denied" to GitHubAuthError.DENIED,
            "expired_token" to GitHubAuthError.EXPIRED,
            "token_expired" to GitHubAuthError.EXPIRED,
            "incorrect_client_credentials" to GitHubAuthError.CONFIGURATION,
            "device_flow_disabled" to GitHubAuthError.CONFIGURATION
        )) {
            val fixture = Fixture(mapOf("error" to error))
            assertFailure(reason, fixture, GitHubAuthEndpoint.TOKEN)
            assertEquals(0, fixture.verifications)
        }
    }

    @Test fun rejectsAnUntrustedVerificationUriAndInvalidTiming() {
        for (override in listOf(
            mapOf("verification_uri" to "https://github.com.example/login/device"),
            mapOf("verification_uri" to "http://github.com/login/device"),
            mapOf("interval" to 0),
            mapOf("expires_in" to "900")
        )) {
            val fixture = Fixture(challengeOverrides = override)
            assertFailure(GitHubAuthError.INVALID_RESPONSE, fixture, GitHubAuthEndpoint.DEVICE_CODE)
            assertTrue(fixture.delays.isEmpty())
        }
    }

    @Test fun rejectsAmbiguousOrInvalidTokensBeforeVerification() {
        for (reply in listOf(
            TOKEN_REPLY + ("error" to "authorization_pending"),
            TOKEN_REPLY + ("token_type" to "basic"),
            TOKEN_REPLY + ("scope" to "repo"),
            TOKEN_REPLY + ("access_token" to "ghu_\r\ninvalid")
        )) {
            val fixture = Fixture(reply)
            assertFailure(GitHubAuthError.INVALID_RESPONSE, fixture, GitHubAuthEndpoint.TOKEN)
            assertEquals(0, fixture.verifications)
        }
    }

    @Test fun networkFailuresIdentifyTheEndpointAndStopTheAttempt() {
        val stages = mapOf(
            GitHubAuthEndpoint.DEVICE_CODE to "فشل طلب رمز الربط من GitHub.",
            GitHubAuthEndpoint.TOKEN to "فشل انتظار نتيجة التفويض من GitHub.",
            GitHubAuthEndpoint.USER to "فشل التحقق من حساب GitHub."
        )
        for ((endpoint, stageMessage) in stages) {
            val fixture = Fixture(TOKEN_REPLY, failAt = endpoint, failure = GitHubAuthFailure(GitHubAuthError.NETWORK))
            val failure = assertFailure(GitHubAuthError.NETWORK, fixture, endpoint)
            assertEquals(
                "$stageMessage\nتعذّر الاتصال بـ GitHub. تحقق من الشبكة وحاول مرة أخرى.",
                githubAuthFailureMessage(failure)
            )
            assertEquals(GitHubAuthEndpoint.entries.take(endpoint.ordinal + 1), fixture.requests)
        }
    }

    @Test fun retriesPreSendDnsFailureWithoutRestartingAuthorization() = runBlocking {
        val fixture = Fixture(
            mapOf("error" to "authorization_pending"),
            emptyMap(),
            TOKEN_REPLY,
            dnsFailures = setOf(1)
        )

        assertEquals("octocat", fixture.auth.connect {}.login)
        assertEquals(listOf(5_000L, 5_000L, 10_000L), fixture.delays)
        assertEquals(1, fixture.requests.count { it == GitHubAuthEndpoint.DEVICE_CODE })
        assertEquals(1, fixture.verifications)
    }

    @Test fun dnsRetryBudgetIsBounded() {
        val fixture = Fixture(dnsFailures = setOf(0, 1, 2))
        assertFailure(GitHubAuthError.DNS, fixture, GitHubAuthEndpoint.TOKEN)
        assertEquals(listOf(5_000L, 10_000L, 20_000L), fixture.delays)
        assertEquals(3, fixture.polls)
        assertEquals(0, fixture.verifications)
    }

    @Test fun dnsBackoffCannotPollAtTheDeadline() {
        val fixture = Fixture(expiresIn = 12, dnsFailures = setOf(0))
        assertFailure(GitHubAuthError.EXPIRED, fixture, GitHubAuthEndpoint.TOKEN)
        assertEquals(listOf(5_000L, 7_000L), fixture.delays)
        assertEquals(1, fixture.polls)
        assertEquals(0, fixture.verifications)
    }

    @Test fun suspendsPollingUntilTheAppReturnsToTheForeground() = runBlocking {
        val foreground = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<Unit>()
        val fixture = Fixture(TOKEN_REPLY)
        val auth = async {
            fixture.auth.connect(awaitForeground = {
                waiting.complete(Unit)
                foreground.await()
            }) {}
        }
        waiting.await()
        assertEquals(listOf(GitHubAuthEndpoint.DEVICE_CODE), fixture.requests)

        foreground.complete(Unit)
        assertEquals("octocat", auth.await().login)
        assertEquals(1, fixture.polls)
        assertEquals(1, fixture.verifications)
    }

    @Test fun refusesToPollWhenForegroundReturnsAfterTheDeadline() {
        val fixture = Fixture(expiresIn = 8)
        val error = assertThrows(GitHubAuthFailure::class.java) {
            runBlocking {
                fixture.auth.connect(awaitForeground = { fixture.now = 8_000L }) {}
            }
        }
        assertEquals(GitHubAuthError.EXPIRED, error.reason)
        assertEquals(GitHubAuthEndpoint.TOKEN, error.endpoint)
        assertEquals(0, fixture.polls)
    }

    @Test fun invalidIdentityIsAUserVerificationFailure() {
        val fixture = Fixture(TOKEN_REPLY, user = emptyMap())
        assertFailure(GitHubAuthError.VERIFICATION, fixture, GitHubAuthEndpoint.USER)
        assertEquals(1, fixture.verifications)
    }

    @Test fun unexpectedFailuresDoNotExposeExceptionMessagesOrCauses() {
        val fixture = Fixture(TOKEN_REPLY, failAt = GitHubAuthEndpoint.USER, failure = IllegalStateException(TOKEN))
        val failure = assertFailure(GitHubAuthError.UNEXPECTED, fixture, GitHubAuthEndpoint.USER)
        assertEquals("فشل التحقق من حساب GitHub.\nحدث خطأ غير متوقع. حاول مرة أخرى.", githubAuthFailureMessage(failure))
        assertFalse(failure.toString().contains(TOKEN))
        assertTrue(failure.cause == null)
    }

    @Test fun cancellationClearsTheCodeAndReleasesTheSingleAttemptLock() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        val codes = mutableListOf<String?>()
        var pause = true
        val fixture = Fixture(TOKEN_REPLY, onDelay = {
            if (pause) {
                waiting.complete(Unit)
                awaitCancellation()
            }
        })
        val first = async { fixture.auth.connect { codes.add(it) } }
        waiting.await()
        val second = runCatching { fixture.auth.connect { error("Concurrent attempt published state") } }
        assertEquals(GitHubAuthError.BUSY, (second.exceptionOrNull() as GitHubAuthFailure).reason)

        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
        assertTrue(codes.size == 2 && codes.last() == null)
        assertEquals(0, fixture.polls)

        pause = false
        assertEquals("octocat", fixture.auth.connect {}.login)
    }

    private fun assertFailure(
        expected: GitHubAuthError,
        fixture: Fixture,
        endpoint: GitHubAuthEndpoint
    ): GitHubAuthFailure {
        var lastCode: String? = null
        val error = assertThrows(GitHubAuthFailure::class.java) {
            runBlocking { fixture.auth.connect { lastCode = it } }
        }
        assertEquals(expected, error.reason)
        assertEquals(endpoint, error.endpoint)
        assertTrue(lastCode == null)
        return error
    }

    private class Fixture(
        vararg replies: Map<String, Any>,
        expiresIn: Int = 900,
        challengeOverrides: Map<String, Any> = emptyMap(),
        pollDurationMillis: Long = 0,
        dnsFailures: Set<Int> = emptySet(),
        failAt: GitHubAuthEndpoint? = null,
        failure: Exception? = null,
        user: Map<String, Any> = mapOf("login" to "octocat"),
        onDelay: suspend () -> Unit = {}
    ) {
        var now = 0L
        val delays = mutableListOf<Long>()
        var polls = 0
        var verifications = 0
        val requests = mutableListOf<GitHubAuthEndpoint>()
        val auth = GitHubDeviceAuth("SyntheticClient", { endpoint, form, bearer ->
            requests += endpoint
            if (endpoint == failAt) throw requireNotNull(failure)
            when (endpoint) {
                GitHubAuthEndpoint.DEVICE_CODE -> {
                    assertEquals(setOf("client_id"), form.keys)
                    assertTrue(bearer == null)
                    mapOf(
                        "device_code" to DEVICE_CODE,
                        "user_code" to USER_CODE,
                        "verification_uri" to GITHUB_VERIFICATION_URL,
                        "expires_in" to expiresIn,
                        "interval" to 5
                    ) + challengeOverrides
                }
                GitHubAuthEndpoint.TOKEN -> {
                    assertEquals(setOf("client_id", "device_code", "grant_type"), form.keys)
                    assertTrue(form["device_code"] == DEVICE_CODE && bearer == null)
                    assertEquals("urn:ietf:params:oauth:grant-type:device_code", form["grant_type"])
                    val index = polls++
                    now += pollDurationMillis
                    if (index in dnsFailures) throw GitHubAuthFailure(GitHubAuthError.DNS)
                    replies[index]
                }
                GitHubAuthEndpoint.USER -> {
                    verifications++
                    assertTrue(form.isEmpty() && bearer == TOKEN)
                    user
                }
            }
        }, { now }, { millis ->
            delays += millis
            now += millis
            onDelay()
        })
    }

    private companion object {
        val DEVICE_CODE = "a".repeat(40)
        const val USER_CODE = "DEMO-CODE"
        const val TOKEN = "ghu_SyntheticToken"
        val TOKEN_REPLY = mapOf("access_token" to TOKEN, "token_type" to "bearer", "scope" to "")
    }
}
