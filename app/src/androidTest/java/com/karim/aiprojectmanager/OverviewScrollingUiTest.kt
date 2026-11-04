package com.karim.aiprojectmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OverviewScrollingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longOverviewEndIsReachableWithFixedNavigationAndIndependentProjectScroll() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "overview-scroll-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val file = File(root, "test-projects.json")
        val storage = ProjectStorage(file)
        val first = ProjectItem("first", "Long overview", "First description", "Blueprint", "Roadmap",
            (1..40).map { TaskItem("task-$it", "Pending task $it") })
        val second = first.copy(id = "second", name = "Second overview", description = "Second description")
        storage.save(listOf(first, second))
        val original = file.readBytes()
        try {
            compose.setContent { ProjectManagerApp(storage) }
            compose.onNodeWithText(first.name).performClick()
            val footer = "GitHub والفحص الآلي: غير متصلين في هذه النسخة."
            compose.onNodeWithText(footer).assertIsNotDisplayed()
            compose.onNodeWithText(footer).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("نظرة عامة").assertIsDisplayed()
            compose.onNodeWithText("رجوع").assertIsDisplayed()
            compose.onNodeWithText("Pending task 40 — لم تبدأ").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Blueprint").performScrollTo().performClick()
            compose.onNodeWithText("محتوى المستند").assertIsDisplayed()
            compose.onNodeWithText("نظرة عامة").performScrollTo().performClick()
            compose.onNodeWithText(footer).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("رجوع").performClick()
            compose.onNodeWithText(second.name).performClick()
            compose.onNodeWithText(second.description).assertIsDisplayed()
            compose.onNodeWithText(footer).assertIsNotDisplayed()
            compose.onNodeWithText(footer).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertArrayEquals(original, file.readBytes()) }
        } finally {
            root.deleteRecursively()
        }
    }
}
