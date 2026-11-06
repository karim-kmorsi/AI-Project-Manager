package com.karim.aiprojectmanager

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProjectCreationAccessibilityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun namedCreateButtonKeepsSymbolAndOpensExistingDialogWithoutSavingOnCancel() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "create-accessibility-${System.nanoTime()}")
        assertTrue(root.mkdir())
        val file = File(root, "projects.json")
        file.writeText("""[{"id":"keep","name":"Existing project","legacy":42,"tasks":[]}]""")
        val before = file.readBytes()
        val visible = mutableStateOf(true)
        try {
            compose.setContent { if (visible.value) AppTheme { ProjectManagerApp(ProjectStorage(file)) } }
            val create = compose.onNodeWithContentDescription("إنشاء مشروع")
                .assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            create.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            // Retain the existing symbol/semantics selector used by workflow regressions.
            compose.onNodeWithText("+").assertIsDisplayed()
            create.performClick()
            compose.onNodeWithText("إنشاء مشروع").assertIsDisplayed()
            compose.onNodeWithText("إنشاء").assertIsNotEnabled()
            compose.onNodeWithText("اسم المشروع").performTextReplacement("Unsaved synthetic project")
            compose.onNodeWithText("إنشاء").assertIsEnabled()
            compose.onNodeWithText("إلغاء").performClick()
            compose.onNodeWithText("إنشاء مشروع").assertDoesNotExist()
            compose.onNodeWithContentDescription("إنشاء مشروع").assertIsDisplayed()
            compose.runOnIdle {
                assertArrayEquals(before, file.readBytes())
                assertEquals(listOf("projects.json"), root.list()!!.toList())
            }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }
}
