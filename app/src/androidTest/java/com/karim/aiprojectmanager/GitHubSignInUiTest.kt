package com.karim.aiprojectmanager

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GitHubSignInUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun displaysTheCodeAndOpensTheOfficialPageWithoutPassingItToAndroid() {
        val allowPoll = CompletableDeferred<Unit>()
        var browserIntent: Intent? = null
        val signIn = testSignIn(allowPoll)
        compose.setContent {
            val baseContext = LocalContext.current
            val context = object : ContextWrapper(baseContext) {
                override fun startActivity(intent: Intent) { browserIntent = intent }
            }
            CompositionLocalProvider(LocalContext provides context) {
                AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = signIn) }
            }
        }

        click(R.string.github_connect)
        compose.onNodeWithText(text(R.string.github_connect)).assertDoesNotExist()
        compose.onNodeWithText("DEMO-CODE").performScrollTo().assertIsDisplayed()
        click(R.string.github_open_authorization)
        compose.runOnIdle {
            val intent = requireNotNull(browserIntent)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertEquals(GITHUB_VERIFICATION_URL, intent.dataString)
            assertNull(intent.extras)
        }
        click(R.string.github_cancel_connection)
        compose.onNodeWithText("DEMO-CODE").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.github_status_cancelled)).performScrollTo().assertIsDisplayed()
    }

    @Test fun showsOnlyTheVerifiedLoginAndCanDisconnect() {
        val allowPoll = CompletableDeferred<Unit>()
        val signIn = testSignIn(allowPoll)
        compose.setContent { AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = signIn) } }

        click(R.string.github_connect)
        compose.runOnIdle { allowPoll.complete(Unit) }
        compose.onNodeWithText("octocat", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DEMO-CODE").assertDoesNotExist()
        click(R.string.github_disconnect)
        compose.onNodeWithText("octocat", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.github_connect)).performScrollTo().assertIsDisplayed()
    }

    @Test fun installedBuildConfigurationControlsConnectAvailability() {
        compose.setContent { AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = GitHubSignIn()) } }

        val connect = compose.onNodeWithText(text(R.string.github_connect)).performScrollTo()
        if (BuildConfig.GITHUB_APP_CLIENT_ID.isEmpty()) connect.assertIsNotEnabled()
        else connect.assertIsEnabled()
    }

    @Test fun anUnconfiguredBuildCannotStartAuthorization() {
        val signIn = GitHubSignIn(GitHubDeviceAuth("", { _, _, _ -> error("Unexpected request") }, { 0L }))
        compose.setContent { AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = signIn) } }

        compose.onNodeWithText(text(R.string.github_connect)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.github_status_not_configured)).performScrollTo().assertIsDisplayed()
    }

    private fun text(resourceId: Int) = compose.activity.getString(resourceId)

    private fun click(resourceId: Int) = compose.onNodeWithText(text(resourceId)).performScrollTo().performClick()

    private fun testSignIn(allowPoll: CompletableDeferred<Unit>) = GitHubSignIn(
        GitHubDeviceAuth("SyntheticClient", { endpoint, _, _ ->
            when (endpoint) {
                GitHubAuthEndpoint.DEVICE_CODE -> mapOf(
                    "device_code" to "a".repeat(40),
                    "user_code" to "DEMO-CODE",
                    "verification_uri" to GITHUB_VERIFICATION_URL,
                    "expires_in" to 900,
                    "interval" to 5
                )
                GitHubAuthEndpoint.TOKEN -> mapOf("access_token" to "ghu_SyntheticToken", "token_type" to "bearer", "scope" to "")
                GitHubAuthEndpoint.USER -> mapOf("login" to "octocat")
            }
        }, { 0L }, { allowPoll.await() })
    )
}
