package com.karim.aiprojectmanager

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

    private fun repo(id: Long, private: Boolean) = GitHubRepository(id, "owner/repo-$id", null, "main", private, false)
    private fun label(id: Int) = compose.activity.getString(id)
}
