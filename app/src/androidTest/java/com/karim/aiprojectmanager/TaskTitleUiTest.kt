package com.karim.aiprojectmanager

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic isolated files only; never launches production MainActivity or a system picker. */
@RunWith(AndroidJUnit4::class)
class TaskTitleUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(block: (File, ProjectStorage) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "task-title-ui-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val file = File(root, "projects.json")
            file.writeText("""[
                {"id":"p","name":"Task project","description":"Description","blueprint":"Blueprint",
                 "roadmap":"Roadmap","extraProject":true,"tasks":[
                   {"id":"t","title":"Original task","status":"FUTURE_STATUS","extraTask":42},
                   {"id":"second","title":"Other task","status":"VERIFIED"}]},
                {"id":"other","name":"Other project","tasks":[{"id":"t","title":"Unrelated","status":"VERIFIED"}]}
            ]""")
            block(file, ProjectStorage(file))
        } finally { root.deleteRecursively() }
    }

    private fun openFirstTaskEditor() {
        compose.onNodeWithText("Task project").performClick()
        compose.selectProjectTab(3)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit title") or hasText("تعديل العنوان"))
        compose.onNode(hasText("Edit title") or hasText("تعديل العنوان")).performClick()
    }

    @Test fun savingChangesOnlyTitleAndPreservesStoredExtensionsAndAssociation() = fixture { file, storage ->
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("  عنوان English  ")
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText("Edit task title", "تعديل عنوان المهمة").assertDoesNotExist()
        compose.onNodeWithText("عنوان English").assertExists()
        compose.runOnIdle {
            val expected = original[0].copy(tasks = listOf(original[0].tasks[0].copy(title = "عنوان English"), original[0].tasks[1]))
            assertEquals(listOf(expected, original[1]), ProjectStorage(file).load())
            val json = JSONArray(file.readText()).getJSONObject(0)
            assertTrue(json.getBoolean("extraProject"))
            assertEquals(42, json.getJSONArray("tasks").getJSONObject(0).getInt("extraTask"))
        }
    }

    @Test fun blankTitleBlocksSaveAndCancelDiscardsRestoredInput() = fixture { file, storage ->
        val before = file.readBytes()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("   ")
        compose.onLocalizedText("Save", "حفظ").assertIsNotEnabled()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Not saved")
        restoration.emulateSavedInstanceStateRestore()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Not saved")
        compose.onLocalizedText("Cancel", "إلغاء").performClick()
        compose.onDiscardChanges().performClick()
        compose.onNodeWithText("Original task").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Edit title") or hasText("تعديل العنوان"))
        compose.onNode(hasText("Edit title") or hasText("تعديل العنوان")).performClick()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Original task")
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun failedSaveRetainsInputAndAllowsRetryWithoutChangingStatus() = fixture { file, storage ->
        val before = file.readBytes()
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Retry title")
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText(
            "Could not save the task. Changes were not applied; please retry.",
            "تعذّر حفظ المهمة. لم تُعتمد التغييرات؛ حاول مجددًا."
        ).assertExists()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Retry title")
        compose.onLocalizedText("Save", "حفظ").assertIsEnabled()
        compose.runOnIdle {
            assertEquals("broken JSON", file.readText())
            // Repair only this synthetic fixture to model a transient storage failure.
            file.writeBytes(before)
        }
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText("Edit task title", "تعديل عنوان المهمة").assertDoesNotExist()
        compose.onNodeWithText("Retry title").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(original[0].copy(tasks = listOf(original[0].tasks[0].copy(title = "Retry title"), original[0].tasks[1])), original[1]), storage.load())
        }
    }

    @Test fun staleEditCannotOverwriteNewerSavedTaskStatus() = fixture { file, storage ->
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Pending title")
        var newerBytes = byteArrayOf()
        compose.runOnIdle {
            storage.save(listOf(original[0].copy(tasks = listOf(original[0].tasks[0].copy(status = "VERIFIED"), original[0].tasks[1])), original[1]))
            newerBytes = file.readBytes()
        }
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText(
            "Could not save the task. Changes were not applied; please retry.",
            "تعذّر حفظ المهمة. لم تُعتمد التغييرات؛ حاول مجددًا."
        ).assertExists()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Pending title")
        compose.onLocalizedText("Cancel", "إلغاء").performClick()
        compose.onDiscardChanges().performClick()
        compose.runOnIdle { assertArrayEquals(newerBytes, file.readBytes()) }
    }
    @Test fun conflictReloadRetainsInputUntilConfirmedAndPreservesNewStatus() = fixture { file, storage ->
        val original = storage.load()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Pending title")
        lateinit var newer: ByteArray
        compose.runOnIdle {
            storage.save(listOf(original[0].copy(tasks = listOf(original[0].tasks[0].copy(title = "Newer title", status = "VERIFIED"), original[0].tasks[1])), original[1]))
            newer = file.readBytes()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Pending title")
        compose.runOnIdle { assertArrayEquals(newer, file.readBytes()) }
        compose.onLocalizedText("Reload current values", "إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onLocalizedText("Continue editing", "متابعة التحرير").performClick()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Pending title")
        compose.onLocalizedText("Reload current values", "إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onLocalizedText("Confirm reload", "تأكيد إعادة التحميل").performClick()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Newer title")
        compose.runOnIdle { assertArrayEquals(newer, file.readBytes()) }
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Reviewed title")
        compose.onLocalizedText("Save", "حفظ").performClick()
        compose.runOnIdle {
            assertEquals("Reviewed title", storage.load()[0].tasks[0].title)
            assertEquals("VERIFIED", storage.load()[0].tasks[0].status)
            assertEquals(original[1], storage.load()[1])
            assertEquals(42, JSONArray(file.readText()).getJSONObject(0).getJSONArray("tasks").getJSONObject(0).getInt("extraTask"))
        }
    }

    @Test fun failedReloadAndCancelledDismissalRetainTaskTitle() = fixture { file, storage ->
        compose.setContent { ProjectManagerApp(storage) }
        openFirstTaskEditor()
        compose.onLocalizedText("Task title", "عنوان المهمة").performTextReplacement("Pending title")
        compose.onLocalizedText("Cancel", "إلغاء").performClick()
        compose.onLocalizedText("Continue editing", "متابعة التحرير").performClick()
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onLocalizedText("Reload current values", "إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onLocalizedText("Confirm reload", "تأكيد إعادة التحميل").performClick()
        compose.onLocalizedText("Task title", "عنوان المهمة").assertTextContains("Pending title")
        compose.runOnIdle { assertEquals("broken JSON", file.readText()) }
    }

}
