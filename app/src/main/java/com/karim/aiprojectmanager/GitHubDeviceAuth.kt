package com.karim.aiprojectmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

internal const val GITHUB_VERIFICATION_URL = "https://github.com/login/device"

internal enum class GitHubAuthError {
    BUSY, EXPIRED, DENIED, CONFIGURATION, NETWORK, DNS, TIMEOUT, INVALID_RESPONSE, HTTP, VERIFICATION, UNEXPECTED
}

internal class GitHubAuthFailure(
    val reason: GitHubAuthError,
    val endpoint: GitHubAuthEndpoint? = null
) : Exception(reason.name)

internal enum class GitHubAuthEndpoint(val url: String) {
    DEVICE_CODE("https://github.com/login/device/code"),
    TOKEN("https://github.com/login/oauth/access_token"),
    USER("https://api.github.com/user")
}

internal fun interface GitHubAuthTransport {
    suspend fun request(
        endpoint: GitHubAuthEndpoint,
        form: Map<String, String>,
        bearer: String?
    ): Map<String, Any>
}

internal class GitHubSession(val login: String, private val accessToken: String) {
    val repositoryApi by lazy { GitHubRepositoryApi(accessToken) }
}

internal class GitHubDeviceAuth(
    private val clientId: String,
    private val transport: GitHubAuthTransport,
    private val elapsedMillis: () -> Long,
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    val isConfigured: Boolean get() = clientId.isNotEmpty()
    private val attempt = Mutex()

    suspend fun connect(
        awaitForeground: suspend () -> Unit = {},
        onUserCode: (String?) -> Unit
    ): GitHubSession {
        if (!isConfigured) throw GitHubAuthFailure(GitHubAuthError.CONFIGURATION)
        if (!attempt.tryLock()) throw GitHubAuthFailure(GitHubAuthError.BUSY)
        try {
            val token = try {
                val challenge = atEndpoint(GitHubAuthEndpoint.DEVICE_CODE) { requestChallenge() }
                atEndpoint(GitHubAuthEndpoint.TOKEN) { poll(challenge, awaitForeground, onUserCode) }
            } finally {
                onUserCode(null)
            }
            return atEndpoint(GitHubAuthEndpoint.USER) {
                awaitForeground()
                verify(token)
            }
        } finally {
            attempt.unlock()
        }
    }

    private suspend fun requestChallenge(): DeviceChallenge {
        val startedAt = elapsedMillis()
        val reply = transport.request(GitHubAuthEndpoint.DEVICE_CODE, mapOf("client_id" to clientId), null)
        if ("error" in reply) throw oauthFailure(reply["error"])
        val deviceCode = reply.stringMatching("device_code", Regex("[A-Za-z0-9]{40}"))
        val userCode = reply.stringMatching("user_code", Regex("[A-Z0-9]{4}-[A-Z0-9]{4}"))
        if (reply["verification_uri"] != GITHUB_VERIFICATION_URL) invalidResponse()
        return DeviceChallenge(
            deviceCode,
            userCode,
            startedAt + reply.positiveSeconds("expires_in") * 1_000L,
            reply.positiveSeconds("interval") * 1_000L
        )
    }

    private suspend fun poll(
        challenge: DeviceChallenge,
        awaitForeground: suspend () -> Unit,
        onUserCode: (String?) -> Unit
    ): String {
        val deadline = challenge.deadline
        var interval = challenge.interval
        var dnsRetriesRemaining = 2
        val form = mapOf(
            "client_id" to clientId,
            "device_code" to challenge.deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
        )

        currentCoroutineContext().ensureActive()
        remainingMillis(deadline)
        onUserCode(challenge.userCode)
        while (true) {
            wait(minOf(interval, remainingMillis(deadline)))
            currentCoroutineContext().ensureActive()
            val reply = try {
                withTimeoutOrNull(remainingMillis(deadline)) {
                    awaitForeground()
                    currentCoroutineContext().ensureActive()
                    remainingMillis(deadline)
                    transport.request(GitHubAuthEndpoint.TOKEN, form, null)
                } ?: throw GitHubAuthFailure(GitHubAuthError.EXPIRED)
            } catch (failure: GitHubAuthFailure) {
                if (failure.reason != GitHubAuthError.DNS || dnsRetriesRemaining == 0) throw failure
                dnsRetriesRemaining--
                // DNS failed before the POST was sent. Back off within the original deadline.
                interval *= 2
                continue
            }
            remainingMillis(deadline)
            if ("error" in reply && "access_token" in reply) invalidResponse()
            when (val error = reply["error"]) {
                null -> {
                    if (reply["token_type"] != "bearer" || reply["scope"] != "") invalidResponse()
                    return reply.stringMatching("access_token", Regex("ghu_[A-Za-z0-9]{1,252}"))
                }
                "authorization_pending" -> Unit
                "slow_down" -> {
                    interval += 5_000L
                    if ("interval" in reply) interval = maxOf(interval, reply.positiveSeconds("interval") * 1_000L)
                }
                else -> throw oauthFailure(error)
            }
        }
    }

    private suspend fun verify(token: String): GitHubSession {
        currentCoroutineContext().ensureActive()
        val user = transport.request(GitHubAuthEndpoint.USER, emptyMap(), token)
        val login = user["login"] as? String
        if (login == null || !login.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,38}"))) {
            throw GitHubAuthFailure(GitHubAuthError.VERIFICATION)
        }
        currentCoroutineContext().ensureActive()
        return GitHubSession(login, token)
    }

    private suspend fun <T> atEndpoint(endpoint: GitHubAuthEndpoint, request: suspend () -> T): T = try {
        request()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: GitHubAuthFailure) {
        currentCoroutineContext().ensureActive()
        throw GitHubAuthFailure(failure.reason, endpoint)
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        throw GitHubAuthFailure(GitHubAuthError.UNEXPECTED, endpoint)
    }

    private class DeviceChallenge(
        val deviceCode: String,
        val userCode: String,
        val deadline: Long,
        val interval: Long
    )

    private fun remainingMillis(deadline: Long): Long {
        val remaining = deadline - elapsedMillis()
        if (remaining <= 0) throw GitHubAuthFailure(GitHubAuthError.EXPIRED)
        return remaining
    }

    private fun oauthFailure(error: Any?): GitHubAuthFailure = GitHubAuthFailure(
        when (error) {
            "access_denied" -> GitHubAuthError.DENIED
            "expired_token", "token_expired" -> GitHubAuthError.EXPIRED
            "incorrect_client_credentials", "device_flow_disabled" -> GitHubAuthError.CONFIGURATION
            else -> GitHubAuthError.INVALID_RESPONSE
        }
    )

    private fun Map<String, Any>.positiveSeconds(key: String): Int =
        (get(key) as? Int)?.takeIf { it > 0 } ?: invalidResponse()

    private fun Map<String, Any>.stringMatching(key: String, pattern: Regex): String =
        (get(key) as? String)?.takeIf(pattern::matches) ?: invalidResponse()

    private fun invalidResponse(): Nothing = throw GitHubAuthFailure(GitHubAuthError.INVALID_RESPONSE)
}
