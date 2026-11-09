package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GitHubConnectionSettings() {
    val scope = rememberCoroutineScope()
    val api = remember { GitHubPublicApi() }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(connectionMessage(null)) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ParagraphText("GitHub", style = MaterialTheme.typography.titleMedium)
            ParagraphText("يختبر الاتصال واجهة GitHub العامة فقط. الوصول إلى المستودعات الخاصة غير متاح بعد.")
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
