package com.karim.aiprojectmanager

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubProjectOverviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun englishOverviewSeparatesLocalProgressFromRemoteEvidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = storeWithHistory()
        compose.setContent {
            AppTheme(true) {
                ProjectOverviewScreen(project(), connected = true, onEdit = {}, snapshotStore = store)
            }
        }

        compose.onNodeWithText(context.getString(R.string.github_linked_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("owner/repo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.github_workflow_state_success)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.github_issues_counts, 1, 0)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.github_ci_evidence_note)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.overview_manually_completed)).performScrollTo().assertIsDisplayed()
    }

    @Test fun arabicOverviewUsesLocalizedRepositoryAndWorkflowLabelsInRtl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
            setLayoutDirection(Locale.forLanguageTag("ar"))
        }
        val localized = context.createConfigurationContext(configuration)
        val store = storeWithHistory()
        var direction = LayoutDirection.Ltr
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                val currentDirection = LocalLayoutDirection.current
                SideEffect { direction = currentDirection }
                AppTheme(true) {
                    ProjectOverviewScreen(project(), connected = true, onEdit = {}, snapshotStore = store)
                }
            }
        }

        compose.runOnIdle { assertEquals(LayoutDirection.Rtl, direction) }
        compose.onNodeWithText(localized.getString(R.string.github_linked_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.github_workflow_state_success)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.github_issues_counts, 1, 0)).performScrollTo().assertIsDisplayed()
    }

    @Test fun manualRefreshUpdatesRemoteSnapshotWithoutChangingTheLocalProject() {
        val store = storeWithHistory()
        assertTrue(store.markRefreshAttempt(42, System.currentTimeMillis()))
        val source = SyntheticRefreshSource()
        compose.setContent {
            AppTheme(true) {
                ProjectOverviewScreen(
                    project(),
                    connected = true,
                    onEdit = {},
                    repositoryApi = source,
                    snapshotStore = store
                )
            }
        }
        compose.waitUntil(5_000) {
            (store.read(42) as? GitHubSnapshotRead.Available)?.history?.current?.refreshedAt == 1_000L
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val refreshButton = compose.onNodeWithText(context.getString(R.string.github_refresh)).performScrollTo()
        compose.waitUntil(5_000) {
            try {
                refreshButton.assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        refreshButton.performClick()
        compose.waitUntil(5_000) { source.repositoryCalls.get() == 1 }
        compose.waitUntil(5_000) {
            (store.read(42) as? GitHubSnapshotRead.Available)?.history?.current?.repository?.fullName ==
                "renamed-owner/renamed-repo"
        }

        val history = (store.read(42) as GitHubSnapshotRead.Available).history
        assertEquals(1, source.repositoryCalls.get())
        assertEquals(1_000L, history.previous?.refreshedAt)
        assertEquals(2, history.newCommitCount)
        assertEquals("github:42", project().id)
        assertEquals("Local project", project().name)
        compose.onNodeWithText("renamed-owner/renamed-repo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.github_new_commits, 2, 2))
            .performScrollTo().assertIsDisplayed()
        assertTrue(history.changes.metadataChanges.contains(GitHubMetadataField.NAME))
    }

    @Test fun openingLinkedProjectAutomaticallyRefreshesWhenNoRecentAttemptExists() {
        val store = storeWithHistory()
        val source = SyntheticRefreshSource()
        compose.setContent {
            AppTheme(true) {
                ProjectOverviewScreen(
                    project(),
                    connected = true,
                    onEdit = {},
                    repositoryApi = source,
                    snapshotStore = store
                )
            }
        }

        compose.waitUntil(5_000) {
            (store.read(42) as? GitHubSnapshotRead.Available)?.history?.current?.repository?.fullName ==
                "renamed-owner/renamed-repo"
        }
        assertEquals(1, source.repositoryCalls.get())
        val history = (store.read(42) as GitHubSnapshotRead.Available).history
        assertEquals("owner/repo", history.previous?.repository?.fullName)
        assertEquals(2, history.newCommitCount)
    }

    @Test fun failedManualRefreshShowsNetworkErrorAndPreservesLastSuccessfulSnapshot() {
        val store = storeWithHistory()
        assertTrue(store.markRefreshAttempt(42, System.currentTimeMillis()))
        val source = SyntheticRefreshSource(failBranch = true)
        compose.setContent {
            AppTheme(true) {
                ProjectOverviewScreen(
                    project(),
                    connected = true,
                    onEdit = {},
                    repositoryApi = source,
                    snapshotStore = store
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val refreshButton = compose.onNodeWithText(context.getString(R.string.github_refresh)).performScrollTo()
        compose.waitUntil(5_000) {
            try {
                refreshButton.assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        refreshButton.performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.github_partial_refresh))
                .fetchSemanticsNodes().isNotEmpty()
        }

        val history = (store.read(42) as GitHubSnapshotRead.Available).history
        assertEquals(1_000L, history.current.refreshedAt)
        assertEquals("owner/repo", history.current.repository.fullName)
        val error = context.getString(
            R.string.github_refresh_error,
            context.getString(R.string.github_refresh_section_branch),
            context.getString(R.string.github_read_error_network)
        )
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
    }

    private fun project() = ProjectItem(
        "github:42",
        "Local project",
        "Unchanged local description",
        tasks = listOf(TaskItem("t1", "Manually verified", "VERIFIED"))
    )

    private fun storeWithHistory(): GitHubProjectSnapshotStore {
        val store = GitHubProjectSnapshotStore(MemorySnapshotPreferences())
        val repository = GitHubRepository(42, "owner/repo", "Remote description", "main", false, false)
        val snapshot = GitHubProjectSnapshot(
            repository,
            GitHubBranchPreview("main", false, "a".repeat(40), "b".repeat(40), "Initial commit", null),
            GitHubWorkflowSnapshot(1, workflow(1, "completed", "success")),
            GitHubIssuesSnapshot(1, 0, listOf(issue(1, "open", "Open issue", 500)), emptyList()),
            1_000
        )
        assertTrue(store.save(42, null, GitHubSnapshotHistory(null, snapshot, null)))
        return store
    }

    private fun issue(number: Long, state: String, title: String, createdAt: Long) = GitHubIssue(
        number, number, title, state, java.time.Instant.ofEpochMilli(createdAt).toString(),
        java.time.Instant.ofEpochMilli(createdAt).toString(), "https://github.com/owner/repo/issues/$number"
    )

    private fun workflow(id: Long, status: String, conclusion: String?) = GitHubWorkflowRun(
        id, "CI", id, status, conclusion, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z",
        "https://github.com/owner/repo/actions/runs/$id"
    )

    private class MemorySnapshotPreferences : GitHubSnapshotPreferences {
        private val values = mutableMapOf<String, String>()
        override fun readString(key: String) = synchronized(values) { values[key] }
        override fun writeString(key: String, value: String) = synchronized(values) {
            values[key] = value
            true
        }
    }

    private inner class SyntheticRefreshSource(private val failBranch: Boolean = false) : GitHubProjectRefreshSource {
        val repositoryCalls = AtomicInteger()
        override suspend fun repositoryById(repositoryId: Long): GitHubRepository {
            repositoryCalls.incrementAndGet()
            return GitHubRepository(42, "renamed-owner/renamed-repo", "Updated remote", "main", false, false)
        }
        override suspend fun branch(repository: GitHubRepository): GitHubBranchPreview {
            if (failBranch) throw GitHubReadFailure(GitHubReadError.NETWORK)
            return GitHubBranchPreview("main", false, "c".repeat(40), "d".repeat(40), "Second commit", null)
        }
        override suspend fun snapshot(repository: GitHubRepository) = GitHubRemoteSnapshot(repository, "c".repeat(40))
        override suspend fun readme(repository: GitHubRepository, commitSha: String) = GitHubReadmePreview("", false)
        override suspend fun tree(repository: GitHubRepository, treeSha: String) = GitHubTreePreview(emptyList(), false)
        override suspend fun workflowSnapshot(repository: GitHubRepository) =
            GitHubWorkflowSnapshot(1, workflow(2, "in_progress", null))
        override suspend fun issueSnapshot(repository: GitHubRepository) =
            GitHubIssuesSnapshot(1, 0, listOf(issue(1, "open", "Open issue", 500)), emptyList())
        override suspend fun compareCommits(repository: GitHubRepository, baseSha: String, headSha: String) =
            GitHubCommitComparison(2)
    }
}
