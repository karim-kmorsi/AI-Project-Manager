package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated fixture only; no production project/preference files are opened. */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var exports = 0
    private var previews = 0

    private fun withProjects(block: (File, StateRestorationTester) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "settings-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir() = root
        }
        val file = File(root, "projects.json")
        // Unknown fields and existing status values must survive navigation byte-for-byte.
        file.writeText("""[{"id":"one","name":"Alpha","description":"Keep","blueprint":"Original","roadmap":"Roadmap","legacy":{"keep":true},"tasks":[{"id":"task","title":"Keep task","status":"VERIFIED","legacyTask":7}]},{"id":"two","name":"Beta","description":"Other"}]""")
        val visible = mutableStateOf(true)
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolatedContext) {
                    MaterialTheme {
                        ProjectManagerApp(ProjectStorage(file), onExport = { exports++ }, onRestorePreview = { previews++ })
                    }
                }
            }
            block(file, restoration)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun settingsAction() = compose.onNode(hasClickAction() and hasContentDescription(text(R.string.settings_title)))

    private fun openSettings() {
        settingsAction().assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.settings_connections)).assertIsDisplayed()
    }

    @Test fun toolbarBackPreservesSearchAndProjectBytes() = withProjects { file, _ ->
        val before = file.readBytes()
        compose.onNodeWithTag("dashboard_search").performTextReplacement("Alpha")
        openSettings()
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithTag("dashboard_search").assertTextContains("Alpha")
        compose.onNodeWithText("Beta").assertDoesNotExist()
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun systemBackAfterRestorationReturnsToFilteredListWithoutWrites() = withProjects { file, restoration ->
        val before = file.readBytes()
        compose.onNodeWithTag("dashboard_search").performTextReplacement("Alpha")
        openSettings()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.settings_data_backup)).assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("dashboard_search").assertTextContains("Alpha")
        compose.onNodeWithText("Beta").assertDoesNotExist()
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun disclosureAndExistingCallbacksAreTruthfulAndDoNotSaveProjects() = withProjects { file, _ ->
        val before = file.readBytes()
        openSettings()
        listOf(R.string.settings_backup_loss, R.string.settings_backup_preview_warning,
            R.string.settings_backup_transfer_warning).forEach {
            compose.onNodeWithText(text(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("تصدير JSON").performScrollTo().performClick()
        compose.onNodeWithText("معاينة نسخة").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, exports)
            assertEquals(1, previews)
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun connectionAndDiagnosticActionsAreSeparatedWithoutWrites() = withProjects { file, _ ->
        val before = file.readBytes()
        openSettings()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNode(heading and hasText(text(R.string.settings_connections))).assertIsDisplayed()
        compose.onNodeWithText("ربط حساب GitHub").assertIsDisplayed()
        compose.onNode(heading and hasText(text(R.string.settings_advanced))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_diagnostics)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("اختبار اتصال GitHub العام").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun populatedOverviewOmitsNextStepAndTasksKeepManualVerificationCopy() = withProjects { file, _ ->
        val before = file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("الخطوة التالية:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("التقدم لا يُعتبر مكتملًا دون دليل مستقل.").assertExists()
        compose.onNodeWithText("المهام").performScrollTo().performClick()
        compose.onNodeWithText("الحالة VERIFIED تُسجَّل يدويًا ولا تعني تحققًا آليًا.").assertIsDisplayed()
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun settingsCannotBypassDirtyDocumentGuard() = withProjects { _, _ ->
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("Blueprint").performScrollTo().performClick()
        // Let the tab change compose the editor before holding the autosave clock.
        compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Original")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("محتوى المستند").performTextReplacement("Pending edit")
        compose.mainClock.advanceTimeBy(64)
        settingsAction().assertDoesNotExist()
        compose.onNodeWithText("رجوع").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.onNodeWithText("العودة إلى المحرر").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        settingsAction().assertDoesNotExist()
    }
}
