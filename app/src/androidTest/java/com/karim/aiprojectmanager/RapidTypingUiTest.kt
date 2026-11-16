package com.karim.aiprojectmanager

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RapidTypingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rapidSequentialTypingRetainsLatestDocumentAfterAutosave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "ui-rapid-test-${System.nanoTime()}")
        val store = ProjectStorage(File(folder, "projects.json"))
        val project = ProjectItem("rapid-ui", "Rapid typing test", "", blueprint = "initial")
        folder.mkdirs()
        store.save(listOf(project))
        try {
            compose.setContent { ProjectManagerApp(store) }
            compose.onNodeWithText("Rapid typing test").performClick()
            compose.selectProjectTab(1)
            compose.onDocumentContent().performTextClearance()
            compose.onDocumentContent().performTextInput("first second third")
            compose.waitUntil(15000) {
                store.load().single().blueprint == "first second third"
            }
            assertEquals("first second third", store.load().single().blueprint)
            compose.onLocalizedText("Saved", "تم الحفظ").assertExists()
        } finally {
            // Test data has an isolated, unique directory; no real app project file is touched.
            folder.deleteRecursively()
        }
    }
}
