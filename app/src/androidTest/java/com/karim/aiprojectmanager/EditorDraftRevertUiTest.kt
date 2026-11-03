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

/** Controls the autosave clock and redirects editor drafts to disposable cache files. */
@RunWith(AndroidJUnit4::class)
class EditorDraftRevertUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blueprintRevertDoesNotRecoverOrAutosaveAbandonedText() = editRevertAndReopen("blueprint", "Blueprint")

    @Test fun roadmapRevertDoesNotRecoverOrAutosaveAbandonedText() = editRevertAndReopen("roadmap", "Roadmap")

    @Test fun blueprintRevertPreservesNewerRecoveryDraft() = editRevertAndReopen("blueprint", "Blueprint", preserveNewerDraft = true)

    @Test fun roadmapRevertPreservesNewerRecoveryDraft() = editRevertAndReopen("roadmap", "Roadmap", preserveNewerDraft = true)

    private fun editRevertAndReopen(document: String, tab: String, preserveNewerDraft: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "editor-revert-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir() = root
        }
        val visible = mutableStateOf(true)
        try {
            val file = File(root, "test-projects.json")
            val storage = ProjectStorage(file)
            val project = ProjectItem("edited", "Editor project", "Keep", blueprint = "A", roadmap = "A")
            val other = ProjectItem("other", "Other project", "Unchanged")
            storage.save(listOf(project, other))
            val committedBytes = file.readBytes()
            val drafts = DraftStorage(File(root, "recovery-drafts"))
            val otherDocument = if (document == "blueprint") "roadmap" else "blueprint"
            drafts.save(project.id, otherDocument, "A", "Legitimate other document draft")
            drafts.save(other.id, document, "", "Legitimate other project draft")
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolatedContext) {
                    ProjectManagerApp(storage)
                }
            }
            compose.onNodeWithText(project.name).performClick()
            compose.onNodeWithText(tab).performScrollTo().performClick()
            compose.onNodeWithText("محتوى المستند").assertExists()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("محتوى المستند").performTextReplacement("B")
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {
                assertEquals(DraftStorage.Draft("B", "A"), drafts.load(project.id, document))
                assertArrayEquals(committedBytes, file.readBytes())
                // A different valid draft must not be mistaken for this editor's abandoned B.
                if (preserveNewerDraft) drafts.save(project.id, document, "A", "C")
            }
            // Cancel B's pending autosave by restoring A, before the 700 ms debounce.
            compose.onNodeWithText("محتوى المستند").performTextReplacement("A")
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle {
                assertArrayEquals(committedBytes, file.readBytes())
                if (preserveNewerDraft) assertEquals(DraftStorage.Draft("C", "A"), drafts.load(project.id, document))
            }
            // Navigation may need scrolling frames on narrow displays; the revert is now complete.
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("رجوع").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText(project.name).performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText(tab).performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(64)
            // Also advance beyond any autosave scheduled when reopening the editor.
            compose.mainClock.advanceTimeBy(1000)
            val expectedText = if (preserveNewerDraft) "C" else "A"
            val expectedProject = if (document == "blueprint") project.copy(blueprint = expectedText)
                else project.copy(roadmap = expectedText)
            compose.runOnIdle {
                if (!preserveNewerDraft) {
                    assertArrayEquals("Abandoned text must never reach project storage", committedBytes, file.readBytes())
                }
                assertEquals(listOf(expectedProject, other), ProjectStorage(file).load())
                assertNull(drafts.load(project.id, document))
                assertEquals(DraftStorage.Draft("Legitimate other document draft", "A"), drafts.load(project.id, otherDocument))
                assertEquals(DraftStorage.Draft("Legitimate other project draft", ""), drafts.load(other.id, document))
            }
            compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", expectedText)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }
}
