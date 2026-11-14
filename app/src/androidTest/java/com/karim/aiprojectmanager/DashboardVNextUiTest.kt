package com.karim.aiprojectmanager

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic files and injected locale/font only. No system settings or user files. */
class DashboardVNextUiTest {
    @get:Rule val compose = createComposeRule()

    private fun dashboard(locale: String, dark: Boolean, scale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(locale)) }
        val localized = context.createConfigurationContext(configuration)
        val root = File(context.cacheDir, "vnext-${System.nanoTime()}").apply { mkdirs() }
        val file = File(root, "projects.json")
        val description = "Searchable description العربية 123. ".repeat(16)
        file.writeText("""[{"id":"first","name":"Planning العربية 123 — a long project title that stays readable","description":"$description","legacy":{"keep":true},"tasks":[{"id":"a","title":"A","status":"VERIFIED","extension":7},{"id":"b","title":"B","status":"IN_PROGRESS"},{"id":"c","title":"C","status":"FUTURE_STATUS"}]},${(2..14).joinToString(",") { """{"id":"p$it","name":"Project $it","description":"Keep $it"}""" }}]""")
        val before = file.readBytes()
        val isolated = object : ContextWrapper(localized) { override fun getFilesDir() = root }
        val visible = mutableStateOf(true)
        var exports = 0
        var previews = 0
        fun label(id: Int) = localized.getString(id)
        try {
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolated,
                    LocalConfiguration provides configuration,
                    LocalDensity provides Density(context.resources.displayMetrics.density, scale),
                    LocalLayoutDirection provides if (locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    AppTheme(dark) { ProjectManagerApp(ProjectStorage(file), onExport = { exports++ }, onRestorePreview = { previews++ }) }
                }
            }
            compose.onNodeWithText(label(R.string.dashboard_my_projects)).assertIsDisplayed()
            compose.onNodeWithText(localized.getString(R.string.dashboard_project_count, 14)).assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.settings_title)).assertIsDisplayed()
            File(context.cacheDir, "vnext01b-initial-$locale-$dark-$scale.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val create = compose.onNodeWithText(label(R.string.dashboard_create)).assertIsDisplayed().assertHasClickAction()
            val importAction = compose.onNodeWithText(label(R.string.dashboard_import_github)).assertIsDisplayed().assertHasClickAction()
            val actionBar = compose.onNodeWithTag("dashboard_create_bar")
            assertTrue(create.fetchSemanticsNode().boundsInRoot.height >= 48 * context.resources.displayMetrics.density)
            val list = compose.onNodeWithTag("dashboard_projects")
            assertTrue("Measured Create area is outside the entire scrolling viewport",
                list.fetchSemanticsNode().boundsInRoot.bottom <= actionBar.fetchSemanticsNode().boundsInRoot.top)
            assertTrue("Import and Create remain separate accessible actions",
                importAction.fetchSemanticsNode().boundsInRoot != create.fetchSemanticsNode().boundsInRoot)
            val logo = compose.onNodeWithTag("dashboard_logo", useUnmergedTree = true).captureToImage().toPixelMap()
            val background = appearanceColors(dark).background
            assertEquals("No opaque logo tile", background, logo[0, 0])
            assertEquals(
                "The logo's transparent center must not retain a dark circular halo",
                background,
                logo[logo.width / 2, (logo.height * 410f / 1080f).toInt()]
            )
            if (scale == 1f) {
                val searchBounds = compose.onNodeWithTag("dashboard_search").fetchSemanticsNode().boundsInRoot
                assertTrue("Compact search retains an accessible 48dp target", searchBounds.height >= 48 * context.resources.displayMetrics.density)
                assertTrue("Search stays compact at normal font size", searchBounds.height <= 52 * context.resources.displayMetrics.density)
            }
            list.performScrollToNode(hasText("Planning", substring = true))
            val manual = localized.getString(R.string.dashboard_manual_count, taskCountExpression(1, 3))
            list.performScrollToNode(hasText(manual))
            compose.onNodeWithText(manual).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(description, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(2, layouts.single().lineCount)
            compose.onNodeWithTag("dashboard_search").performScrollTo().performTextReplacement(" searchable ")
            compose.onNodeWithTag("dashboard_search").performImeAction()
            compose.onNodeWithText("Project 14").assertDoesNotExist()
            compose.onNodeWithTag("dashboard_search").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("dashboard_search").assertIsDisplayed()
            create.assertIsDisplayed()
            assertTrue("Keyboard cannot make the fixed action bar overlap the list",
                list.fetchSemanticsNode().boundsInRoot.bottom <= actionBar.fetchSemanticsNode().boundsInRoot.top)
            compose.onNodeWithTag("dashboard_search").performTextReplacement("not-found")
            compose.onNodeWithTag("dashboard_search").performImeAction()
            list.performScrollToIndex(2)
            compose.onNodeWithText(label(R.string.dashboard_no_matches_title), useUnmergedTree = true).assertIsDisplayed()
            list.performScrollToIndex(1)
            compose.onNodeWithTag("dashboard_search").performScrollTo().performTextReplacement(" ")
            compose.onNodeWithTag("dashboard_search").performImeAction()
            list.performScrollToNode(hasText("Project 14"))
            list.performScrollToIndex(15) // Include trailing FAB clearance, beyond merely revealing the row.
            val last = compose.onNodeWithTag("dashboard_project_p14").assertIsDisplayed()
            assertTrue("Last card can clear the fixed action bar",
                last.fetchSemanticsNode().boundsInRoot.bottom <= actionBar.fetchSemanticsNode().boundsInRoot.top)
            last.assert(hasText(localized.getString(R.string.dashboard_manual_count, taskCountExpression(0, 0))))
            last.performClick()
            compose.onNodeWithText("Project 14").assertExists()
            for (index in 0..3) compose.onNodeWithTag("project_tab_$index").assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.project_back)).performClick()
            compose.onNodeWithContentDescription(label(R.string.settings_title)).performClick()
            compose.onNodeWithText(label(R.string.settings_connections)).assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.project_back)).performClick()
            compose.onNodeWithContentDescription(label(R.string.dashboard_more)).performClick()
            compose.onNodeWithText(label(R.string.dashboard_export)).performClick()
            compose.onNodeWithContentDescription(label(R.string.dashboard_more)).performClick()
            compose.onNodeWithText(label(R.string.dashboard_backup_preview)).performClick()
            list.performScrollToIndex(1)
            compose.onNodeWithTag("dashboard_search").performScrollTo().performTextReplacement("")
            compose.onNodeWithTag("dashboard_search").performImeAction()
            list.performScrollToIndex(0)
            compose.waitForIdle()
            val screenshot = File(context.cacheDir, "vnext01b-$locale-$dark-$scale.png")
            screenshot.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            create.performClick()
            compose.onNodeWithText(label(R.string.dashboard_create_title)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.dashboard_create_confirm)).assertIsNotEnabled()
            compose.onNodeWithText(label(R.string.dashboard_project_name)).performTextReplacement("Unsaved العربية")
            compose.onNodeWithText(label(R.string.dashboard_create_confirm)).assertIsEnabled()
            compose.onNodeWithText(label(R.string.dashboard_create_cancel)).performClick()
            compose.runOnIdle { assertEquals(1, exports); assertEquals(1, previews); assertArrayEquals(before, file.readBytes()) }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            // Keep isolated fixture/evidence for review; never remove user files.
        }
    }

    @Test fun darkArabic() = dashboard("ar", true, 1f)
    @Test fun darkEnglish() = dashboard("en", true, 1f)
    @Test fun darkArabicLargeFont() = dashboard("ar", true, 2f)
    @Test fun lightEnglishLargeFont() = dashboard("en", false, 2f)
    @Test fun lightArabic() = dashboard("ar", false, 1f)

    @Test fun emptyAndCorruptDataRemainDistinctAndRetryPreservesBytes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "vnext-failure-${System.nanoTime()}").apply { mkdirs() }
        val file = File(root, "projects.json").apply { writeText("broken JSON") }
        val visible = mutableStateOf(true)
        compose.setContent { if (visible.value) AppTheme { ProjectManagerApp(ProjectStorage(file)) } }
        compose.onNodeWithText(context.getString(R.string.dashboard_read_error_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.dashboard_create)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.dashboard_retry)).performClick()
        compose.runOnIdle { assertEquals("broken JSON", file.readText()); file.writeText("[]") }
        compose.onNodeWithText(context.getString(R.string.dashboard_retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.dashboard_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.dashboard_create)).assertIsDisplayed()
        compose.runOnIdle { assertEquals("[]", file.readText()); visible.value = false }
    }
}
