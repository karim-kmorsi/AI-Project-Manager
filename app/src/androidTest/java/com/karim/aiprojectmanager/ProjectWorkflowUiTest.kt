package com.karim.aiprojectmanager

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises UI callbacks with injected storage; never launches MainActivity or a document picker. */
@RunWith(AndroidJUnit4::class)
class ProjectWorkflowUiTest {
    @get:Rule val compose = createComposeRule()

    private fun withStorage(block: (File, ProjectStorage) -> Unit) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "project-workflow-ui-${System.nanoTime()}"
        )
        assertTrue(dir.mkdirs())
        try {
            block(File(dir, "test-projects.json"), ProjectStorage(File(dir, "test-projects.json")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun cancelledCreationDoesNotSaveAndValidCreationTrimsFields() = withStorage { file, storage ->
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performTextInput("   ")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performTextInput("Cancelled project")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_description)).performTextInput("Must not persist")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_cancel)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_title)).assertDoesNotExist()
        compose.runOnIdle { assertFalse(file.exists()) }

        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performTextInput("  Created project  ")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_description)).performTextInput("  Saved description  ")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).performClick()
        compose.onNodeWithText("Created project").assertExists()
        compose.onNodeWithText("Saved description").assertExists()
        compose.runOnIdle {
            val saved = ProjectStorage(file).load().single()
            assertTrue(saved.id.isNotBlank())
            assertEquals(ProjectItem(saved.id, "Created project", "Saved description"), saved)
        }
        compose.pressProjectBack()
        compose.onNodeWithText("Created project").assertExists()
    }

    @Test fun taskUiPersistsEachStatusAndUpdatesVerifiedProgressWithoutChangingOtherProjects() = withStorage { file, storage ->
        val active = ProjectItem("active", "Task project", "Description", "Blueprint", "Roadmap")
        val untouched = ProjectItem("other", "Other project", "Keep", tasks = listOf(TaskItem("old", "Existing", "VERIFIED")))
        storage.save(listOf(active, untouched))
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Task project").performClick()
        // ScrollableTabRow can place the Tasks tab off screen on narrow CI displays.
        compose.selectProjectTab(3)
        compose.onLocalizedText("Add", "إضافة").assertIsNotEnabled()
        compose.onLocalizedText("New task", "مهمة جديدة").performTextInput("   ")
        compose.onLocalizedText("Add", "إضافة").assertIsNotEnabled()
        compose.onLocalizedText("New task", "مهمة جديدة").performTextInput("New task  ")
        compose.onLocalizedText("Add", "إضافة").performClick()
        compose.onLocalizedText("Add", "إضافة").assertIsNotEnabled()
        compose.onNodeWithText("New task").assertExists()
        var taskId = ""
        compose.runOnIdle {
            val task = ProjectStorage(file).load().first().tasks.single()
            taskId = task.id
            assertTrue(taskId.isNotBlank())
            assertEquals("NOT_STARTED", task.status)
        }
        for ((button, arabicButton, status, statusLabel, arabicStatusLabel) in listOf(
            listOf("In progress", "قيد التنفيذ", "IN_PROGRESS", "In progress", "قيد التنفيذ"),
            listOf("Implemented, unverified", "كود غير متحقق", "IMPLEMENTED_UNVERIFIED", "Implemented, unverified", "منفذة دون تحقق"),
            listOf("Mark verified", "تم التحقق", "VERIFIED", "Manually verified", "موسومة كمتحقق منها يدويًا")
        )) {
            // A task card can extend past the viewport on the hosted emulator.
            val taskList = compose.onNode(hasScrollToIndexAction())
            val actionMatcher = hasText(button) or hasText(arabicButton)
            taskList.performScrollToNode(actionMatcher)
            compose.onNode(actionMatcher).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(
                    listOf(active.copy(tasks = listOf(TaskItem(taskId, "New task", status))), untouched),
                    ProjectStorage(file).load()
                )
            }
            val statusMatcher = hasText(statusLabel) or hasText(arabicStatusLabel)
            taskList.performScrollToNode(statusMatcher)
            compose.onAllNodes(statusMatcher).onFirst().performScrollTo().assertIsDisplayed()
            compose.selectProjectTab(0)
            compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_manual_count, taskCountExpression(if (status == "VERIFIED") 1 else 0, 1))).assertExists()
            compose.selectProjectTab(3)
        }
        compose.pressProjectBack()
        // The other project has one verified task too, so use the unique overview assertion above.
        compose.onNodeWithText("Task project").assertExists()
    }

    @Test fun closingPreviewAndErrorDialogsDoesNotPersistOrInvokeExport() = withStorage { file, storage ->
        storage.save(listOf(ProjectItem("live", "Local project", "Keep")))
        val original = file.readBytes()
        val preview = mutableStateOf<RestorePreview.Summary?>(null)
        val error = mutableStateOf<String?>(null)
        var dismissals = 0
        var exports = 0
        compose.setContent {
            ProjectManagerApp(
                storage,
                onExport = { exports++ },
                onRestorePreview = {
                    preview.value = RestorePreview.inspect(
                        """[{"id":"backup","name":"Backup project","tasks":[{"id":"t","title":"Pending"}]}]""".toByteArray()
                    )
                },
                restorePreview = preview.value,
                restoreError = error.value,
                onDismissPreview = { dismissals++; preview.value = null; error.value = null }
            )
        }
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_more)).performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_backup_preview)).performClick()
        compose.onBackupPreview().assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.backup_preview_counts, 1, 1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.backup_preview_project_tasks, "Backup project", 1)).assertExists()
        compose.onLocalizedText("Close", "إغلاق").performClick()
        compose.onBackupPreview().assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertArrayEquals(original, file.readBytes())
            error.value = "Invalid backup; data unchanged"
        }
        compose.onLocalizedText("Preview unavailable", "تعذّرت المعاينة").assertExists()
        compose.onNodeWithText("Invalid backup; data unchanged").assertExists()
        compose.onLocalizedText("Close", "إغلاق").performClick()
        compose.onLocalizedText("Preview unavailable", "تعذّرت المعاينة").assertDoesNotExist()
        compose.onNodeWithText("Local project").assertExists()
        compose.runOnIdle {
            assertEquals(2, dismissals)
            assertEquals(0, exports)
            assertArrayEquals(original, file.readBytes())
            assertEquals(listOf("test-projects.json"), file.parentFile!!.list()!!.toList())
        }
    }
}
