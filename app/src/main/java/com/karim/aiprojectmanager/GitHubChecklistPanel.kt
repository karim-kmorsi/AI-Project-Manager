package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The preview is read-only until the owner applies candidate additions.
 * Refreshing never overwrites an existing local task, including its status.
 */
@Composable
internal fun GitHubChecklistPanel(
    project: ProjectItem,
    repositoryApi: GitHubRepositoryApi?,
    update: (ProjectItem) -> Boolean
) {
    val repositoryId = project.id.removePrefix("github:").toLongOrNull()
        ?.takeIf { project.id == "github:$it" && it > 0 } ?: return
    val scope = rememberCoroutineScope()
    var loading by remember(repositoryId, repositoryApi) { mutableStateOf(false) }
    var result by remember(repositoryId, repositoryApi) { mutableStateOf<GitHubChecklistFetchResult?>(null) }
    var error by remember(repositoryId, repositoryApi) { mutableStateOf(false) }
    var saveError by remember(repositoryId) { mutableStateOf(false) }
    suspend fun refresh() {
        if (repositoryApi == null || loading) return
        loading = true
        error = false
        saveError = false
        try {
            result = withContext(Dispatchers.IO) { repositoryApi.checklist(repositoryId) }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            error = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(repositoryId, repositoryApi) { refresh() }
    val additions = result?.let { proposeGitHubChecklistTasks(project.tasks, it.preview) }.orEmpty()

    VNextPanel(Modifier.fillMaxWidth().testTag("github_checklist_panel")) {
        VNextSectionTitle(stringResource(R.string.checklist_title), R.drawable.vnext_check)
        Text(stringResource(R.string.checklist_source_note), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { scope.launch { refresh() } }, enabled = repositoryApi != null && !loading) {
            Text(stringResource(if (loading) R.string.checklist_loading else R.string.checklist_refresh))
        }
        if (repositoryApi == null) Text(stringResource(R.string.checklist_connect))
        if (error) Text(stringResource(R.string.checklist_error), color = MaterialTheme.colorScheme.error)
        if (saveError) Text(stringResource(R.string.checklist_save_error), color = MaterialTheme.colorScheme.error)
        result?.let { data ->
            Text(stringResource(R.string.checklist_found, data.preview.entries.size, additions.size))
            if (data.preview.entries.isEmpty()) Text(stringResource(R.string.checklist_empty))
            data.warnings.forEach { warning ->
                Text(warning, style = MaterialTheme.typography.bodySmall)
            }
            additions.take(20).forEach { task ->
                Text("• ${task.title}", style = MaterialTheme.typography.bodySmall)
            }
            if (additions.size > 20) Text(stringResource(R.string.checklist_more, additions.size - 20))
            if (additions.isNotEmpty()) {
                TextButton(onClick = {
                    // Recalculate against the latest project state at click time.
                    val toAdd = proposeGitHubChecklistTasks(project.tasks, data.preview)
                    if (toAdd.isNotEmpty()) {
                        if (update(project.copy(tasks = project.tasks + toAdd))) {
                            saveError = false
                        } else saveError = true
                    }
                }) { Text(stringResource(R.string.checklist_apply, additions.size)) }
            }
        }
    }
}
