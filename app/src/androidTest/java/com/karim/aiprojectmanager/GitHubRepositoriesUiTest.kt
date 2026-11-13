package com.karim.aiprojectmanager

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubRepositoriesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsRepositoriesAcrossPagesAndFiltersLoadedResults() {
        val next = GitHubRepositoryCursor(2)
        val seen = mutableListOf<GitHubRepositoryCursor>()
        val source = GitHubRepositorySource { cursor ->
            seen += cursor
            if (cursor == next) GitHubRepositoryPage(listOf(repo(2, true)), null)
            else GitHubRepositoryPage(listOf(repo(1, false)), next)
        }
        compose.setContent { AppTheme(true) { GitHubRepositoriesDialog(source, {}, {}) } }
        compose.onNodeWithText("owner/repo-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.github_repos_more)).performScrollTo().performClick()
        compose.onNodeWithText("owner/repo-2").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(label(R.string.github_repos_private)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("owner/repo-1").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.github_repos_search)).performScrollTo().performTextInput("absent")
        compose.onNodeWithText(label(R.string.github_repos_no_matches)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(GitHubRepositoryCursor(), next), seen) }
    }

    @Test fun previewsImportAndSyncWithoutChangingExistingProjectBytes() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val fixture = File.createTempFile("github-preview", ".json", cache)
        val bytes = """[{"id":"local-1","name":"owner/repo-1","description":"Original","roadmap":"Keep","tasks":[{"id":"t","title":"Keep me","status":"VERIFIED"}],"legacy":{"keep":true}}]""".encodeToByteArray()
        fixture.writeBytes(bytes)
        try {
            val projects = ProjectStorage(fixture).load()
            val repository = repo(1, true)
            val source = GitHubRepositorySource { GitHubRepositoryPage(listOf(repository), null) }
            var snapshotCalls = 0
            val detail = object : GitHubRepositoryPreviewSource {
                override suspend fun branch(repository: GitHubRepository) =
                    GitHubBranchPreview("main", false, "old-head", "tree", "Initial commit", null)
                override suspend fun readme(repository: GitHubRepository, commitSha: String) =
                    GitHubReadmePreview("Sample README", false)
                override suspend fun tree(repository: GitHubRepository, treeSha: String) =
                    GitHubTreePreview(listOf(GitHubTreeEntry("sample.txt", false)), false)
                override suspend fun snapshot(repository: GitHubRepository): GitHubRemoteSnapshot {
                    snapshotCalls++
                    return GitHubRemoteSnapshot(repository, "new-head")
                }
            }
            compose.setContent {
                AppTheme(true) {
                    GitHubRepositoriesDialog(
                        source, {}, {}, previewSource = detail, localProjects = projects,
                        onImport = { GitHubImportOutcome.IMPORTED }
                    )
                }
            }
            clickLabel(R.string.github_repos_preview)
            clickLabel(R.string.github_repos_import_preview)
            val duplicate = compose.activity.getString(R.string.github_repos_import_duplicate, "owner/repo-1")
            compose.onNodeWithText(duplicate).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label(R.string.github_repos_import_action)).performScrollTo().assertIsNotEnabled()
            clickLabel(R.string.github_repos_sync_compare)
            compose.onNodeWithText(label(R.string.github_repos_sync_conflict)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label(R.string.github_repos_sync_head)).performScrollTo().assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sample README"))
            compose.onNodeWithText("Sample README").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, snapshotCalls) }
            assertArrayEquals(bytes, fixture.readBytes())
        } finally {
            fixture.delete()
        }
    }

    @Test fun importRequiresExplicitConfirmationBeforeCallingOwner() {
        val repository = repo(45, false)
        val source = GitHubRepositorySource { GitHubRepositoryPage(listOf(repository), null) }
        val detail = object : GitHubRepositoryPreviewSource {
            override suspend fun branch(repository: GitHubRepository) =
                GitHubBranchPreview("main", false, "head", "tree", "Initial commit", null)
            override suspend fun readme(repository: GitHubRepository, commitSha: String) =
                GitHubReadmePreview("", false)
            override suspend fun tree(repository: GitHubRepository, treeSha: String) =
                GitHubTreePreview(emptyList(), false)
            override suspend fun snapshot(repository: GitHubRepository) = GitHubRemoteSnapshot(repository, "head")
        }
        var imported: GitHubRepository? = null
        compose.setContent {
            AppTheme(true) {
                GitHubRepositoriesDialog(
                    source, {}, {}, previewSource = detail, localProjects = emptyList(),
                    onImport = { imported = it; GitHubImportOutcome.IMPORTED }
                )
            }
        }

        clickLabel(R.string.github_repos_preview)
        clickLabel(R.string.github_repos_import_preview)
        clickLabel(R.string.github_repos_import_action)
        compose.onNodeWithText(label(R.string.github_repos_import_confirm_title)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, imported) }
        compose.onNode(hasText(label(R.string.github_repos_import_cancel)) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(null, imported) }

        clickLabel(R.string.github_repos_import_action)
        compose.onNode(hasText(label(R.string.github_repos_import_confirm_action)) and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(repository, imported) }
    }

    @Test fun shipsArabicResourcesAndKeepsRepositoryNamesVisibleInRtl() {
        val source = GitHubRepositorySource { GitHubRepositoryPage(listOf(repo(1, true)), null) }
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val localized = compose.activity.createConfigurationContext(configuration)
        assertEquals("مستودعات GitHub", localized.getString(R.string.github_repos_title))
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                AppTheme(true) { GitHubRepositoriesDialog(source, {}, {}) }
            }
        }
        compose.onNodeWithText("owner/repo-1").performScrollTo().assertIsDisplayed()
    }

    @Test fun refreshKeepsOldResultsOnFailureAndReplacesThemOnRetry() {
        var requests = 0
        val source = GitHubRepositorySource { cursor ->
            assertEquals(GitHubRepositoryCursor(), cursor)
            when (++requests) {
                1 -> GitHubRepositoryPage(listOf(repo(1, false)), null)
                2 -> throw GitHubReadFailure(GitHubReadError.NETWORK)
                else -> GitHubRepositoryPage(listOf(repo(2, true)), null)
            }
        }
        compose.setContent { AppTheme(true) { GitHubRepositoriesDialog(source, {}, {}) } }
        compose.onNodeWithText("owner/repo-1").performScrollTo().assertIsDisplayed()
        clickLabel(R.string.github_repos_refresh)
        compose.onNodeWithText(label(R.string.github_repos_network)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("owner/repo-1").performScrollTo().assertIsDisplayed()
        clickLabel(R.string.github_repos_retry)
        compose.onNodeWithText("owner/repo-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("owner/repo-1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, requests) }
    }

    private fun clickLabel(id: Int) {
        compose.onNode(hasText(label(id)) and hasClickAction()).performScrollTo().performClick()
    }

    private fun repo(id: Long, private: Boolean) = GitHubRepository(id, "owner/repo-$id", null, "main", private, false)
    private fun label(id: Int) = compose.activity.getString(id)
}
