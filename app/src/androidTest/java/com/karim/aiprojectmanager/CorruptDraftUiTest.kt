package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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

@RunWith(AndroidJUnit4::class)
class CorruptDraftUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blueprintCorruptionPreservesDataAndAllowsNavigationAndEditing() = checkCorruption("blueprint", "Blueprint")
    @Test fun roadmapCorruptionPreservesDataAndAllowsNavigationAndEditing() = checkCorruption("roadmap", "Roadmap")
    @Test fun blueprintPreservationFailureKeepsDataReadOnlyAndNavigable() = checkCorruption("blueprint", "Blueprint", failPreservation = true)
    @Test fun roadmapPreservationFailureKeepsDataReadOnlyAndNavigable() = checkCorruption("roadmap", "Roadmap", failPreservation = true)

    private fun checkCorruption(document: String, tab: String, failPreservation: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "corrupt-editor-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolatedContext = object : ContextWrapper(context) {
            override fun getFilesDir() = root
        }
        val visible = mutableStateOf(true)
        try {
            val project = ProjectItem("corrupt-ui", "Corrupt draft project", "Keep", "Committed A", "Committed A")
            val other = ProjectItem("other", "Other project", "Keep too")
            val file = File(root, "test-projects.json")
            val storage = ProjectStorage(file)
            storage.save(listOf(project, other))
            val originalProjects = file.readBytes()
            val draftDir = File(root, "recovery-drafts")
            val drafts = DraftStorage(draftDir)
            drafts.save(project.id, document, "Committed A", "Draft B")
            val corruptFile = draftDir.listFiles()!!.single()
            val corruptBytes = byteArrayOf(0xff.toByte(), 0, 123, 98, 114, 111, 107, 101, 110)
            corruptFile.writeBytes(corruptBytes)
            val otherDocument = if (document == "blueprint") "roadmap" else "blueprint"
            drafts.save(project.id, otherDocument, "Committed A", "Unrelated document draft")
            drafts.save(other.id, document, "", "Unrelated project draft")
            val preservationBlocker = File(draftDir, "preserved")
            if (failPreservation) preservationBlocker.writeText("Test-only storage failure")
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolatedContext) {
                    ProjectManagerApp(storage)
                }
            }
            fun openEditor() {
                compose.onNodeWithText(project.name).performClick()
                compose.selectDocumentTab(tab)
            }
            fun assertPreserved() = compose.runOnIdle {
                assertArrayEquals(corruptBytes, corruptFile.readBytes())
                assertArrayEquals(originalProjects, file.readBytes())
            }
            openEditor()
            compose.onNodeWithText(isolatedContext.getString(R.string.editor_recovery_error)).assertExists()
            compose.onDocumentContent().assertTextContains("Committed A")
            compose.onLocalizedText("Save", "حفظ").assertIsNotEnabled()
            assertPreserved()
            compose.pressProjectBack()
            compose.onNodeWithText(isolatedContext.getString(R.string.dashboard_my_projects)).assertExists()
            assertPreserved()
            openEditor()
            compose.onDocumentContent().assertIsNotEnabled()
            compose.selectProjectTab(0)
            compose.onNodeWithText(isolatedContext.getString(
                R.string.dashboard_manual_count, taskCountExpression(0, 0)
            )).assertExists()
            compose.selectDocumentTab(tab)
            assertPreserved()
            if (failPreservation) {
                compose.onNodeWithText(isolatedContext.getString(R.string.editor_preserve_and_continue)).performClick()
                compose.onNodeWithText(isolatedContext.getString(R.string.editor_preservation_failed)).assertExists()
                compose.onDocumentContent().assertIsNotEnabled()
                compose.onLocalizedText("Save", "حفظ").assertIsNotEnabled()
                assertPreserved()
                compose.pressProjectBack()
                compose.onNodeWithText(isolatedContext.getString(R.string.dashboard_my_projects)).assertExists()
                assertPreserved()
                compose.runOnIdle { assertTrue(preservationBlocker.delete()) }
                openEditor()
            }
            compose.onNodeWithText(isolatedContext.getString(R.string.editor_preserve_and_continue)).performClick()
            compose.onDocumentContent().assertIsEnabled()
            compose.runOnIdle {
                val preserved = File(draftDir, "preserved").walkTopDown().filter { it.isFile }.toList()
                assertEquals(1, preserved.size)
                assertArrayEquals(corruptBytes, preserved.single().readBytes())
                assertArrayEquals(originalProjects, file.readBytes())
            }
            compose.onDocumentContent().performTextReplacement("Edited C")
            compose.onLocalizedText("Save", "حفظ").performClick()
            compose.onLocalizedText("Saved", "تم الحفظ").assertExists()
            compose.pressProjectBack()
            compose.onNodeWithText(isolatedContext.getString(R.string.dashboard_my_projects)).assertExists()
            openEditor()
            compose.onDocumentContent().assertTextContains("Edited C")
            compose.runOnIdle {
                val expected = if (document == "blueprint") project.copy(blueprint = "Edited C")
                    else project.copy(roadmap = "Edited C")
                assertEquals(listOf(expected, other), ProjectStorage(file).load())
                assertEquals(DraftStorage.Draft("Unrelated document draft", "Committed A"), drafts.load(project.id, otherDocument))
                assertEquals(DraftStorage.Draft("Unrelated project draft", ""), drafts.load(other.id, document))
                val preserved = File(draftDir, "preserved").walkTopDown().filter { it.isFile }.toList()
                assertEquals(1, preserved.size)
                assertArrayEquals(corruptBytes, preserved.single().readBytes())
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }
}
