package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Injected, validated preview and synthetic storage; no picker, import or real project files. */
@RunWith(AndroidJUnit4::class)
class BackupPreviewAccessibilityUiTest {
    @get:Rule val compose = createComposeRule()

    private fun projectName(index: Int) = "Backup ${index.toString().padStart(2, '0')} — long project name العربية 12345"

    private fun longPreview(direction: LayoutDirection) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "preview-accessibility-${System.nanoTime()}")
        assertTrue(root.mkdir())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val live = File(root, "projects.json")
        live.writeText("""[{"id":"live","name":"Keep","legacy":{"retain":true},"tasks":[]}]""")
        val before = live.readBytes()
        val backup = JSONArray().apply {
            (1..31).forEach { index ->
                put(JSONObject().put("id", "backup-$index").put("name", projectName(index)))
            }
        }.toString().toByteArray()
        val preview = mutableStateOf<RestorePreview.Summary?>(RestorePreview.inspect(backup))
        val visible = mutableStateOf(true)
        var dismissals = 0
        var exports = 0
        // Constrain effective Compose viewport to <=320x640dp without changing device settings.
        // Dialogs have their own window; a narrow Box around the app would not constrain them.
        val metrics = context.resources.displayMetrics
        val narrowDensity = maxOf(metrics.widthPixels / 320f, metrics.heightPixels / 640f)
        try {
            compose.setContent {
                if (visible.value) CompositionLocalProvider(
                    LocalContext provides isolated,
                    LocalDensity provides Density(narrowDensity, 2f),
                    LocalLayoutDirection provides direction
                ) {
                    AppTheme(dark = false) {
                        ProjectManagerApp(ProjectStorage(live), restorePreview = preview.value,
                            onDismissPreview = { dismissals++; preview.value = null },
                            onExport = { exports++ })
                    }
                }
            }
            val close = compose.onNodeWithText("إغلاق").assertIsDisplayed().assertHasClickAction()
            val closeBounds = close.fetchSemanticsNode().boundsInRoot
            compose.onNodeWithText("عدد المشاريع: 31 | عدد المهام: 0").performScrollTo().assertIsDisplayed()
            (1..30).forEach { index ->
                compose.onNodeWithText("${projectName(index)} — 0 مهمة").performScrollTo().assertIsDisplayed()
                close.assertIsDisplayed()
                assertEquals(closeBounds, close.fetchSemanticsNode().boundsInRoot)
            }
            compose.onNodeWithText("${projectName(31)} — 0 مهمة").assertDoesNotExist()
            compose.onNodeWithText("تُعرض أول 30 مشروعًا فقط").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("معاينة فقط: لن يتم استبدال أو دمج أي بيانات.").performScrollTo().assertIsDisplayed()
            close.assertIsDisplayed().performClick()
            compose.onNodeWithText("معاينة نسخة احتياطية").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, dismissals)
                assertEquals(0, exports)
                assertArrayEquals(before, live.readBytes())
                assertEquals(listOf("projects.json"), root.list()!!.toList())
            }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }

    @Test fun narrowLargeFontPreviewKeepsEveryDisplayedEntryAndCloseReachableLtr() = longPreview(LayoutDirection.Ltr)
    @Test fun narrowLargeFontPreviewKeepsEveryDisplayedEntryAndCloseReachableRtl() = longPreview(LayoutDirection.Rtl)
}
