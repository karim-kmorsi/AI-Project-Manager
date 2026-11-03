package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
                compose.onNodeWithText(tab).performScrollTo().performClick()
            }
            fun assertPreserved() = compose.runOnIdle {
                assertArrayEquals(corruptBytes, corruptFile.readBytes())
                assertArrayEquals(originalProjects, file.readBytes())
            }
            openEditor()
            compose.onNodeWithText("تعذّر حفظ أو قراءة المسودة. لم تتغير بياناتك؛ التحرير متوقف حتى الاحتفاظ بالمسودة.").assertExists()
            compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Committed A")
            compose.onNodeWithText("حفظ").assertIsNotEnabled()
            assertPreserved()
            compose.onNodeWithText("رجوع").performClick()
            // A corrupt on-disk draft is preserved data, not an unsaved edit that traps navigation.
            compose.onNodeWithText("مشاريعي").assertExists()
            assertPreserved()
            openEditor()
            compose.onNodeWithText("محتوى المستند").assertIsNotEnabled()
            compose.onNodeWithText("نظرة عامة").performScrollTo().performClick()
            compose.onNodeWithText("تقدم مثبت: 0 من 0 مهمة").assertExists()
            compose.onNodeWithText(tab).performScrollTo().performClick()
            assertPreserved()
            if (failPreservation) {
                compose.onNodeWithText("الاحتفاظ بالمسودة ومتابعة التحرير").performClick()
                compose.onNodeWithText("تعذّر الاحتفاظ بالمسودة؛ لم تُحذف أي بيانات. حاول مجددًا؛ ستبقى التعديلات غير المحفوظة محمية من المغادرة.").assertExists()
                compose.onNodeWithText("محتوى المستند").assertIsNotEnabled()
                compose.onNodeWithText("حفظ").assertIsNotEnabled()
                assertPreserved()
                compose.onNodeWithText("رجوع").performClick()
                compose.onNodeWithText("مشاريعي").assertExists()
                assertPreserved()
                compose.runOnIdle { assertTrue(preservationBlocker.delete()) }
                openEditor()
            }
            compose.onNodeWithText("الاحتفاظ بالمسودة ومتابعة التحرير").performClick()
            compose.onNodeWithText("محتوى المستند").assertIsEnabled()
            compose.runOnIdle {
                val preserved = File(draftDir, "preserved").walkTopDown().filter { it.isFile }.toList()
                assertEquals(1, preserved.size)
                assertArrayEquals(corruptBytes, preserved.single().readBytes())
                assertArrayEquals(originalProjects, file.readBytes())
            }
            compose.onNodeWithText("محتوى المستند").performTextReplacement("Edited C")
            compose.onNodeWithText("حفظ").performClick()
            compose.onNodeWithText("تم الحفظ").assertExists()
            compose.onNodeWithText("رجوع").performClick()
            compose.onNodeWithText("مشاريعي").assertExists()
            openEditor()
            compose.onNodeWithText("محتوى المستند").assertTextEquals("محتوى المستند", "Edited C")
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
