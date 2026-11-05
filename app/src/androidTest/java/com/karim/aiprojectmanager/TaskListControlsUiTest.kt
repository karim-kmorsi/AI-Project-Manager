package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TaskListControlsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(block: (File, ProjectStorage) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "task-controls-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val file = File(root, "projects.json")
            block(file, ProjectStorage(file))
        } finally { root.deleteRecursively() }
    }

    @Test fun searchAndStatusCombineClearAndShowEmptyResultsWithoutWriting() = fixture { file, storage ->
        storage.save(listOf(ProjectItem("p", "Search project", "", tasks = listOf(
            TaskItem("a", "Alpha pending", "NOT_STARTED"),
            TaskItem("b", "Alpha active", "IN_PROGRESS"),
            TaskItem("c", "Beta active", "IN_PROGRESS"),
            TaskItem("d", "Alpha legacy", "FUTURE_STATUS")
        ))))
        val before = file.readBytes()
        compose.setContent { MaterialTheme { ProjectManagerApp(storage) } }
        compose.onNodeWithText("Search project").performClick()
        compose.onNodeWithText("المهام").performScrollTo().performClick()
        compose.onNodeWithText("بحث في المهام").performScrollTo().performTextReplacement("  ALPHA  ")
        compose.onNodeWithText("حالة المهام: كل الحالات").performScrollTo().performClick()
        compose.onAllNodesWithText("قيد التنفيذ").onLast().performClick()
        compose.onNodeWithText("Alpha active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Alpha pending").assertDoesNotExist()
        compose.onNodeWithText("Beta active").assertDoesNotExist()
        compose.onNodeWithText("بحث في المهام").performScrollTo().performTextReplacement("missing")
        compose.onNodeWithText("لا توجد مهام تطابق خيارات البحث والتصفية.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("مسح البحث").performScrollTo().performClick()
        compose.onNodeWithText("Alpha active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Beta active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("حالة المهام: قيد التنفيذ").performScrollTo().performClick()
        compose.onNodeWithText("كل الحالات").performClick()
        compose.onNodeWithText("Alpha legacy").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun narrowLargeFontActionsStayWithinCardAndRemainUsable() = fixture { _, storage ->
        val title = "A long task title with Arabic العربية and enough words to wrap across several lines"
        storage.save(listOf(ProjectItem("p", "Layout project", "", tasks = listOf(TaskItem("t", title)))))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme { Box(Modifier.width(240.dp)) { ProjectManagerApp(storage) } }
            }
        }
        compose.onNodeWithText("Layout project").performClick()
        compose.onNodeWithText("المهام").performScrollTo().performClick()
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        val labelsAndStatuses = listOf("قيد التنفيذ" to "IN_PROGRESS",
            "كود غير متحقق" to "IMPLEMENTED_UNVERIFIED", "تم التحقق" to "VERIFIED")
        for ((label, status) in labelsAndStatuses) {
            val action = compose.onNodeWithText(label)
            action.performScrollTo().assertIsDisplayed()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("Action has positive width", bounds.right > bounds.left)
            assertTrue("Action does not overflow narrow content", bounds.left >= 0.dp && bounds.right <= 240.dp)
            action.performClick()
            compose.runOnIdle {
                assertEquals(status, storage.load().single().tasks.single().status)
                assertEquals(title, storage.load().single().tasks.single().title)
            }
        }
    }
}
