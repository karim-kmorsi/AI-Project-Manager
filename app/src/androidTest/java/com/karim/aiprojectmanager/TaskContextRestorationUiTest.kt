package com.karim.aiprojectmanager

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated files; actual Activity recreation, never actual process death. */
@RunWith(AndroidJUnit4::class)
class TaskContextRestorationUiTest {
    @get:Rule val compose = createAndroidComposeRule<IsolatedWorkflowActivity>()
    private lateinit var file: File
    private lateinit var original: ByteArray

    @Before fun seed() {
        compose.activityRule.scenario.onActivity { activity ->
            file = File(activity.filesDir, "projects.json")
            val storage = ProjectStorage(file)
            storage.save(storage.load().mapIndexed { project, p -> p.copy(tasks = (0 until 80).map {
                TaskItem("task-$it", "${if (project == 0) "A" else "B"} task $it",
                    if (it % 2 == 0) "IN_PROGRESS" else "NOT_STARTED")
            }) })
            original = file.readBytes()
        }
        compose.activityRule.scenario.recreate()
    }
    @After fun cleanFixture() {
        val root = compose.activity.fixtureRoot
        compose.activityRule.scenario.close()
        root.deleteRecursively()
    }
    private fun list() = compose.onNode(hasScrollToIndexAction())
    private fun open(name: String) {
        compose.onNodeWithText(name).performClick()
        compose.selectProjectTab(3)
    }
    private fun select(query: String, label: String) {
        list().performScrollToNode(hasText("Search tasks") or hasText("بحث في المهام"))
        compose.onLocalizedText("Search tasks", "بحث في المهام").performTextReplacement(query)
        compose.onLocalizedText("Task status: All statuses", "حالة المهام: كل الحالات").performScrollTo().performClick()
        val englishLabel = when (label) {
            "قيد التنفيذ" -> "In progress"
            "لم تبدأ" -> "Not started"
            else -> label
        }
        compose.onAllNodes(hasText(label) or hasText(englishLabel)).onLast().performClick()
    }
    private fun position() = list().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun assertContext(query: String, status: String, scroll: Float) {
        assertEquals("Restore scroll before scrolling to inspect controls", scroll, position(), 1f)
        list().performScrollToNode(hasText("Search tasks") or hasText("بحث في المهام"))
        compose.onLocalizedText("Search tasks", "بحث في المهام").assertTextContains(query)
        val englishStatus = if (status == "قيد التنفيذ") "In progress" else "Not started"
        compose.onLocalizedText("Task status: $englishStatus", "حالة المهام: $status").assertExists()
        compose.onNode(isPopup()).assertDoesNotExist()
        assertArrayEquals(original, file.readBytes())
    }
    @Test fun queryFilterAndScrollSurviveActivityRecreationAndMenuRestoresClosed() {
        open("Lifecycle project")
        select("  A TASK  ", "قيد التنفيذ")
        list().performScrollToIndex(12)
        val scroll = position()
        compose.activityRule.scenario.recreate()
        assertContext("  A TASK  ", "قيد التنفيذ", scroll)
        compose.onLocalizedText("Task status: In progress", "حالة المهام: قيد التنفيذ").performScrollTo().performClick()
        compose.onNode(isPopup()).assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.onLocalizedText("Search tasks", "بحث في المهام").assertTextContains("  A TASK  ")
        compose.onLocalizedText("Task status: In progress", "حالة المهام: قيد التنفيذ").assertExists()
        assertArrayEquals(original, file.readBytes())
    }
    @Test fun tabRoundTripAndProjectSwitchesRetainIndependentContextsWithoutWrites() {
        open("Lifecycle project")
        select("A task", "قيد التنفيذ")
        list().performScrollToIndex(12)
        val a = position()
        compose.selectProjectTab(2)
        compose.selectProjectTab(3)
        assertEquals(a, position(), 1f)
        compose.pressProjectBack()
        open("Unrelated project")
        assertEquals(0f, position(), 1f)
        list().performScrollToNode(hasText("Search tasks") or hasText("بحث في المهام"))
        compose.onLocalizedText("Search tasks", "بحث في المهام").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onLocalizedText("Task status: All statuses", "حالة المهام: كل الحالات").assertExists()
        select("B task", "لم تبدأ")
        list().performScrollToIndex(5)
        val b = position()
        compose.pressProjectBack()
        open("Lifecycle project")
        assertContext("A task", "قيد التنفيذ", a)
        compose.pressProjectBack()
        open("Unrelated project")
        assertContext("B task", "لم تبدأ", b)
    }
    @Test fun accumulatedProjectContextsMeasureActivityBundleAndRestoreIndependently() {
        fun phase(name: String) = android.util.Log.i("SavedStatePhase", "contexts=2 phase=$name")
        phase("context-entry-start")
        open("Lifecycle project")
        select("A task", "قيد التنفيذ")
        list().performScrollToIndex(12)
        val a = position()
        compose.pressProjectBack()
        open("Unrelated project")
        select("B task", "لم تبدأ")
        list().performScrollToIndex(5)
        val b = position()
        phase("state-capture-start")
        val state = android.os.Bundle()
        compose.activityRule.scenario.onActivity {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .callActivityOnSaveInstanceState(it, state)
        }
        phase("state-captured")
        val parcel = android.os.Parcel.obtain()
        val bytes = try { parcel.writeBundle(state); parcel.dataSize() } finally { parcel.recycle() }
        android.util.Log.i("SavedStatePayload", "ACTIVITY_CONTEXTS projects=2 tasksEach=80 bytes=$bytes")
        assertTrue(bytes > 0)
        assertArrayEquals(original, file.readBytes())
        phase("recreation-start")
        compose.activityRule.scenario.recreate()
        phase("restoration-assertions-start")
        assertContext("B task", "لم تبدأ", b)
        compose.pressProjectBack()
        open("Lifecycle project")
        assertContext("A task", "قيد التنفيذ", a)
        phase("restoration-assertions-passed")
    }

}
