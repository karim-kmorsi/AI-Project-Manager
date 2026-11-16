package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    onBack: () -> Unit,
    onImport: ((GitHubRepository, Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>) -> GitHubImportOutcome)? = null
) {
    var preview by remember(repository) { mutableStateOf<GitHubRepositoryPreview?>(null) }
    var error by remember(repository) { mutableStateOf<GitHubReadFailure?>(null) }
    var request by remember(repository) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val localBaseline = remember(repository) { localProjects?.toList() }
    var showImport by remember(repository) { mutableStateOf(false) }
    var confirmImport by remember(repository) { mutableStateOf(false) }
    var importMessage by remember(repository) { mutableIntStateOf(0) }
    var comparing by remember(repository) { mutableStateOf(false) }
    var comparison by remember(repository) { mutableStateOf<GitHubSyncComparison?>(null) }
    var comparisonError by remember(repository) { mutableStateOf<GitHubReadError?>(null) }
    val context = LocalContext.current
    LaunchedEffect(repository, request) {
        try {
            preview = loadGitHubRepositoryPreview(source, repository)
        } catch (failure: GitHubReadFailure) {
            if (failure.reason == GitHubReadError.EXPIRED) onExpired() else error = failure
        }
    }
    val importProposal = localProjects?.let {
        previewGitHubImport(repository, it, preview?.planningDocuments)
    }
    val canRequestImport = importProposal != null && importProposal.planningDocuments != null &&
        importProposal.candidates.isEmpty() && onImport != null
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
                        if (showImport) GitHubImportPreviewContent(
                            repository, localProjects, preview, importMessage
                        )
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.github_repos_back))
                    }
                    TextButton(
                        enabled = canRequestImport,
                        modifier = Modifier.weight(1f),
                        onClick = { confirmImport = true; importMessage = 0 }
                    ) { Text(stringResource(R.string.github_repos_import_action)) }
                }
            }
        }
    }
    if (confirmImport) {
        val proposal = importProposal
        val canImport = proposal != null && proposal.planningDocuments != null &&
            proposal.candidates.isEmpty() && onImport != null
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.github_repos_import_confirm_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        localProjects == null -> ParagraphText(stringResource(R.string.github_repos_import_unavailable))
                        proposal == null -> ParagraphText(stringResource(R.string.github_repos_import_invalid))
                        else -> {
                            ParagraphText(stringResource(
                                R.string.github_repos_import_project_name, proposal.project.name
                            ))
                            ParagraphText(stringResource(
                                R.string.github_repos_import_repository_identity,
                                repository.fullName,
                                repository.id
                            ))
                            GitHubDocumentMappingContent(
                                proposal.planningDocuments,
                                showDocumentContents = false
                            )
                            if (proposal.candidates.isNotEmpty()) {
                                ParagraphText(stringResource(R.string.github_repos_import_duplicate_blocked))
                                proposal.candidates.take(10).forEach { candidate ->
                                    ParagraphText(stringResource(R.string.github_repos_import_duplicate, candidate.name))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = canImport, onClick = {
                    val outcome = proposal?.planningDocuments?.let { onImport?.invoke(repository, it) }
                        ?: GitHubImportOutcome.FAILED
                    confirmImport = false
                    when (outcome) {
                        GitHubImportOutcome.IMPORTED -> android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.github_repos_import_success),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        GitHubImportOutcome.DUPLICATE -> {
                            showImport = true
                            importMessage = R.string.github_repos_import_duplicate_blocked
                        }
                        GitHubImportOutcome.FAILED -> {
                            showImport = true
                            importMessage = R.string.github_repos_import_failed
                        }
                    }
                }) { Text(stringResource(R.string.github_repos_import_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text(stringResource(R.string.github_repos_import_cancel))
                }
            }
        )
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
private fun GitHubImportPreviewContent(
    repository: GitHubRepository,
    projects: List<ProjectItem>?,
    repositoryPreview: GitHubRepositoryPreview?,
    importMessage: Int
) {
    if (projects == null) {
        ParagraphText(stringResource(R.string.github_repos_import_unavailable))
        return
    }
    val proposal = previewGitHubImport(repository, projects, repositoryPreview?.planningDocuments)
    if (proposal == null) {
        ParagraphText(stringResource(R.string.github_repos_import_invalid))
        return
    }
    val branchFailure = repositoryPreview?.branch as? GitHubPreviewSection.Unavailable
    VNextPanel {
        ParagraphText(stringResource(R.string.github_repos_import_read_only))
        ParagraphText(proposal.project.name)
        ParagraphText(stringResource(R.string.github_repos_import_id, proposal.project.id))
        ParagraphText(proposal.project.description)
        when {
            proposal.planningDocuments != null -> GitHubDocumentMappingContent(proposal.planningDocuments)
            branchFailure != null -> ParagraphText(stringResource(branchFailure.reason.messageResource()))
            else -> ParagraphText(stringResource(R.string.github_repos_import_documents_loading))
        }
        if (proposal.candidates.isEmpty()) ParagraphText(stringResource(R.string.github_repos_import_none))
        proposal.candidates.take(10).forEach { candidate ->
            ParagraphText(stringResource(R.string.github_repos_import_duplicate, candidate.name))
        }
        if (proposal.candidates.size > 10) ParagraphText(stringResource(R.string.github_repos_partial))
        if (proposal.candidates.isNotEmpty()) {
            ParagraphText(stringResource(R.string.github_repos_import_duplicate_blocked))
        }
        if (importMessage != 0) ParagraphText(stringResource(importMessage))
    }
}

@Composable
private fun GitHubDocumentMappingContent(
    documents: Map<GitHubPlanningFile, GitHubPreviewSection<GitHubPlanningDocument>>?,
    showDocumentContents: Boolean = true
) {
    ParagraphText(stringResource(R.string.github_repos_import_readme_mapping))
    if (documents == null) {
        ParagraphText(stringResource(R.string.github_repos_import_documents_loading))
        return
    }
    GitHubDocumentMapping(
        GitHubPlanningFile.BLUEPRINT,
        documents.getValue(GitHubPlanningFile.BLUEPRINT),
        R.string.github_repos_import_blueprint_mapping,
        R.string.github_repos_import_blueprint_absent,
        showDocumentContents
    )
    GitHubDocumentMapping(
        GitHubPlanningFile.ROADMAP,
        documents.getValue(GitHubPlanningFile.ROADMAP),
        R.string.github_repos_import_roadmap_mapping,
        R.string.github_repos_import_roadmap_absent,
        showDocumentContents
    )
    ParagraphText(stringResource(R.string.github_repos_import_tasks_empty))
}

@Composable
private fun GitHubDocumentMapping(
    file: GitHubPlanningFile,
    document: GitHubPreviewSection<GitHubPlanningDocument>,
    mapping: Int,
    absent: Int,
    showDocumentContents: Boolean
) {
    when (document) {
        is GitHubPreviewSection.Available -> {
            ParagraphText(stringResource(mapping))
            if (showDocumentContents) {
                val visibleText = document.value.text.take(12_000)
                ParagraphText(visibleText.ifBlank { stringResource(R.string.github_repos_import_document_empty) })
                if (document.value.text.length > visibleText.length) {
                    ParagraphText(stringResource(R.string.github_repos_partial))
                }
            }
        }
        is GitHubPreviewSection.Unavailable -> when (document.reason) {
            GitHubReadError.NOT_FOUND -> ParagraphText(stringResource(absent))
            GitHubReadError.TOO_LARGE -> ParagraphText(
                stringResource(R.string.github_repos_import_document_too_large, file.fileName)
            )
            else -> {
                ParagraphText(stringResource(mapping))
                ParagraphText(stringResource(R.string.github_repos_import_document_unavailable, file.fileName))
                ParagraphText(stringResource(document.reason.messageResource()))
            }
        }
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
