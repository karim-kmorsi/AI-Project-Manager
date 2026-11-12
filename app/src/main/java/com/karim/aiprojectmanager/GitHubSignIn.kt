package com.karim.aiprojectmanager

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val deviceAuth by lazy {
    GitHubDeviceAuth(BuildConfig.GITHUB_APP_CLIENT_ID, GitHubAuthHttp(), SystemClock::elapsedRealtime)
}

/** The app composition owns this state. Nothing is written to saved instance state or storage. */
internal class GitHubSignIn(private val auth: GitHubDeviceAuth = deviceAuth) {
    val isConfigured: Boolean get() = auth.isConfigured
    var session by mutableStateOf<GitHubSession?>(null)
        private set
    var sessionExpired by mutableStateOf(false)
        private set
    var userCode by mutableStateOf<String?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf(R.string.github_status_not_connected)
        private set
    private var job: Job? = null

    fun connect(scope: CoroutineScope, awaitForeground: suspend () -> Unit) {
        if (running || session != null || !isConfigured) return
        sessionExpired = false
        running = true
        status = R.string.github_status_requesting_code
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                session = auth.connect(awaitForeground) { code ->
                    userCode = code
                    status = if (code != null) R.string.github_status_enter_code else R.string.github_status_verifying
                }
            } catch (cancelled: CancellationException) {
                status = R.string.github_status_cancelled
                throw cancelled
            } catch (failure: GitHubAuthFailure) {
                status = githubAuthFailureStringRes(failure)
            } catch (_: Exception) {
                status = R.string.github_status_connect_failed
            } finally {
                userCode = null
                running = false
            }
        }
    }

    fun cancel() {
        userCode = null
        job?.cancel()
    }

    fun expire(expected: GitHubSession) {
        if (session !== expected) return
        session = null
        sessionExpired = true
    }

    fun disconnect() {
        session = null
        status = R.string.github_status_cancelled
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
        GitHubAuthError.VERIFICATION -> "تعذّر تأكيد هوية الحساب من استجابة GitHub."
        GitHubAuthError.UNEXPECTED -> "حدث خطأ غير متوقع. حاول مرة أخرى."
    }
    return if (stage == null) detail else "$stage\n$detail"
}
