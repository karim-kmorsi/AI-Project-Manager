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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@Composable
internal fun GitHubRepositoryPreviewDialog(
    repository: GitHubRepository,
    source: GitHubRepositoryPreviewSource,
    localProjects: List<ProjectItem>?,
    onExpired: () -> Unit,
    onBack: () -> Unit
) {
    var preview by remember(repository) { mutableStateOf<GitHubRepositoryPreview?>(null) }
    var error by remember(repository) { mutableStateOf<GitHubReadFailure?>(null) }
    var request by remember(repository) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val localBaseline = remember(repository) { localProjects?.toList() }
    var showImport by remember(repository) { mutableStateOf(false) }
    var comparing by remember(repository) { mutableStateOf(false) }
    var comparison by remember(repository) { mutableStateOf<GitHubSyncComparison?>(null) }
    var comparisonError by remember(repository) { mutableStateOf<GitHubReadError?>(null) }
    LaunchedEffect(repository, request) {
        try {
            preview = loadGitHubRepositoryPreview(source, repository)
        } catch (failure: GitHubReadFailure) {
            if (failure.reason == GitHubReadError.EXPIRED) onExpired() else error = failure
        }
    }
    Dialog(onDismissRequest = onBack) {
        VNextPreviewSurface() {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParagraphText(stringResource(R.string.github_repos_preview), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        GitHubRepositorySummary(repository)
                        TextButton(onClick = { showImport = !showImport }) {
                            Text(stringResource(R.string.github_repos_import_preview))
                        }
                        if (showImport) GitHubImportPreviewContent(repository, localProjects)
                        val branch = (preview?.branch as? GitHubPreviewSection.Available)?.value
                        if (branch != null && localBaseline != null && localProjects != null) {
                            TextButton(enabled = !comparing, onClick = {
                                if (!comparing) {
                                    comparing = true
                                    comparison = null
                                    comparisonError = null
                                    scope.launch {
                                        try {
                                            val current = source.snapshot(repository)
                                            comparison = compareGitHubSnapshots(
                                                GitHubRemoteSnapshot(repository, branch.commitSha),
                                                current, localBaseline, localProjects.toList()
                                            )
                                        } catch (failure: GitHubReadFailure) {
                                            if (failure.reason == GitHubReadError.EXPIRED) onExpired()
                                            else comparisonError = failure.reason
                                        } finally {
                                            comparing = false
                                        }
                                    }
                                }
                            }) { Text(stringResource(R.string.github_repos_sync_compare)) }
                        }
                        if (comparing) ParagraphText(stringResource(R.string.github_repos_sync_loading))
                        comparisonError?.let { ParagraphText(stringResource(it.messageResource())) }
                        comparison?.let { GitHubSyncComparisonContent(it) }
                    }
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
    VNextPanel { when (val branch = preview.branch) {
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
    }
    preview.readme?.let { readme -> VNextPanel {
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
    }
    preview.tree?.let { tree -> VNextPanel {
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
}

@Composable
private fun GitHubImportPreviewContent(repository: GitHubRepository, projects: List<ProjectItem>?) {
    if (projects == null) {
        ParagraphText(stringResource(R.string.github_repos_import_unavailable))
        return
    }
    val proposal = previewGitHubImport(repository, projects)
    if (proposal == null) {
        ParagraphText(stringResource(R.string.github_repos_import_invalid))
        return
    }
    VNextPanel {
    ParagraphText(stringResource(R.string.github_repos_import_read_only))
    ParagraphText(proposal.project.name)
    ParagraphText(stringResource(R.string.github_repos_import_id, proposal.project.id))
    ParagraphText(proposal.project.description)
    ParagraphText(stringResource(R.string.github_repos_import_empty_fields))
    if (proposal.candidates.isEmpty()) ParagraphText(stringResource(R.string.github_repos_import_none))
    proposal.candidates.take(10).forEach { candidate ->
        ParagraphText(stringResource(R.string.github_repos_import_duplicate, candidate.name))
    }
    if (proposal.candidates.size > 10) ParagraphText(stringResource(R.string.github_repos_partial))
    }
}

@Composable
private fun GitHubSyncComparisonContent(comparison: GitHubSyncComparison) {
    VNextPanel {
    ParagraphText(stringResource(when (comparison.disposition) {
        GitHubSyncDisposition.UNCHANGED -> R.string.github_repos_sync_unchanged
        GitHubSyncDisposition.REMOTE_ONLY -> R.string.github_repos_sync_remote
        GitHubSyncDisposition.LOCAL_ONLY -> R.string.github_repos_sync_local
        GitHubSyncDisposition.CONFLICT -> R.string.github_repos_sync_conflict
        GitHubSyncDisposition.UNLINKED -> R.string.github_repos_sync_unlinked
    }))
    comparison.changes.forEach { change ->
        ParagraphText(stringResource(when (change) {
            GitHubRemoteChange.METADATA -> R.string.github_repos_sync_metadata
            GitHubRemoteChange.DEFAULT_BRANCH -> R.string.github_repos_sync_branch
            GitHubRemoteChange.HEAD -> R.string.github_repos_sync_head
        }))
    }
}
}
