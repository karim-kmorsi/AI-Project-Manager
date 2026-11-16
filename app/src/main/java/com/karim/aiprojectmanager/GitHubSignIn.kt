package com.karim.aiprojectmanager

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

private val deviceAuth by lazy {
    GitHubDeviceAuth(BuildConfig.GITHUB_APP_CLIENT_ID, GitHubAuthHttp(), SystemClock::elapsedRealtime)
}

/** Owns the verified session state while the encrypted persistence adapter owns its durable copy. */
internal class GitHubSignIn(
    private val auth: GitHubDeviceAuth = deviceAuth,
    private val persistence: GitHubSessionPersistence = InMemoryGitHubSessionPersistence()
) {
    val isConfigured: Boolean get() = auth.isConfigured
    var session by mutableStateOf<GitHubSession?>(null)
        private set
    var sessionExpired by mutableStateOf(false)
        private set
    var userCode by mutableStateOf<String?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var cancelling by mutableStateOf(false)
        private set
    var restoring by mutableStateOf(false)
        private set
    var canRetryRestore by mutableStateOf(false)
        private set
    var disconnecting by mutableStateOf(false)
        private set
    var disconnectFailed by mutableStateOf(false)
        private set
    var status by mutableStateOf(R.string.github_status_not_connected)
        private set
    private var job: Job? = null
    private val generation = AtomicLong()
    private val persistenceMutex = Mutex()

    fun connect(scope: CoroutineScope, awaitForeground: suspend () -> Unit) {
        if (running || cancelling || restoring || disconnecting || disconnectFailed || session != null || !isConfigured) return
        val operation = generation.incrementAndGet()
        sessionExpired = false
        running = true
        status = R.string.github_status_requesting_code
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val authenticated = auth.connect(awaitForeground) { code ->
                    if (generation.get() == operation) {
                        userCode = code
                        status = if (code != null) R.string.github_status_enter_code else R.string.github_status_verifying
                    }
                }
                val stored = persistenceMutex.withLock {
                    if (generation.get() != operation) return@withLock false
                    withContext(Dispatchers.IO) {
                        persistence.write(authenticated.login, authenticated.tokenForEncryptedPersistence())
                    }
                    if (generation.get() != operation) {
                        withContext(NonCancellable + Dispatchers.IO) { persistence.clear() }
                        false
                    } else true
                }
                if (stored && generation.get() == operation) {
                    session = authenticated
                    canRetryRestore = false
                }
            } catch (failure: GitHubSessionStorageFailure) {
                if (generation.get() == operation) status = R.string.github_status_store_failed
            } catch (cancelled: CancellationException) {
                if (generation.get() == operation) status = R.string.github_status_cancelled
                throw cancelled
            } catch (failure: GitHubAuthFailure) {
                if (generation.get() == operation) status = githubAuthFailureStringRes(failure)
            } catch (_: Exception) {
                if (generation.get() == operation) status = R.string.github_status_connect_failed
            } finally {
                if (generation.get() == operation) {
                    userCode = null
                    running = false
                    cancelling = false
                } else if (cancelling) {
                    userCode = null
                    running = false
                    cancelling = false
                }
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        userCode = null
        cancelling = true
        status = R.string.github_status_cancelled
        job?.cancel()
    }

    fun restore(scope: CoroutineScope) {
        if (running || cancelling || restoring || disconnecting || disconnectFailed || session != null) return
        val operation = generation.incrementAndGet()
        restoring = true
        canRetryRestore = false
        status = R.string.github_status_restoring
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val saved = persistenceMutex.withLock {
                    withContext(Dispatchers.IO) { persistence.read() }
                }
                if (generation.get() != operation) return@launch
                if (saved == null) {
                    status = R.string.github_status_not_connected
                    return@launch
                }
                try {
                    val verified = auth.verifySavedToken(saved.tokenForVerification())
                    if (generation.get() != operation) return@launch
                    if (verified.login != saved.login) {
                        persistenceMutex.withLock {
                            if (generation.get() == operation) withContext(Dispatchers.IO) {
                                persistence.write(verified.login, verified.tokenForEncryptedPersistence())
                            }
                        }
                    }
                    if (generation.get() == operation) {
                        session = verified
                        sessionExpired = false
                        status = R.string.github_status_not_connected
                    }
                } catch (failure: GitHubAuthFailure) {
                    if (generation.get() != operation) return@launch
                    if (failure.reason in setOf(GitHubAuthError.UNAUTHORIZED, GitHubAuthError.EXPIRED, GitHubAuthError.VERIFICATION)) {
                        if (clearStoredCredentials()) {
                            sessionExpired = true
                            status = R.string.github_status_restore_expired
                        } else {
                            disconnectFailed = true
                            status = R.string.github_status_disconnect_failed
                        }
                    } else {
                        // Network, DNS, timeout, HTTP and malformed responses do not prove revocation.
                        canRetryRestore = true
                        status = R.string.github_status_restore_unverified
                    }
                }
            } catch (failure: GitHubSessionStorageFailure) {
                if (generation.get() == operation) {
                    if (failure.reason in setOf(GitHubSessionStorageError.CORRUPT,
                            GitHubSessionStorageError.KEY_MISSING, GitHubSessionStorageError.KEY_INVALIDATED)) {
                        if (clearStoredCredentials()) sessionExpired = true
                        else disconnectFailed = true
                    } else if (failure.reason == GitHubSessionStorageError.KEYSTORE ||
                        failure.reason == GitHubSessionStorageError.IO) {
                        canRetryRestore = true
                    }
                    status = if (disconnectFailed) R.string.github_status_disconnect_failed else when (failure.reason) {
                        GitHubSessionStorageError.IO -> R.string.github_status_store_failed
                        GitHubSessionStorageError.KEYSTORE -> R.string.github_status_restore_keystore
                        GitHubSessionStorageError.CORRUPT, GitHubSessionStorageError.KEY_MISSING,
                        GitHubSessionStorageError.KEY_INVALIDATED -> R.string.github_status_restore_storage
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation.get() == operation) {
                    canRetryRestore = true
                    status = R.string.github_status_restore_unverified
                }
            } finally {
                if (generation.get() == operation) restoring = false
            }
        }
    }

    fun retryRestore(scope: CoroutineScope) {
        if (canRetryRestore) restore(scope)
    }

    fun expire(expected: GitHubSession, scope: CoroutineScope) {
        if (session !== expected) return
        generation.incrementAndGet()
        job?.cancel()
        userCode = null
        running = false
        cancelling = false
        restoring = false
        session = null
        sessionExpired = true
        canRetryRestore = false
        clearCredentialsAsync(scope, expired = true)
    }

    fun disconnect(scope: CoroutineScope) {
        generation.incrementAndGet()
        job?.cancel()
        userCode = null
        running = false
        cancelling = false
        restoring = false
        canRetryRestore = false
        session = null
        sessionExpired = false
        disconnectFailed = false
        disconnecting = true
        status = R.string.github_status_disconnecting
        clearCredentialsAsync(scope, expired = false)
    }

    private fun clearCredentialsAsync(scope: CoroutineScope, expired: Boolean) {
        disconnecting = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val success = try {
                persistenceMutex.withLock {
                    withContext(NonCancellable + Dispatchers.IO) { persistence.clear() }
                }
                true
            } catch (_: Exception) { false }
            disconnecting = false
            disconnectFailed = !success
            status = when {
                !success -> R.string.github_status_disconnect_failed
                expired -> R.string.github_status_restore_expired
                else -> R.string.github_status_not_connected
            }
        }
    }

    private suspend fun clearStoredCredentials(): Boolean = try {
        persistenceMutex.withLock {
            withContext(NonCancellable + Dispatchers.IO) { persistence.clear() }
        }
        true
    } catch (_: Exception) {
        false
    }
}

