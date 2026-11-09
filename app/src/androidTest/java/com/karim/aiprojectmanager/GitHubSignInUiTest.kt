package com.karim.aiprojectmanager

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
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

        click("ربط حساب GitHub")
        compose.onNodeWithText("ربط حساب GitHub").assertDoesNotExist()
        compose.onNodeWithText("DEMO-CODE").performScrollTo().assertIsDisplayed()
        click("فتح صفحة GitHub")
        compose.runOnIdle {
            val intent = requireNotNull(browserIntent)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertEquals(GITHUB_VERIFICATION_URL, intent.dataString)
            assertNull(intent.extras)
        }
        click("إلغاء الربط")
        compose.onNodeWithText("DEMO-CODE").assertDoesNotExist()
        compose.onNodeWithText("أُلغي ربط GitHub.").performScrollTo().assertIsDisplayed()
    }

    @Test fun showsOnlyTheVerifiedLoginAndCanDisconnect() {
        val allowPoll = CompletableDeferred<Unit>()
        val signIn = testSignIn(allowPoll)
        compose.setContent { AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = signIn) } }

        click("ربط حساب GitHub")
        compose.runOnIdle { allowPoll.complete(Unit) }
        compose.onNodeWithText("octocat", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("DEMO-CODE").assertDoesNotExist()
        click("قطع الاتصال")
        compose.onNodeWithText("octocat", substring = true).assertDoesNotExist()
        compose.onNodeWithText("ربط حساب GitHub").performScrollTo().assertIsDisplayed()
    }

    @Test fun anUnconfiguredBuildCannotStartAuthorization() {
        val signIn = GitHubSignIn(GitHubDeviceAuth("", { _, _, _ -> error("Unexpected request") }, { 0L }))
        compose.setContent { AppTheme(false) { SettingsScreen(onExport = {}, onPreview = {}, githubSignIn = signIn) } }

        compose.onNodeWithText("ربط حساب GitHub").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("لم يُضبط معرّف تطبيق GitHub في هذه النسخة.").performScrollTo().assertIsDisplayed()
    }

    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

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
