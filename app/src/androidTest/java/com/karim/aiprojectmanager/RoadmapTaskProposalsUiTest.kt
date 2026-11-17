package com.karim.aiprojectmanager

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local synthetic projects only; session and preferences are isolated too. */
@RunWith(AndroidJUnit4::class)
class RoadmapTaskProposalsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixtureContext: Context

    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun withProject(block: (File, ProjectStorage) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "roadmap-proposals-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            fixtureContext = isolatedWorkflowContext(
                InstrumentationRegistry.getInstrumentation().targetContext, root, File(root, "isolated-app-files")
            )
            val file = File(root, "projects.json")
            val storage = ProjectStorage(file)
            storage.save(listOf(ProjectItem(
                "project", "Roadmap project", "", roadmap = "- Implement the release checklist\n- Add an audit report",
                tasks = listOf(TaskItem("existing", "Existing task", "VERIFIED"))
            )))
            block(file, storage)
        } finally {
            compose.activityRule.scenario.close()
            assertTrue(root.deleteRecursively())
        }
    }

    private fun composeProject(storage: ProjectStorage) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides fixtureContext) {
                AppTheme(dark = false) { ProjectManagerApp(storage) }
            }
        }
    }

    private fun openProposals() {
        compose.onNodeWithText("Roadmap project").performClick()
        compose.selectProjectTab(3)
        compose.onLocalizedText("Propose tasks from roadmap", "اقتراح مهام من خارطة الطريق").performClick()
    }

    private fun proposalCheckbox(line: Int): SemanticsNodeInteraction {
        require(line in 1..2)
        // This fixture has exactly two proposals in source order. Layout-only Row/Column
        // nodes are flattened in Compose semantics, so do not infer label siblings.
        val checkboxes = compose.onAllNodes(checkbox and hasAnyAncestor(isDialog()))
        checkboxes.assertCountEquals(2)
        return checkboxes[line - 1].performScrollTo()
    }

    @Test fun cancellingProposalReviewLeavesExistingTasksUntouched() = withProject { file, storage ->
        val before = file.readBytes()
        composeProject(storage)
        openProposals()
        compose.onLocalizedText("Review proposed tasks", "مراجعة المهام المقترحة").assertIsDisplayed()
        compose.onLocalizedText("Cancel", "إلغاء").performClick()
        compose.onLocalizedText("Review proposed tasks", "مراجعة المهام المقترحة").assertDoesNotExist()
        compose.runOnIdle {
            assertArrayEquals(before, file.readBytes())
            assertEquals(listOf(TaskItem("existing", "Existing task", "VERIFIED")),
                storage.load().single().tasks)
        }
    }

    @Test fun proposalCanBeDeselectedEditedAndConfirmedWithoutChangingExistingTask() = withProject { file, storage ->
        composeProject(storage)
        openProposals()
        proposalCheckbox(2).assertIsOn().performClick()
        proposalCheckbox(2).assertIsOff()
        proposalCheckbox(1).assertIsOn().performClick()
        proposalCheckbox(1).assertIsOff()
        compose.onLocalizedText("Add selected tasks", "إضافة المهام المحددة").assertIsNotEnabled()
        proposalCheckbox(1).assertIsOff().performClick()
        proposalCheckbox(1).assertIsOn()
        compose.onNode(hasSetTextAction() and hasText("Implement the release checklist"))
            .performScrollTo().performTextReplacement("Finalize smoke test")
        compose.onLocalizedText("Add selected tasks", "إضافة المهام المحددة").performClick()
        compose.onLocalizedText("Review proposed tasks", "مراجعة المهام المقترحة").assertDoesNotExist()
        compose.runOnIdle {
            val tasks = storage.load().single().tasks
            assertEquals(2, tasks.size)
            assertEquals(TaskItem("existing", "Existing task", "VERIFIED"), tasks[0])
            assertTrue(tasks[1].id.isNotBlank())
            assertEquals("Finalize smoke test", tasks[1].title)
            assertEquals("NOT_STARTED", tasks[1].status)
            assertFalse(tasks.any { it.title == "Add an audit report" })
        }
    }
}
