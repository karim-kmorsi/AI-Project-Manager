package com.karim.aiprojectmanager

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
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
    VNextCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VNextSectionTitle("GitHub", R.drawable.vnext_link)
            GitHubSignInControls(signIn, scope, localProjects)
        }
    }
}

@Composable
internal fun GitHubPublicConnectivityProbe() {
    val scope = rememberCoroutineScope()
    val api = remember { GitHubPublicApi() }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<GitHubResult?>(null) }
    var unexpectedError by remember { mutableStateOf(false) }

    VNextCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ParagraphText(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
            ParagraphText(stringResource(R.string.settings_public_probe_description))
            ParagraphText(
                when {
                    testing -> stringResource(R.string.github_probe_testing)
                    unexpectedError -> stringResource(R.string.github_probe_unexpected)
                    else -> status?.let { localizedProbeResult(it) } ?: stringResource(R.string.github_probe_initial)
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    scope.launch {
                        try {
                            try {
                                status = withContext(Dispatchers.IO) { api.repository("octocat", "Hello-World") }
                                unexpectedError = false
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { unexpectedError = true }
                        } finally {
                            testing = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.github_probe_button)) }
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
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val code = signIn.userCode
    val session = signIn.session
    var browserError by remember(code) { mutableStateOf(false) }
    var showRepositories by remember(session) { mutableStateOf(false) }

    ParagraphText(
        when {
            session != null -> stringResource(R.string.github_status_connected, session.login)
            signIn.disconnecting -> stringResource(R.string.github_status_disconnecting)
            signIn.restoring -> stringResource(R.string.github_status_restoring)
            signIn.sessionExpired -> stringResource(signIn.status)
            !signIn.isConfigured -> stringResource(R.string.github_status_not_configured)
            else -> stringResource(signIn.status)
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
    if (code != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(code, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(code))
                android.widget.Toast.makeText(context, context.getString(R.string.github_code_copied), android.widget.Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.github_copy_code)) }
        }
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
        }) { Text(stringResource(R.string.github_open_authorization)) }
        if (browserError) ParagraphText(stringResource(R.string.github_browser_error, GITHUB_VERIFICATION_URL))
    }
    when {
        signIn.running -> TextButton(onClick = signIn::cancel) { Text(stringResource(R.string.github_cancel_connection)) }
        signIn.disconnecting || signIn.restoring -> Unit
        signIn.disconnectFailed -> TextButton(onClick = { signIn.disconnect(scope) }) {
            Text(stringResource(R.string.github_retry_disconnect))
        }
        session != null -> {
            TextButton(onClick = { showRepositories = true }) {
                Text(stringResource(R.string.github_repos_browse))
            }
            TextButton(onClick = { signIn.disconnect(scope) }) { Text(stringResource(R.string.github_disconnect)) }
        }
        else -> {
            if (signIn.canRetryRestore) TextButton(onClick = { signIn.retryRestore(scope) }) {
                Text(stringResource(R.string.github_retry_session))
            }
            TextButton(
                enabled = signIn.isConfigured,
                onClick = {
                    signIn.connect(scope) {
                        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    }
                }
            ) { Text(stringResource(R.string.github_connect)) }
        }
    }
    if (session != null && showRepositories) {
        GitHubRepositoriesDialog(
            source = session.repositoryApi,
            onExpired = { signIn.expire(session, scope) },
            onClose = { showRepositories = false },
            previewSource = session.repositoryApi,
            localProjects = localProjects
        )
    }
}

@Composable
private fun localizedProbeResult(result: GitHubResult): String = when (result) {
    is GitHubResult.Success -> stringResource(R.string.github_probe_success,
        result.repository.fullName, result.repository.defaultBranch)
    is GitHubResult.HttpError -> stringResource(R.string.github_probe_http, result.statusCode)
    GitHubResult.NetworkError -> stringResource(R.string.github_probe_network)
    GitHubResult.Timeout -> stringResource(R.string.github_probe_timeout)
    GitHubResult.InvalidResponse -> stringResource(R.string.github_probe_invalid)
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
