package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests explicit Save and dirty-editor guards without touching the app's real files.
 * The Compose clock is held before the 700 ms autosave interval.
 */
@RunWith(AndroidJUnit4::class)
class ManualSaveNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blueprintManualSavePersistsBeforeAutosave() = checkManualSave("blueprint", "Blueprint")
    @Test fun roadmapManualSavePersistsBeforeAutosave() = checkManualSave("roadmap", "Roadmap")
    @Test fun blueprintSaveFailureBlocksBackAndTabs() = checkSaveFailure("blueprint", "Blueprint")
    @Test fun roadmapSaveFailureBlocksBackAndTabs() = checkSaveFailure("roadmap", "Roadmap")

    private fun withIsolatedProject(block: (File, ProjectStorage, ProjectItem, DraftStorage) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "manual-save-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir() = root
        }
        val file = File(root, "test-projects.json")
        val storage = ProjectStorage(file)
        val project = ProjectItem("manual-save", "Manual save project", "Keep", "Original", "Original")
        val other = ProjectItem("other", "Other project", "Never change")
        val drafts = DraftStorage(File(root, "recovery-drafts"))
        val visible = mutableStateOf(true)
        try {
            storage.save(listOf(project, other))
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolatedContext) {
                    ProjectManagerApp(storage)
                }
            }
            block(file, storage, project, drafts)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }

    private fun openEditor(tab: String) {
        compose.onNodeWithText("Manual save project").performClick()
        compose.onNodeWithText(tab).performScrollTo().performClick()
        compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Original")
    }

    private fun editedProject(project: ProjectItem, document: String) =
        if (document == "blueprint") project.copy(blueprint = "Manual edit")
        else project.copy(roadmap = "Manual edit")

    private fun checkManualSave(document: String, tab: String) = withIsolatedProject { file, storage, project, drafts ->
        val original = file.readBytes()
        openEditor(tab)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("محتوى المستند").performTextReplacement("Manual edit")
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertArrayEquals(original, file.readBytes())
            assertEquals(DraftStorage.Draft("Manual edit", "Original"), drafts.load(project.id, document))
        }
        compose.onNodeWithText("حفظ").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تم الحفظ").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(editedProject(project, document), ProjectItem("other", "Other project", "Never change")), storage.load())
            assertNull(drafts.load(project.id, document))
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithText("مشاريعي").assertExists()
        compose.onNodeWithText(project.name).performClick()
        compose.onNodeWithText(tab).performScrollTo().performClick()
        compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Manual edit")
    }

    private fun checkSaveFailure(document: String, tab: String) = withIsolatedProject { file, storage, project, drafts ->
        openEditor(tab)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("محتوى المستند").performTextReplacement("Manual edit")
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertEquals(DraftStorage.Draft("Manual edit", "Original"), drafts.load(project.id, document))
            // Deliberately break ONLY this test's disposable projects file.
            file.writeText("invalid JSON to block writes")
        }
        val rejectedBytes = file.readBytes()
        compose.onNodeWithText("حفظ").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("فشل حفظ المشروع — المسودة الاحتياطية محفوظة إن أمكن").assertExists()
        // Navigation includes animated tab scrolling. A paused clock deadlocks performScrollTo
        // when Roadmap has moved Overview off screen. The corrupt fixture still blocks autosave.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("رجوع").performClick()
        // Render the guard before asserting it.
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.onNodeWithText("العودة إلى المحرر").performClick()
        compose.onNodeWithText("نظرة عامة").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.onNodeWithText("العودة إلى المحرر").performClick()
        compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Manual edit")
        compose.runOnIdle {
            assertArrayEquals(rejectedBytes, file.readBytes())
            assertEquals(DraftStorage.Draft("Manual edit", "Original"), drafts.load(project.id, document))
            // Restore only the disposable fixture, allowing a safe retry.
            file.delete()
            storage.save(listOf(project, ProjectItem("other", "Other project", "Never change")))
        }
        compose.onNodeWithText("حفظ").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تم الحفظ").assertExists()
        compose.runOnIdle {
            assertEquals("Manual edit", if (document == "blueprint") storage.load()[0].blueprint else storage.load()[0].roadmap)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithText("مشاريعي").assertExists()
    }
}
