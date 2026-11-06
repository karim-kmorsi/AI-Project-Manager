package com.karim.aiprojectmanager

import android.os.Bundle
import android.os.Parcel
import android.util.Log
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android Parcel/Bundle measurement when executed; not executed by the host test suite.
 * Slice measurements exclude Compose/envelope overhead. Full Activity measurements include it.
 * Neither establishes Binder transaction safety or actual process-death recovery.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SavedStatePayloadUiTest {
    @get:Rule val compose = createAndroidComposeRule<IsolatedWorkflowActivity>()
    @After fun cleanFixture() {
        compose.mainClock.autoAdvance = true
        val root = compose.activity.fixtureRoot
        compose.activityRule.scenario.close()
        root.deleteRecursively()
    }
    private fun size(bundle: Bundle): Int = Parcel.obtain().let {
        try { it.writeBundle(bundle); it.dataSize() } finally { it.recycle() }
    }
    private fun project(units: Int, count: Int): ProjectItem = ProjectItem("active", "Lifecycle project", "Keep description",
        "B".repeat(units), "ر".repeat(units), (0 until count).map { TaskItem("task-$it", "Task $it", "FUTURE") })
    private fun legacy(p: ProjectItem) = arrayListOf(p.id, p.name, p.description, p.blueprint, p.roadmap, p.tasks.size.toString()).apply {
        p.tasks.forEach { add(it.id); add(it.title); add(it.status) }
    }
    @Test fun parcelSlicesCharacterizeBaselineAndPendingInputSeparately() {
        var compactSize: Int? = null
        for ((units, count, pending) in listOf(Triple(64, 3, 32), Triple(262_144, 2_000, 32), Triple(262_144, 2_000, 131_072))) {
            val p = project(units, count)
            val saved = with(EditorSnapshot.saver) { SaverScope { true }.save(editorSnapshot(p)) }!!
            fun slice(base: ArrayList<String>, draft: String = "") = Bundle().apply {
                putStringArrayList("baseline", base); putString("pending", draft)
                putString("projectId", p.id); putString("taskId", "task-0"); putString("confirmation", "reload")
            }
            val compact = size(slice(saved))
            if (compactSize == null) compactSize = compact else assertEquals(compactSize, compact)
            val legacyBytes = size(slice(legacy(p)))
            val pendingBytes = size(slice(saved, "x".repeat(pending)))
            Log.i("SavedStatePayload", "PARCEL_SLICE unitsEach=$units tasks=$count pending=$pending legacy=$legacyBytes compact=$compact withPending=$pendingBytes")
            if (units > 64) assertTrue(legacyBytes > compact * 100)
            val restored = EditorSnapshot.saver.restore(saved)!!
            assertEquals(editorSnapshot(p), restored)
            assertNotEquals(editorSnapshot(p.copy(roadmap = "newer")), restored)
        }
    }
    @Test fun smallMetadataActivityBundle() = activity("metadata", 64, 3, 32)
    @Test fun largeMetadataActivityBundleWithPendingDescription() = activity("metadata", 262_144, 200, 131_072)
    @Test fun largeTaskTitleActivityBundle() = activity("title", 262_144, 200, 32)
    @Test fun largePendingDocumentActivityBundle() = activity("Blueprint", 262_144, 200, 131_072)

    private fun activity(editor: String, units: Int, count: Int, pendingUnits: Int) {
        fun phase(name: String) = Log.i("SavedStatePhase", "editor=$editor phase=$name")
        phase("fixture-start")
        lateinit var file: File
        compose.activityRule.scenario.onActivity {
            file = File(it.filesDir, "projects.json")
            ProjectStorage(file).save(listOf(project(units, count)))
        }
        compose.activityRule.scenario.recreate()
        phase("fixture-recreated")
        val original = file.readBytes()
        compose.onNodeWithText("Lifecycle project").performClick()
        val pending = "x".repeat(pendingUnits)
        when (editor) {
            "metadata" -> {
                compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
                compose.onNodeWithText("وصف مختصر").performTextReplacement(pending)
            }
            "title" -> {
                compose.onNodeWithText("المهام").performScrollTo().performClick()
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Task 0"))
                compose.onAllNodesWithText("تعديل العنوان").onFirst().performClick()
                compose.onNodeWithText("عنوان المهمة").performTextReplacement(pending)
            }
            else -> {
                compose.onNodeWithText(editor).performScrollTo().performClick()
                phase("editor-readiness-start")
                // Finish loading the editor before freezing autosave time.
                compose.onNodeWithText("محتوى المستند").assertTextContains("B".repeat(units))
                phase("editor-ready")
                compose.mainClock.autoAdvance = false
                phase("pending-input-start")
                compose.onNodeWithText("محتوى المستند").performTextReplacement(pending)
                phase("pending-input-entered")
                compose.mainClock.advanceTimeByFrame()
            }
        }
        phase("state-capture-start")
        val state = Bundle()
        compose.activityRule.scenario.onActivity {
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(it, state)
        }
        phase("state-captured")
        val bytes = size(state)
        Log.i("SavedStatePayload", "ACTIVITY_BUNDLE editor=$editor unitsEach=$units tasks=$count pending=$pendingUnits bytes=$bytes")
        assertTrue(bytes > 0)
        assertArrayEquals(original, file.readBytes())
        phase("pending-recreation-start")
        compose.activityRule.scenario.recreate()
        phase("pending-recreated")
        if (editor == "Blueprint") compose.mainClock.advanceTimeByFrame()
        phase("restoration-assertions-start")
        compose.onNodeWithText(when (editor) { "metadata" -> "وصف مختصر"; "title" -> "عنوان المهمة"; else -> "محتوى المستند" }).assertTextContains(pending)
        assertArrayEquals(original, file.readBytes())
        phase("restoration-assertions-passed")
    }
}
