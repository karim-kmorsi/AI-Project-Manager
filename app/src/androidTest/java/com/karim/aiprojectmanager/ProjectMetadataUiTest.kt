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
        compose.onNodeWithText("تجاهل التعديلات").performClick()
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
        compose.onNodeWithText("تجاهل التعديلات").performClick()
        compose.runOnIdle { assertArrayEquals(newerBytes, file.readBytes()) }
    }
    @Test fun transientFailureKeepsMetadataAndRetrySucceeds() = fixture { file, storage ->
        val before = file.readBytes()
        val original = storage.load()
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Retry name")
        compose.onNodeWithText("وصف مختصر").performTextReplacement("Retry description")
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onNodeWithText("حفظ").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Retry name")
        compose.onNodeWithText("وصف مختصر").assertTextContains("Retry description")
        compose.runOnIdle { assertEquals("broken JSON", file.readText()); file.writeBytes(before) }
        compose.onNodeWithText("حفظ").performClick()
        compose.runOnIdle {
            assertEquals(listOf(original[0].copy(name = "Retry name", description = "Retry description"), original[1]), storage.load())
            assertEquals(42, JSONArray(file.readText()).getJSONObject(0).getJSONArray("tasks").getJSONObject(0).getInt("extra"))
        }
    }

    @Test fun conflictReloadRequiresConfirmationAndAllowsEditAgainstNewBase() = fixture { file, storage ->
        val original = storage.load()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Pending name")
        compose.onNodeWithText("وصف مختصر").performTextReplacement("Pending description")
        lateinit var newer: ByteArray
        compose.runOnIdle {
            storage.save(listOf(original[0].copy(name = "Newer name", blueprint = "Newer blueprint"), original[1]))
            newer = file.readBytes()
        }
        // Restored pending input must not adopt a newer storage snapshot as its original base.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("حفظ").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Pending name")
        compose.runOnIdle { assertArrayEquals(newer, file.readBytes()) }
        compose.onNodeWithText("إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Pending name")
        compose.onNodeWithText("وصف مختصر").assertTextContains("Pending description")
        compose.onNodeWithText("إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onNodeWithText("تأكيد إعادة التحميل").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Newer name")
        compose.runOnIdle { assertArrayEquals(newer, file.readBytes()) }
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Reviewed name")
        compose.onNodeWithText("حفظ").performClick()
        compose.runOnIdle {
            assertEquals("Reviewed name", storage.load()[0].name)
            assertEquals("Newer blueprint", storage.load()[0].blueprint)
            assertEquals(original[0].tasks, storage.load()[0].tasks)
        }
    }

    @Test fun failedReloadAndCancelledDismissalRetainPendingMetadata() = fixture { file, storage ->
        compose.setContent { ProjectManagerApp(storage) }
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Pending")
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.runOnIdle { file.writeText("broken JSON") }
        compose.onNodeWithText("إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.onNodeWithText("تأكيد إعادة التحميل").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Pending")
        compose.onNodeWithText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.").assertExists()
        compose.runOnIdle { assertEquals("broken JSON", file.readText()) }
    }

}
