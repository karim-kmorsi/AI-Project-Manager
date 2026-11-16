package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StorageFailureUiTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(block: (File, ProjectStorage) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "failure-ui-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val file = File(root, "projects.json")
            val storage = ProjectStorage(file)
            block(file, storage)
        } finally { root.deleteRecursively() }
    }
    @Test fun blueprintDraftSurvivesSavedStateRestoration() = restoreEditor("Blueprint", "blueprint")
    @Test fun roadmapDraftSurvivesSavedStateRestoration() = restoreEditor("Roadmap", "roadmap")

    private fun restoreEditor(tab: String, document: String) = fixture { file, storage ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = file.parentFile!! }
        storage.save(listOf(ProjectItem("p", "Editor project", "", "Original", "Original")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalContext provides isolated) { ProjectManagerApp(storage) }
        }
        compose.onNodeWithText("Editor project").performClick()
        compose.selectDocumentTab(tab)
        compose.onDocumentContent().assertTextContains("Original")
        compose.mainClock.autoAdvance = false
        try {
            compose.onDocumentContent().performTextReplacement("تعديل English 123")
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {
                assertEquals("تعديل English 123", DraftStorage(File(file.parentFile, "recovery-drafts")).load("p", document)?.text)
                assertEquals("Original", if (document == "blueprint") storage.load().single().blueprint else storage.load().single().roadmap)
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.mainClock.advanceTimeBy(64)
            compose.onDocumentContent().assertTextContains("تعديل English 123")
            compose.pressProjectBack()
            compose.mainClock.advanceTimeBy(64)
            compose.onUnsavedChanges().assertExists()
        } finally { compose.mainClock.autoAdvance = true; compose.waitForIdle() }
    }

    @Test fun corruptStartupShowsErrorAndBlocksCreationWithoutChangingBytes() = fixture { file, storage ->
        file.writeText("broken JSON")
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_read_error)).assertExists()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create)).assertDoesNotExist()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_retry)).performClick()
        compose.runOnIdle { assertEquals("broken JSON", file.readText()) }
        compose.runOnIdle { file.writeText("[]") }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_retry)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create)).assertExists()
    }
    @Test fun failedCreationKeepsFormAndAllowsRetry() = fixture { file, storage ->
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performTextInput("Retained name")
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_creation_error)).assertExists()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).assertTextContains("Retained name")
        compose.runOnIdle { assertEquals("broken JSON", file.readText()); file.delete() }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).performClick()
        compose.runOnIdle { assertEquals("Retained name", storage.load().single().name) }
    }
    @Test fun taskFailureRetainsInputAndDoesNotAdvanceStatus() = fixture { file, storage ->
        val original = listOf(ProjectItem("p", "Task project", "", tasks = listOf(TaskItem("t", "Existing"))))
        storage.save(original)
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Task project").performClick()
        compose.selectProjectTab(3)
        compose.onLocalizedText("New task", "مهمة جديدة").performTextInput("Retain task")
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onLocalizedText("Add", "إضافة").performClick()
        compose.onLocalizedText("Close", "إغلاق").performClick()
        compose.onLocalizedText("New task", "مهمة جديدة").assertTextContains("Retain task")
        val inProgressAction = hasText("In progress", substring = false) or
            hasText("قيد التنفيذ", substring = false)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(inProgressAction)
        compose.onNode(inProgressAction).performClick()
        compose.onLocalizedText("Close", "إغلاق").performClick()
        compose.onLocalizedText("Not started", "لم تبدأ").assertExists()
        compose.runOnIdle { assertEquals("broken JSON", file.readText()); file.delete(); storage.save(original) }
        compose.runOnIdle { assertEquals("Storage fixture must be restored before retry", original, storage.load()) }
        // The preceding status action scrolled the lazy task list past the add form.
        // Bring the original form on-screen before retrying rather than tapping it offscreen.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(
            hasText("New task") or hasText("مهمة جديدة")
        )
        compose.onLocalizedText("New task", "مهمة جديدة").assertTextContains("Retain task")
        compose.onLocalizedText("Add", "إضافة").assertIsDisplayed().performClick()
        compose.onNodeWithText("Retain task").assertExists()
        compose.runOnIdle {
            val actual = storage.load().single().tasks.map { it.title }
            assertEquals("Retry did not persist task; check UI error dialog and fixture state", listOf("Existing", "Retain task"), actual)
        }
    }
    @Test fun selectedTabAndUnsubmittedTaskSurviveSavedStateRestoration() = fixture { _, storage ->
        storage.save(listOf(ProjectItem("p", "Task project", "")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Task project").performClick()
        compose.selectProjectTab(3)
        compose.onLocalizedText("New task", "مهمة جديدة").performTextInput("Unsaved input")
        restoration.emulateSavedInstanceStateRestore()
        compose.onLocalizedText("New task", "مهمة جديدة").assertTextContains("Unsaved input")
    }
}
