package com.karim.aiprojectmanager

import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Injected system appearance and private fake preferences: no real settings or user files touched. */
@RunWith(AndroidJUnit4::class)
class ThemeUiTest {
    @get:Rule val compose = createComposeRule()
    private class Store : AppPreferences {
        var value = "SYSTEM"
        var reject = false
        override fun readString(key: String, defaultValue: String) = value
        override fun writeString(key: String, value: String): Boolean {
            if (reject) return false
            this.value = value
            return true
        }
    }
    private class Fixture(val file: File, val store: Store, val controller: ThemePreferences) {
        val systemDark = mutableStateOf(false)
        var observedBackground = Color.Unspecified
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "theme-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val file = File(root, "projects.json")
        ProjectStorage(file).save(listOf(
            ProjectItem("one", "Alpha", "Keep", "Original", "Original", listOf(TaskItem("task", "Keep task", "VERIFIED")))
        ) + (0..15).map { ProjectItem("p$it", "Project $it", "Other") })
        val store = Store()
        val fixture = Fixture(file, store, ThemePreferences({ store }))
        runBlocking { fixture.controller.load() }
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolated) {
                    val state by fixture.controller.state.collectAsState()
                    val scope = rememberCoroutineScope()
                    AppTheme(state.mode.isDark(fixture.systemDark.value)) {
                        val background = androidx.compose.material3.MaterialTheme.colorScheme.background
                        SideEffect { fixture.observedBackground = background }
                        ProjectManagerApp(ProjectStorage(file), appearanceSettings = {
                            AppearanceSettings(state,
                                onSelect = { mode -> scope.launch { fixture.controller.select(mode) } },
                                onRetry = { scope.launch { fixture.controller.retry() } })
                        })
                    }
                }
            }
            block(fixture)
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }

    private fun openSettings() {
        val settings = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_title)
        compose.onNode(hasClickAction() and hasContentDescription(settings)).performClick()
        compose.onNodeWithText("Follow System").performScrollTo().assertIsSelected()
    }
    private fun select(label: String, fixture: Fixture) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.controller.state.value.mode.label == label && !fixture.controller.state.value.saving }
        compose.onNodeWithText(label).assertIsSelected()
    }
    private fun assertBackground(fixture: Fixture, dark: Boolean) {
        compose.runOnIdle { assertEquals(Color(if (dark) 0xFF0B101D else 0xFFF6F8FB), fixture.observedBackground) }
    }

    @Test fun selectionsPersistAndExplicitModesIgnoreSystemChanges() = withFixture { f ->
        val original = f.file.readBytes()
        openSettings()
        select("Dark", f)
        assertBackground(f, true)
        compose.runOnIdle { f.systemDark.value = true }
        assertBackground(f, true)
        select("Light", f)
        assertBackground(f, false)
        compose.runOnIdle { f.systemDark.value = false }
        assertBackground(f, false)
        select("Follow System", f)
        compose.runOnIdle { f.systemDark.value = true }
        assertBackground(f, true)
        compose.runOnIdle {
            assertEquals("SYSTEM", f.store.value)
            assertArrayEquals(original, f.file.readBytes())
        }
        runBlocking {
            val reopened = ThemePreferences({ f.store }); reopened.load()
            assertEquals(ThemeMode.SYSTEM, reopened.state.value.mode)
        }
    }

    @Test fun failedSelectionKeepsConfirmedRadioAndAppearanceUntilRetry() = withFixture { f ->
        openSettings()
        compose.runOnIdle { f.store.reject = true }
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitUntil(5_000) { f.controller.state.value.error == ThemePreferenceError.SAVE }
        compose.onNodeWithText("Follow System").assertIsSelected()
        assertBackground(f, false)
        compose.onNodeWithText("تعذّر حفظ المظهر: Dark. بقي المظهر السابق؛ أعد المحاولة.").assertExists()
        compose.runOnIdle { f.store.reject = false }
        compose.onNodeWithText("إعادة محاولة المظهر").performScrollTo().performClick()
        compose.waitUntil(5_000) { f.controller.state.value.mode == ThemeMode.DARK }
        compose.onNodeWithText("Dark").assertIsSelected()
        assertBackground(f, true)
    }

    @Test fun systemAppearancePreservesProjectSearchListScrollAndBytes() = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithTag("dashboard_search").performScrollTo().performTextReplacement("Project")
        // This regression measures theme/scroll preservation after submitting search;
        // keyboard-open layout is covered separately by DashboardVNextUiTest.
        compose.onNodeWithTag("dashboard_search").performImeAction()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Project 9"))
        val scroll = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle { f.systemDark.value = true }
        assertBackground(f, true)
        compose.onNodeWithText("Project 9").assertIsDisplayed()
        assertEquals(scroll, list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .001f)
        list.performScrollToNode(hasTestTag("dashboard_search"))
        compose.onNodeWithTag("dashboard_search").assertTextContains("Project")
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }

    @Test fun settingsScrollAndBackPreserveSearchAfterAppearanceChange() = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithTag("dashboard_search").performTextReplacement("Alpha")
        openSettings()
        compose.onNodeWithText("معاينة نسخة").performScrollTo()
        val scrollNode = compose.onNode(hasScrollAction())
        val scroll = scrollNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.runOnIdle { f.systemDark.value = true }
        compose.onNodeWithText("معاينة نسخة").assertIsDisplayed()
        assertEquals(scroll, scrollNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .001f)
        compose.onNodeWithText("رجوع").performClick()
        compose.onNodeWithTag("dashboard_search").assertTextContains("Alpha")
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }

    @Test fun blueprintPendingDraftAndGuardSurviveThemeRecomposition() = pendingDocument("Blueprint")
    @Test fun roadmapPendingDraftAndGuardSurviveThemeRecomposition() = pendingDocument("Roadmap")
    private fun pendingDocument(tab: String) = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText(tab).performScrollTo().performClick()
        compose.onNodeWithText("محتوى المستند").assertTextContains("Original")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("محتوى المستند").performTextReplacement("Pending")
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { f.systemDark.value = true }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("محتوى المستند").assertTextContains("Pending")
        val settings = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.settings_title)
        compose.onNode(hasClickAction() and hasContentDescription(settings)).assertDoesNotExist()
        compose.onNodeWithText("رجوع").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }

    @Test fun metadataPendingInputAndReloadConfirmationSurviveThemeChange() = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("تعديل المشروع").performScrollTo().performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("Pending name")
        compose.onNodeWithText("إعادة تحميل القيم الحالية").performScrollTo().performClick()
        compose.runOnIdle { f.systemDark.value = true }
        compose.onNodeWithText("تأكيد إعادة التحميل").assertExists()
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("Pending name")
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }

    @Test fun taskTitlePendingInputAndDiscardConfirmationSurviveThemeChange() = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("المهام").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("تعديل العنوان"))
        compose.onNodeWithText("تعديل العنوان").performClick()
        compose.onNodeWithText("عنوان المهمة").performTextReplacement("Pending title")
        compose.onNodeWithText("إلغاء").performClick()
        compose.runOnIdle { f.systemDark.value = true }
        compose.onNodeWithText("تجاهل التعديلات").assertExists()
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.onNodeWithText("عنوان المهمة").assertTextContains("Pending title")
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }

    @Test fun taskCreationSearchAndStatusFilterSurviveThemeChange() = withFixture { f ->
        val before = f.file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("المهام").performScrollTo().performClick()
        compose.onNodeWithText("مهمة جديدة").performTextReplacement("Pending new task")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("بحث في المهام"))
        compose.onNodeWithText("بحث في المهام").performTextReplacement("Keep")
        compose.onNodeWithText("حالة المهام: كل الحالات").performScrollTo().performClick()
        compose.onNode(hasText("قيد التنفيذ") and hasAnyAncestor(isPopup())).performClick()
        compose.runOnIdle { f.systemDark.value = true }
        compose.onNodeWithText("مهمة جديدة").assertTextContains("Pending new task")
        compose.onNodeWithText("بحث في المهام").assertTextContains("Keep")
        compose.onNodeWithText("حالة المهام: قيد التنفيذ").assertExists()
        val noMatches = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.tasks_no_matches)
        compose.onNodeWithText(noMatches).assertExists()
        compose.runOnIdle { assertArrayEquals(before, f.file.readBytes()) }
    }
}
