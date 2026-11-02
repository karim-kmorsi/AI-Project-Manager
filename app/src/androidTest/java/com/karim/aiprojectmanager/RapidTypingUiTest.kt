package com.karim.aiprojectmanager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RapidTypingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun rapidSequentialTypingRetainsLatestDocumentAfterAutosave() {
        val activity = compose.activity
        val folder = File(activity.filesDir, "ui-rapid-test-${System.nanoTime()}")
        val store = ProjectStorage(File(folder, "projects.json"))
        val project = ProjectItem("rapid-ui", "Rapid typing test", "", blueprint = "initial")
        folder.mkdirs()
        store.save(listOf(project))
        try {
            compose.setContent { ProjectManagerApp(store) }
            compose.onNodeWithText("Rapid typing test").performClick()
            compose.onNodeWithText("Blueprint").performClick()
            compose.onNodeWithText("محتوى المستند").performTextInput(" first second third")
            compose.waitUntil(15000) {
                store.load().single().blueprint == "initial first second third"
            }
            assertEquals("initial first second third", store.load().single().blueprint)
            compose.onNodeWithText("تم الحفظ").assertExists()
        } finally {
            // Test data has an isolated, unique directory; no real app project file is touched.
            folder.deleteRecursively()
        }
    }
}
