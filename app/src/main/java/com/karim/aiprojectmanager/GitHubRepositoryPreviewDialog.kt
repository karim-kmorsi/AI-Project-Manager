package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
internal fun GitHubRepositoryPreviewDialog(
    repository: GitHubRepository,
    source: GitHubRepositoryPreviewSource,
    onExpired: () -> Unit,
    onBack: () -> Unit
) {
    var preview by remember(repository) { mutableStateOf<GitHubRepositoryPreview?>(null) }
    var error by remember(repository) { mutableStateOf<GitHubReadFailure?>(null) }
    var request by remember(repository) { mutableIntStateOf(0) }
    LaunchedEffect(repository, request) {
        try {
            preview = loadGitHubRepositoryPreview(source, repository)
        } catch (failure: GitHubReadFailure) {
            if (failure.reason == GitHubReadError.EXPIRED) onExpired() else error = failure
        }
    }
    Dialog(onDismissRequest = onBack) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParagraphText(stringResource(R.string.github_repos_preview), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { GitHubRepositorySummary(repository) }
                    item {
                        val result = preview
                        val failure = error
                        when {
                            result != null -> GitHubRepositoryPreviewContent(result)
                            failure != null -> {
                                val wait = failure.retryAfterSeconds
                                ParagraphText(if (wait != null) stringResource(R.string.github_repos_limited_wait, wait)
                                    else stringResource(failure.reason.messageResource()))
                                TextButton(onClick = { error = null; request++ }) {
                                    Text(stringResource(R.string.github_repos_retry))
                                }
                            }
                            else -> ParagraphText(stringResource(R.string.github_repos_preview_loading))
                        }
                    }
                }
                TextButton(onClick = onBack) { Text(stringResource(R.string.github_repos_back)) }
            }
        }
    }
}

@Composable
private fun GitHubRepositoryPreviewContent(preview: GitHubRepositoryPreview) {
    when (val branch = preview.branch) {
        is GitHubPreviewSection.Available -> {
            val info = branch.value
            ParagraphText(stringResource(R.string.github_repos_branch_name, info.name))
            if (info.protected) ParagraphText(stringResource(R.string.github_repos_protected))
            ParagraphText(stringResource(R.string.github_repos_latest_commit), style = MaterialTheme.typography.titleMedium)
            ParagraphText(info.commitSha.take(12))
            if (info.message.isNotBlank()) ParagraphText(info.message)
            info.committedAt?.let { ParagraphText(stringResource(R.string.github_repos_committed_at, it)) }
        }
        is GitHubPreviewSection.Unavailable -> ParagraphText(stringResource(branch.reason.messageResource()))
    }
    preview.readme?.let { readme ->
        ParagraphText(stringResource(R.string.github_repos_readme), style = MaterialTheme.typography.titleMedium)
        when (readme) {
            is GitHubPreviewSection.Available -> {
                val content = readme.value.text.take(12_000)
                ParagraphText(content.ifBlank { stringResource(R.string.github_repos_readme_absent) })
                if (readme.value.truncated || readme.value.text.length > content.length) {
                    ParagraphText(stringResource(R.string.github_repos_partial))
                }
            }
            is GitHubPreviewSection.Unavailable -> ParagraphText(stringResource(
                if (readme.reason == GitHubReadError.NOT_FOUND) R.string.github_repos_readme_absent
                else readme.reason.messageResource()
            ))
        }
    }
    preview.tree?.let { tree ->
        ParagraphText(stringResource(R.string.github_repos_tree), style = MaterialTheme.typography.titleMedium)
        when (tree) {
            is GitHubPreviewSection.Available -> {
                if (tree.value.entries.isEmpty()) ParagraphText(stringResource(R.string.github_repos_tree_empty))
                tree.value.entries.forEach { entry ->
                    ParagraphText(stringResource(if (entry.directory) R.string.github_repos_folder else R.string.github_repos_file, entry.path))
                }
                if (tree.value.truncated) ParagraphText(stringResource(R.string.github_repos_partial))
            }
            is GitHubPreviewSection.Unavailable -> ParagraphText(stringResource(tree.reason.messageResource()))
        }
    }
}
