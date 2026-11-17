package com.karim.aiprojectmanager

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual ActivityScenario.recreate, unlike StateRestorationTester-only coverage. */
@RunWith(AndroidJUnit4::class)
class ActivityRecreationUiTest {
    @get:Rule val compose = createAndroidComposeRule<IsolatedWorkflowActivity>()
    @After fun cleanFixture() {
        compose.mainClock.autoAdvance = true
        val root = compose.activity.fixtureRoot
        compose.activityRule.scenario.close()
        root.deleteRecursively()
    }
    @Test fun blueprintPendingDraftSurvivesActivityRecreationAndSystemBack() = pendingDraft("Blueprint", "blueprint")
    @Test fun roadmapPendingDraftSurvivesActivityRecreationAndSystemBack() = pendingDraft("Roadmap", "roadmap")
    @Test fun blueprintFailedSavePreservesSavedBytesAcrossRecreationAndRetry() = failedSave("Blueprint", "blueprint", "Roadmap")
    @Test fun roadmapFailedSavePreservesSavedBytesAcrossRecreationAndRetry() = failedSave("Roadmap", "roadmap", "Blueprint")

    private fun openEditor(tab: String) {
        compose.onNodeWithText("Lifecycle project").performClick()
        compose.selectDocumentTab(tab)
        compose.onDocumentContent().assertExists()
    }
    private fun pendingDraft(tab: String, document: String) {
        val file = File(compose.activity.filesDir, "projects.json")
        val saved = file.readBytes()
        val drafts = DraftStorage(File(compose.activity.filesDir, "recovery-drafts"))
        openEditor(tab)
        compose.mainClock.autoAdvance = false
        compose.onDocumentContent().performTextReplacement("Pending تعديل 123")
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals("Pending تعديل 123", drafts.load("active", document)?.text); assertArrayEquals(saved, file.readBytes()) }
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(64)
        compose.onDocumentContent().assertTextContains("Pending تعديل 123")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(64)
        compose.onUnsavedChanges().assertExists()
        compose.runOnIdle { assertArrayEquals(saved, file.readBytes()) }
        compose.onLocalizedText("Return to editor", "العودة إلى المحرر").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            val result = ProjectStorage(file).load()
            assertEquals("Pending تعديل 123", if (document == "blueprint") result[0].blueprint else result[0].roadmap)
            assertEquals("Other Blueprint", result[1].blueprint)
            assertNull(drafts.load("active", document))
        }
    }
    private fun failedSave(tab: String, document: String, nextTab: String) {
        val file = File(compose.activity.filesDir, "projects.json")
        val original = ProjectStorage(file).load()
        val bytes = file.readBytes()
        val blocked = File(file.path + ".new").apply { assertTrue(mkdir()); File(this, "keep-blocked").writeText("Disposable failure injection") }
        openEditor(tab)
        compose.onDocumentContent().performTextReplacement("Failed then retained")
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.editor_save_error)).assertExists()
        compose.runOnIdle { assertArrayEquals(bytes, file.readBytes()) }
        compose.activityRule.scenario.recreate()
        // VNext3 startup refuses to read data while an AtomicFile .new sidecar
        // exists; verify safe failure instead of bypassing the recovery gate.
        compose.onLocalizedText("Retry", "إعادة المحاولة").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(blocked.deleteRecursively())
        }
        compose.onLocalizedText("Retry", "إعادة المحاولة").performClick()
        compose.onDocumentContent().assertTextContains("Failed then retained")
        compose.selectDocumentTab(nextTab)
        compose.onUnsavedChanges().assertExists()
        compose.onLocalizedText("Return to editor", "العودة إلى المحرر").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onUnsavedChanges().assertExists()
        compose.onLocalizedText("Return to editor", "العودة إلى المحرر").performClick()
        compose.runOnIdle {
            assertArrayEquals(bytes, file.readBytes())
            assertEquals("Failed then retained", DraftStorage(File(file.parentFile, "recovery-drafts")).load("active", document)?.text)
            assertFalse("The test blocker was cleared before startup retry", blocked.exists())
        }
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText("Saved", "تم الحفظ").assertExists()
        compose.runOnIdle {
            val expected = original.toMutableList()
            expected[0] = if (document == "blueprint") expected[0].copy(blueprint = "Failed then retained") else expected[0].copy(roadmap = "Failed then retained")
            assertEquals(expected, ProjectStorage(file).load())
        }
        compose.activityRule.scenario.recreate()
        compose.onDocumentContent().assertTextContains("Failed then retained")
    }
}
