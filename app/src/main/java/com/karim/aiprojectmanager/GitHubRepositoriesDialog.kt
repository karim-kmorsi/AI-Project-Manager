package com.karim.aiprojectmanager

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

internal enum class GitHubRepositoryFilter(val label: Int) {
    ALL(R.string.github_repos_all), PUBLIC(R.string.github_repos_public), PRIVATE(R.string.github_repos_private)
}

internal fun filterGitHubRepositories(
    repositories: List<GitHubRepository>,
    query: String,
    filter: GitHubRepositoryFilter
): List<GitHubRepository> {
    val term = query.trim()
    return repositories.filter { repository ->
        (filter == GitHubRepositoryFilter.ALL || repository.isPrivate == (filter == GitHubRepositoryFilter.PRIVATE)) &&
            (repository.fullName.contains(term, ignoreCase = true) ||
                repository.description?.contains(term, ignoreCase = true) == true)
    }
}

@Composable
internal fun GitHubRepositoriesDialog(
    source: GitHubRepositorySource,
    onExpired: () -> Unit,
    onClose: () -> Unit,
    previewSource: GitHubRepositoryPreviewSource? = null
) {
    var repositories by remember { mutableStateOf(emptyList<GitHubRepository>()) }
    var cursor by remember { mutableStateOf<GitHubRepositoryCursor?>(GitHubRepositoryCursor()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(GitHubRepositoryFilter.ALL) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<GitHubReadError?>(null) }
    var retryAt by remember { mutableStateOf(0L) }
    var retrySeconds by remember { mutableStateOf(0L) }
    var request by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<GitHubRepository?>(null) }

    LaunchedEffect(request) {
        val current = cursor ?: return@LaunchedEffect
        try {
            val page = loadGitHubRepositoryPage(source, current)
            repositories = (repositories + page.repositories).distinctBy { it.id }
            cursor = page.next
        } catch (failure: GitHubReadFailure) {
            if (failure.reason == GitHubReadError.EXPIRED) {
                onExpired()
            } else {
                error = failure.reason
                if (failure.reason == GitHubReadError.RATE_LIMIT) {
                    val seconds = failure.retryAfterSeconds?.coerceIn(1, Long.MAX_VALUE / 2_000) ?: 60L
                    retryAt = SystemClock.elapsedRealtime() + seconds * 1_000
                    retrySeconds = seconds
                }
            }
        } finally {
            loading = false
        }
    }
    LaunchedEffect(retryAt) {
        while (retrySeconds > 0) {
            delay(1_000)
            retrySeconds = ((retryAt - SystemClock.elapsedRealtime() + 999) / 1_000).coerceAtLeast(0)
        }
    }

    val visible = remember(repositories, query, filter) { filterGitHubRepositories(repositories, query, filter) }
    val selectedRepository = selected
    if (selectedRepository != null && previewSource != null) {
        GitHubRepositoryPreviewDialog(selectedRepository, previewSource, onExpired) { selected = null }
        return
    }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParagraphText(stringResource(R.string.github_repos_title), style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            textStyle = contentTextStyle(),
                            label = { Text(stringResource(R.string.github_repos_search)) }
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GitHubRepositoryFilter.entries.forEach { choice ->
                                FilterChip(selected = filter == choice, onClick = { filter = choice },
                                    label = { Text(stringResource(choice.label)) })
                            }
                        }
                    }
                    items(visible, key = { it.id }) { repository ->
                        GitHubRepositorySummary(repository)
                        if (previewSource != null) {
                            TextButton(onClick = { selected = repository }) { Text(stringResource(R.string.github_repos_preview)) }
                        }
                    }
                    item {
                        val currentError = error
                        val status = when {
                            loading -> stringResource(R.string.github_repos_loading)
                            retrySeconds > 0 -> stringResource(R.string.github_repos_limited_wait, retrySeconds)
                            currentError != null -> stringResource(currentError.messageResource())
                            repositories.isEmpty() && cursor == null -> stringResource(R.string.github_repos_empty)
                            repositories.isEmpty() -> null
                            visible.isEmpty() -> stringResource(R.string.github_repos_no_matches)
                            else -> null
                        }
                        if (status != null) ParagraphText(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (cursor != null && !loading) {
                            TextButton(enabled = retrySeconds == 0L, onClick = {
                                if (!loading && retrySeconds == 0L) {
                                    error = null
                                    loading = true
                                    request++
                                }
                            }) {
                                Text(stringResource(if (currentError == null) R.string.github_repos_more else R.string.github_repos_retry))
                            }
                        }
                    }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.github_repos_close)) }
            }
        }
    }
}

@Composable
internal fun GitHubRepositorySummary(repository: GitHubRepository) {
    Column(Modifier.fillMaxWidth()) {
        ParagraphText(repository.fullName, style = MaterialTheme.typography.titleMedium)
        ParagraphText(stringResource(if (repository.isPrivate) R.string.github_repos_private else R.string.github_repos_public))
        if (repository.archived) ParagraphText(stringResource(R.string.github_repos_archived))
        if (repository.defaultBranch.isNotBlank()) {
            ParagraphText(stringResource(R.string.github_repos_branch, repository.defaultBranch))
        }
        repository.description?.let { ParagraphText(it, maxLines = 3) }
    }
}

internal fun GitHubReadError.messageResource(): Int = when (this) {
    GitHubReadError.EXPIRED -> R.string.github_repos_expired
    GitHubReadError.FORBIDDEN -> R.string.github_repos_forbidden
    GitHubReadError.NOT_FOUND -> R.string.github_repos_not_found
    GitHubReadError.EMPTY -> R.string.github_repos_empty_repo
    GitHubReadError.RATE_LIMIT -> R.string.github_repos_limited
    GitHubReadError.NETWORK -> R.string.github_repos_network
    GitHubReadError.TIMEOUT -> R.string.github_repos_timeout
    GitHubReadError.INVALID_RESPONSE -> R.string.github_repos_invalid
    GitHubReadError.HTTP -> R.string.github_repos_http
}
