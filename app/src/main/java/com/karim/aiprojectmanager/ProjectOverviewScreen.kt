package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeParseException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Overview of saved project information. It does not infer dates, versions, or verification. */
@Composable
internal fun ProjectOverviewScreen(
    project: ProjectItem,
    connected: Boolean,
    onEdit: () -> Unit,
    repositoryApi: GitHubProjectRefreshSource? = null,
    snapshotStore: GitHubProjectSnapshotStore? = null
) {
    val verified = project.tasks.count { it.status == "VERIFIED" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = ProjectSpacing.screen, vertical = ProjectSpacing.small),
        verticalArrangement = Arrangement.spacedBy(ProjectSpacing.small)
    ) {
        VNextPanel(Modifier.testTag("overview_description_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_description_title), R.drawable.vnext_info)
            ParagraphText(
                project.description.ifBlank { stringResource(R.string.overview_description_empty) },
                Modifier.testTag("overview_description"), style = ProjectTextStyles.supporting
            )
            SecondaryActionButton(onEdit) {
                VNextIcon(R.drawable.vnext_edit)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.overview_edit_project))
            }
        }

        VNextPanel(Modifier.testTag("overview_progress_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_progress), R.drawable.vnext_check)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ParagraphText(stringResource(R.string.overview_manually_completed),
                    style = ProjectTextStyles.supporting, fillWidth = false)
                ParagraphText(taskCountExpression(verified, project.tasks.size),
                    style = ProjectTextStyles.project, fontWeight = FontWeight.Bold, fillWidth = false)
            }
            VNextProgress(project)
            ParagraphText(stringResource(R.string.overview_manual_note),
                style = ProjectTextStyles.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val pending = project.tasks.filter { it.status != "VERIFIED" }
        if (pending.isNotEmpty()) VNextPanel(Modifier.testTag("overview_pending_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_pending), R.drawable.vnext_route)
            pending.forEach { ParagraphText("${it.title} — ${localizedTaskStatus(it.status)}", style = ProjectTextStyles.supporting) }
        }

        VNextPanel(Modifier.testTag("overview_github_panel")) {
            VNextSectionTitle(stringResource(R.string.overview_github), R.drawable.vnext_link)
            VNextStatusPill(stringResource(if (connected) R.string.overview_connected else R.string.overview_disconnected),
                warning = !connected)
            ParagraphText(stringResource(R.string.overview_github_note),
                style = ProjectTextStyles.supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val repositoryId = project.id.removePrefix("github:").toLongOrNull()
            ?.takeIf { project.id == "github:$it" && it > 0 }
        if (repositoryId != null && snapshotStore != null) {
            LinkedGitHubProjectPanel(repositoryId, repositoryApi, snapshotStore)
        }

        VNextPanel(Modifier.testTag("overview_quick_info")) {
            VNextSectionTitle(stringResource(R.string.overview_quick_info), R.drawable.vnext_info)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewFact(stringResource(R.string.overview_total_tasks), project.tasks.size.toString(), Modifier.weight(1f))
                OverviewFact(stringResource(R.string.overview_blueprint),
                    stringResource(if (project.blueprint.isBlank()) R.string.overview_empty else R.string.overview_present), Modifier.weight(1f))
                OverviewFact(stringResource(R.string.overview_roadmap),
                    stringResource(if (project.roadmap.isBlank()) R.string.overview_empty else R.string.overview_present), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LinkedGitHubProjectPanel(
    repositoryId: Long,
    source: GitHubProjectRefreshSource?,
    store: GitHubProjectSnapshotStore
) {
    val scope = rememberCoroutineScope()
    var saved by remember(repositoryId, store) { mutableStateOf<GitHubSnapshotRead?>(null) }
    var refreshResult by remember(repositoryId, store) { mutableStateOf<GitHubProjectRefreshResult?>(null) }
    var loading by remember(repositoryId, store) { mutableStateOf(false) }
    var storageError by remember(repositoryId, store) { mutableStateOf(false) }

    val refresh: suspend (Boolean) -> Unit = { automatic ->
        if (!loading && source != null) {
            loading = true
            refreshResult = null
            storageError = false
            try {
                val priorState = saved ?: withContext(Dispatchers.IO) { store.read(repositoryId) }
                saved = priorState
                if (priorState == GitHubSnapshotRead.Invalid) {
                    storageError = true
                } else {
                    val attemptedAt = System.currentTimeMillis()
                    val attemptRecorded = withContext(Dispatchers.IO) {
                        store.markRefreshAttempt(repositoryId, attemptedAt)
                    }
                    if (!attemptRecorded) storageError = true
                    if (!(automatic && !attemptRecorded)) {
                        val previous = (priorState as? GitHubSnapshotRead.Available)?.history
                        val result = refreshGitHubProject(source, repositoryId, previous, System.currentTimeMillis())
                        refreshResult = result
                        val snapshot = result.snapshotOrNull()
                        if (snapshot != null) {
                            val next = GitHubSnapshotHistory(previous?.current, snapshot, result.newCommitCount)
                            val stored = withContext(Dispatchers.IO) {
                                store.save(repositoryId, previous, next)
                            }
                            if (stored) {
                                saved = GitHubSnapshotRead.Available(next)
                            } else {
                                storageError = true
                                saved = withContext(Dispatchers.IO) { store.read(repositoryId) }
                            }
                        }
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(repositoryId, source, store) {
        val initial = withContext(Dispatchers.IO) { store.read(repositoryId) }
        saved = initial
        if (source != null && initial != GitHubSnapshotRead.Invalid) {
            val now = System.currentTimeMillis()
            val lastAttempt = withContext(Dispatchers.IO) { store.lastRefreshAttemptAt(repositoryId) }
            if (shouldAutomaticallyRefreshGitHubProject(lastAttempt, now)) refresh(true)
        }
    }

    val history = (saved as? GitHubSnapshotRead.Available)?.history
    val persisted = history?.current
    val shownRepository = refreshResult?.repository ?: persisted?.repository
    val shownBranch = refreshResult?.branch ?: persisted?.branch
    val shownWorkflows = refreshResult?.workflows ?: persisted?.workflows
    val shownIssues = refreshResult?.issues ?: persisted?.issues
    val errors = refreshResult?.errors.orEmpty()
    val context = LocalContext.current

    VNextPanel(Modifier.testTag("github_linked_repository_panel")) {
        VNextSectionTitle(stringResource(R.string.github_linked_title), R.drawable.vnext_link)
        ParagraphText(
            stringResource(R.string.github_repository_id, repositoryId),
            style = ProjectTextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryActionButton(
                onClick = { scope.launch { refresh(false) } },
                enabled = source != null && !loading && saved != GitHubSnapshotRead.Invalid
            ) {
                Text(stringResource(if (loading) R.string.github_refreshing else R.string.github_refresh))
            }
        }
        if (source == null) {
            ParagraphText(
                stringResource(R.string.github_refresh_needs_connection),
                style = ProjectTextStyles.supporting
            )
        }
        if (storageError) {
            ParagraphText(
                stringResource(
                    if (saved == GitHubSnapshotRead.Invalid) R.string.github_snapshot_invalid
                    else R.string.github_snapshot_save_failed
                ),
                style = ProjectTextStyles.supporting,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (persisted != null) {
            ParagraphText(
                stringResource(
                    R.string.github_refresh_last_success,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(persisted.refreshedAt))
                ),
                style = ProjectTextStyles.metadata
            )
        } else {
            ParagraphText(stringResource(R.string.github_refresh_not_yet), style = ProjectTextStyles.metadata)
        }
        if (errors.isNotEmpty()) {
            ParagraphText(
                stringResource(R.string.github_partial_refresh),
                style = ProjectTextStyles.supporting,
                color = MaterialTheme.colorScheme.error
            )
            errors.forEach { (section, error) ->
                ParagraphText(
                    stringResource(R.string.github_refresh_error, refreshSectionLabel(section), readErrorLabel(error)),
                    style = ProjectTextStyles.metadata,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        shownRepository?.let { repository ->
            ParagraphText(repository.fullName, style = ProjectTextStyles.project, fontWeight = FontWeight.Bold)
            ParagraphText(
                "https://github.com/${repository.fullName}",
                style = ProjectTextStyles.metadata,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("github_remote_url")
            )
            ParagraphText(stringResource(R.string.github_remote_description), style = ProjectTextStyles.metadata)
            ParagraphText(
                repository.description ?: stringResource(R.string.github_no_remote_description),
                style = ProjectTextStyles.supporting
            )
            ParagraphText(
                stringResource(if (repository.isPrivate) R.string.github_repository_private else R.string.github_repository_public),
                style = ProjectTextStyles.metadata
            )
            ParagraphText(
                stringResource(if (repository.archived) R.string.github_repository_archived else R.string.github_repository_active),
                style = ProjectTextStyles.metadata
            )
        }
        shownBranch?.let { branch ->
            ParagraphText(stringResource(R.string.github_default_branch, branch.name), style = ProjectTextStyles.supporting)
            ParagraphText(
                stringResource(R.string.github_latest_commit, branch.commitSha) + " — " + branch.message,
                style = ProjectTextStyles.supporting,
                modifier = Modifier.testTag("github_latest_commit")
            )
            branch.committedAt?.let { date ->
                ParagraphText(
                    stringResource(R.string.github_commit_date, date.toLocalizedDate(context)),
                    style = ProjectTextStyles.metadata
                )
            }
        }
        shownWorkflows?.let { workflows ->
            GitHubWorkflowSection(workflows)
        }
        shownIssues?.let { issues ->
            GitHubIssuesSection(issues)
        }
        history?.let { GitHubChangesSection(it) }
    }
}

@Composable
private fun GitHubWorkflowSection(workflows: GitHubWorkflowSnapshot) {
    Spacer(Modifier.height(4.dp))
    VNextSectionTitle(stringResource(R.string.github_actions_title), R.drawable.vnext_check)
    val stateResource = when (workflows.state) {
        GitHubWorkflowState.SUCCESS -> R.string.github_workflow_state_success
        GitHubWorkflowState.FAILURE -> R.string.github_workflow_state_failure
        GitHubWorkflowState.PENDING -> R.string.github_workflow_state_pending
        GitHubWorkflowState.NO_WORKFLOWS -> R.string.github_workflow_state_none
        GitHubWorkflowState.UNKNOWN -> R.string.github_workflow_state_unknown
    }
    VNextStatusPill(
        stringResource(stateResource),
        warning = workflows.state in setOf(GitHubWorkflowState.FAILURE, GitHubWorkflowState.PENDING, GitHubWorkflowState.UNKNOWN)
    )
    workflows.latest?.let {
        ParagraphText(stringResource(R.string.github_workflow_run, it.runNumber, it.name), style = ProjectTextStyles.supporting)
    }
    ParagraphText(
        stringResource(R.string.github_ci_evidence_note),
        style = ProjectTextStyles.metadata,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GitHubIssuesSection(issues: GitHubIssuesSnapshot) {
    Spacer(Modifier.height(4.dp))
    VNextSectionTitle(stringResource(R.string.github_issues_title), R.drawable.vnext_route)
    ParagraphText(
        stringResource(R.string.github_issues_counts, issues.openCount, issues.closedCount),
        style = ProjectTextStyles.supporting
    )
    GitHubIssueList(stringResource(R.string.github_issue_open), issues.open)
    GitHubIssueList(stringResource(R.string.github_issue_closed), issues.closed)
}

@Composable
private fun GitHubIssueList(title: String, issues: List<GitHubIssue>) {
    ParagraphText(title, style = ProjectTextStyles.metadata, fontWeight = FontWeight.SemiBold)
    if (issues.isEmpty()) {
        ParagraphText(stringResource(R.string.github_no_issues), style = ProjectTextStyles.metadata)
    } else {
        issues.forEach { issue ->
            ParagraphText("#${issue.number} · ${issue.title}", style = ProjectTextStyles.supporting)
        }
    }
}

@Composable
private fun GitHubChangesSection(history: GitHubSnapshotHistory) {
    Spacer(Modifier.height(4.dp))
    VNextSectionTitle(stringResource(R.string.github_changes_title), R.drawable.vnext_info)
    val changes = history.changes
    if (!changes.hasPrevious) {
        ParagraphText(stringResource(R.string.github_first_snapshot), style = ProjectTextStyles.supporting)
        return
    }
    var hasChanges = false
    changes.newCommitCount?.let {
        hasChanges = hasChanges || it > 0 || changes.commitHeadChanged
        when {
            it > 0 -> ParagraphText(
                pluralStringResource(R.plurals.github_new_commits, it, it),
                style = ProjectTextStyles.supporting
            )
            changes.commitHeadChanged ->
                ParagraphText(stringResource(R.string.github_commit_head_changed), style = ProjectTextStyles.supporting)
        }
    } ?: ParagraphText(stringResource(R.string.github_commit_comparison_unavailable), style = ProjectTextStyles.supporting)
    if (changes.metadataChanges.isNotEmpty()) {
        hasChanges = true
        changes.metadataChanges.forEach { field ->
            ParagraphText(
                stringResource(R.string.github_metadata_changed, stringResource(metadataFieldLabel(field))),
                style = ProjectTextStyles.supporting
            )
        }
    }
    if (changes.openIssueDelta != 0 || changes.closedIssueDelta != 0) {
        hasChanges = true
        ParagraphText(
            stringResource(R.string.github_issue_count_delta, changes.openIssueDelta ?: 0, changes.closedIssueDelta ?: 0),
            style = ProjectTextStyles.supporting
        )
    }
    changes.issueChanges.forEach { issue ->
        hasChanges = true
        val resource = when (issue.kind) {
            GitHubIssueChangeKind.CREATED -> R.string.github_issue_change_created
            GitHubIssueChangeKind.CLOSED -> R.string.github_issue_change_closed
            GitHubIssueChangeKind.REOPENED -> R.string.github_issue_change_reopened
            GitHubIssueChangeKind.UPDATED -> R.string.github_issue_change_updated
        }
        ParagraphText(stringResource(resource, issue.number, issue.title), style = ProjectTextStyles.supporting)
    }
    changes.workflowChange?.let {
        hasChanges = true
        val state = stringResource(workflowStateLabel(it.currentState))
        ParagraphText(stringResource(R.string.github_workflow_changed, state), style = ProjectTextStyles.supporting)
    }
    if (!hasChanges) ParagraphText(stringResource(R.string.github_no_changes), style = ProjectTextStyles.supporting)
}

@Composable
private fun refreshSectionLabel(section: GitHubRefreshSection): String = stringResource(when (section) {
    GitHubRefreshSection.REPOSITORY -> R.string.github_refresh_section_repository
    GitHubRefreshSection.BRANCH -> R.string.github_refresh_section_branch
    GitHubRefreshSection.ACTIONS -> R.string.github_refresh_section_actions
    GitHubRefreshSection.ISSUES -> R.string.github_refresh_section_issues
    GitHubRefreshSection.HISTORY -> R.string.github_refresh_section_history
})

@Composable
private fun readErrorLabel(error: GitHubReadError): String = stringResource(when (error) {
    GitHubReadError.EXPIRED -> R.string.github_read_error_expired
    GitHubReadError.FORBIDDEN -> R.string.github_read_error_forbidden
    GitHubReadError.NOT_FOUND -> R.string.github_read_error_not_found
    GitHubReadError.EMPTY -> R.string.github_read_error_empty
    GitHubReadError.TOO_LARGE -> R.string.github_repos_document_too_large
    GitHubReadError.RATE_LIMIT -> R.string.github_read_error_rate_limit
    GitHubReadError.NETWORK -> R.string.github_read_error_network
    GitHubReadError.TIMEOUT -> R.string.github_read_error_timeout
    GitHubReadError.INVALID_RESPONSE -> R.string.github_read_error_invalid_response
    GitHubReadError.HTTP -> R.string.github_read_error_http
})

private fun metadataFieldLabel(field: GitHubMetadataField): Int = when (field) {
    GitHubMetadataField.NAME -> R.string.github_metadata_name
    GitHubMetadataField.DESCRIPTION -> R.string.github_metadata_description
    GitHubMetadataField.DEFAULT_BRANCH -> R.string.github_metadata_default_branch
    GitHubMetadataField.VISIBILITY -> R.string.github_metadata_visibility
    GitHubMetadataField.ARCHIVED -> R.string.github_metadata_archived
}

private fun workflowStateLabel(state: GitHubWorkflowState): Int = when (state) {
    GitHubWorkflowState.SUCCESS -> R.string.github_workflow_state_success
    GitHubWorkflowState.FAILURE -> R.string.github_workflow_state_failure
    GitHubWorkflowState.PENDING -> R.string.github_workflow_state_pending
    GitHubWorkflowState.NO_WORKFLOWS -> R.string.github_workflow_state_none
    GitHubWorkflowState.UNKNOWN -> R.string.github_workflow_state_unknown
}

private fun String.toLocalizedDate(context: android.content.Context): String = try {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(Instant.parse(this).toEpochMilli()))
} catch (_: DateTimeParseException) {
    this
}

@Composable
private fun OverviewFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ParagraphText(label, style = ProjectTextStyles.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ParagraphText(value, style = ProjectTextStyles.supporting, fontWeight = FontWeight.SemiBold)
    }
}
