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

/** Isolated storage only; no production Activity, picker or installed project data. */
@RunWith(AndroidJUnit4::class)
class ProjectMetadataUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(block: (File, ProjectStorage) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "metadata-ui-${System.nanoTime()}")
        assertTrue(root.mkdir())
        try {
            val file = File(root, "projects.json")
            file.writeText("""[
                {"id":"p","name":"Original","description":"Description","blueprint":"Blueprint",
                 "roadmap":"Roadmap","extension":{"keep":true},"tasks":[
                   {"id":"t2","title":"Unknown","status":"FUTURE_STATUS","extra":42},
                   {"id":"t1","title":"Verified","status":"VERIFIED"}]},
                {"id":"other","name":"Untouched","description":"Keep"}
            ]""")
            block(file, ProjectStorage(file))
        } finally { root.deleteRecursively() }
    }

    @Test fun savingMetadataPreservesExistingContentExtensionsAndProjectOrder() = fixture { file, storage ->
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("  عنوان English  ")
        compose.onNodeWithText("وصف مختصر").performTextReplacement("  وصف\nDescription  ")
        compose.onNodeWithText("حفظ").performClick()
        compose.onNodeWithText("عنوان English").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(original[0].copy(name = "عنوان English", description = "  وصف\nDescription  "), original[1]), storage.load())
            val json = JSONArray(file.readText()).getJSONObject(0)
            assertTrue(json.getJSONObject("extension").getBoolean("keep"))
            assertEquals(42, json.getJSONArray("tasks").getJSONObject(0).getInt("extra"))
        }
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithText("عنوان English").assertExists()
        compose.onNodeWithText("  وصف\nDescription  ").assertExists()
    }

    @Test fun blankTitleBlocksSaveAndCancelDiscardsRestoredFormWithoutWriting() = fixture { file, storage ->
        val before = file.readBytes()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("   ")
        compose.onNodeWithText("حفظ").assertIsNotEnabled()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Unsubmitted")
        compose.onNodeWithText("وصف مختصر").performTextReplacement("Unsaved description")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Unsubmitted")
        compose.onNodeWithText("وصف مختصر").assertTextContains("Unsaved description")
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("Original").assertExists()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Original")
        compose.onNodeWithText("وصف مختصر").assertTextContains("Description")
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun staleSaveRetainsInputAndDoesNotOverwriteNewerData() = fixture { file, storage ->
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Pending edit")
        compose.onNodeWithText("وصف مختصر").performTextReplacement("Pending description")
        var newerBytes = byteArrayOf()
        compose.runOnIdle {
            storage.save(listOf(original[0].copy(blueprint = "Newer saved document"), original[1]))
            newerBytes = file.readBytes()
        }
        compose.onNodeWithText("حفظ").performClick()
        compose.onNodeWithText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.").assertExists()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Pending edit")
        compose.onNodeWithText("وصف مختصر").assertTextContains("Pending description")
        compose.onNodeWithText("حفظ").assertIsEnabled()
        compose.onNodeWithText("إلغاء").performClick()
        compose.runOnIdle { assertArrayEquals(newerBytes, file.readBytes()) }
    }
}
