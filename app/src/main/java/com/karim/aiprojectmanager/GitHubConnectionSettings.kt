package com.karim.aiprojectmanager

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubConnectionSettings(signIn: GitHubSignIn, localProjects: List<ProjectItem>?) {
    val scope = rememberCoroutineScope()
    DisposableEffect(signIn) { onDispose { signIn.cancel() } }
    val api = remember { GitHubPublicApi() }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(connectionMessage(null)) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ParagraphText("GitHub", style = MaterialTheme.typography.titleMedium)
            GitHubSignInControls(signIn, scope, localProjects)
            ParagraphText("يختبر الزر أدناه واجهة GitHub العامة. لا تتوفر مزامنة المستودعات.")
            ParagraphText(
                if (testing) "جارٍ اختبار الاتصال…" else status,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        try {
                            status = checkGitHubConnection {
                                withContext(Dispatchers.IO) { api.repository("octocat", "Hello-World") }
                            }
                        } finally {
                            testing = false
                        }
                    }
                }
            ) { Text("اختبار اتصال GitHub العام") }
        }
    }
}

@Composable
private fun GitHubSignInControls(
    signIn: GitHubSignIn,
    scope: CoroutineScope,
    localProjects: List<ProjectItem>?
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val code = signIn.userCode
    val session = signIn.session
    var browserError by remember(code) { mutableStateOf(false) }
    var showRepositories by remember(session) { mutableStateOf(false) }

    ParagraphText(
        when {
            session != null -> "تم التحقق من حساب GitHub.\n${session.login}"
            !signIn.isConfigured -> "لم يُضبط معرّف تطبيق GitHub في هذه النسخة."
            signIn.sessionExpired -> stringResource(R.string.github_repos_expired)
            else -> signIn.status
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (code != null) {
        Text(code, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, GITHUB_VERIFICATION_URL.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
            browserError = try {
                context.startActivity(intent)
                false
            } catch (_: ActivityNotFoundException) {
                true
            } catch (_: SecurityException) {
                true
            }
        }) { Text("فتح صفحة GitHub") }
        if (browserError) ParagraphText("تعذّر فتح المتصفح. افتح صفحة GitHub التالية يدويًا.\n$GITHUB_VERIFICATION_URL")
    }
    when {
        signIn.running -> TextButton(onClick = signIn::cancel) { Text("إلغاء الربط") }
        session != null -> {
            TextButton(onClick = { showRepositories = true }) {
                Text(stringResource(R.string.github_repos_browse))
            }
            TextButton(onClick = signIn::disconnect) { Text("قطع الاتصال") }
        }
        else -> TextButton(
            enabled = signIn.isConfigured,
            onClick = {
                signIn.connect(scope) {
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                }
            }
        ) { Text("ربط حساب GitHub") }
    }
    if (session != null && showRepositories) {
        GitHubRepositoriesDialog(
            source = session.repositoryApi,
            onExpired = { signIn.expire(session) },
            onClose = { showRepositories = false },
            previewSource = session.repositoryApi,
            localProjects = localProjects
        )
    }
}

internal suspend fun checkGitHubConnection(request: suspend () -> GitHubResult): String = try {
    connectionMessage(request())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    "حدث خطأ غير متوقع أثناء اختبار اتصال GitHub. حاول مرة أخرى."
}

private fun connectionMessage(result: GitHubResult?): String = when (result) {
    null -> "لم يُختبر الاتصال بعد."
    is GitHubResult.Success -> "نجح الاتصال بواجهة GitHub العامة.\n${result.repository.fullName} · ${result.repository.defaultBranch}"
    is GitHubResult.HttpError -> "أعاد GitHub خطأ HTTP (${result.statusCode})."
    GitHubResult.NetworkError -> "تعذّر الاتصال بواجهة GitHub العامة. تحقق من اتصال الشبكة."
    GitHubResult.Timeout -> "انتهت مهلة الاتصال بواجهة GitHub العامة."
    GitHubResult.InvalidResponse -> "أعاد GitHub استجابة غير صالحة."
}