internal fun githubAuthFailureStringRes(failure: GitHubAuthFailure): Int = when (failure.reason) {
    GitHubAuthError.BUSY -> R.string.github_auth_busy
    GitHubAuthError.EXPIRED -> R.string.github_auth_expired
    GitHubAuthError.DENIED -> R.string.github_auth_denied
    GitHubAuthError.CONFIGURATION -> R.string.github_auth_configuration
    GitHubAuthError.NETWORK -> R.string.github_auth_network
    GitHubAuthError.DNS -> R.string.github_auth_dns
    GitHubAuthError.TIMEOUT -> R.string.github_auth_timeout
    GitHubAuthError.INVALID_RESPONSE -> R.string.github_auth_invalid
    GitHubAuthError.HTTP -> R.string.github_auth_http
    GitHubAuthError.UNAUTHORIZED -> R.string.github_auth_unauthorized
    GitHubAuthError.VERIFICATION -> R.string.github_auth_verification
    GitHubAuthError.UNEXPECTED -> when (failure.endpoint) {
        GitHubAuthEndpoint.DEVICE_CODE -> R.string.github_auth_device_failure
        GitHubAuthEndpoint.TOKEN -> R.string.github_auth_token_failure
        GitHubAuthEndpoint.USER, null -> R.string.github_auth_unexpected
    }
}

internal fun githubAuthFailureMessage(failure: GitHubAuthFailure): String {
    val stage = when (failure.endpoint) {
        GitHubAuthEndpoint.DEVICE_CODE -> "فشل طلب رمز الربط من GitHub."
        GitHubAuthEndpoint.TOKEN -> "فشل انتظار نتيجة التفويض من GitHub."
        GitHubAuthEndpoint.USER -> "فشل التحقق من حساب GitHub."
        null -> null
    }
    val detail = when (failure.reason) {
        GitHubAuthError.BUSY -> "هناك محاولة ربط جارية. حاول مرة أخرى بعد انتهائها."
        GitHubAuthError.EXPIRED -> "انتهت صلاحية الرمز. اطلب رمزًا جديدًا."
        GitHubAuthError.DENIED -> "رُفض طلب الربط في GitHub."
        GitHubAuthError.CONFIGURATION -> "تعذّر بدء الربط بإعدادات تطبيق GitHub الحالية."
        GitHubAuthError.NETWORK -> "تعذّر الاتصال بـ GitHub. تحقق من الشبكة وحاول مرة أخرى."
        GitHubAuthError.DNS -> "تعذّر العثور على عنوان GitHub عبر الشبكة. حاول مرة أخرى."
        GitHubAuthError.TIMEOUT -> "انتهت مهلة الاتصال بـ GitHub. حاول مرة أخرى."
        GitHubAuthError.INVALID_RESPONSE -> "أعاد GitHub استجابة غير صالحة."
        GitHubAuthError.HTTP -> "أعاد GitHub خطأ في الطلب. حاول مرة أخرى."
        GitHubAuthError.UNAUTHORIZED -> "رفض GitHub بيانات الاعتماد المحفوظة. أعد الاتصال."
        GitHubAuthError.VERIFICATION -> "تعذّر تأكيد هوية الحساب من استجابة GitHub."
        GitHubAuthError.UNEXPECTED -> "حدث خطأ غير متوقع. حاول مرة أخرى."
    }
    return if (stage == null) detail else "$stage\n$detail"
}
