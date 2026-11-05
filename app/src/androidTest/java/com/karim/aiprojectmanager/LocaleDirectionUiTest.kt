package com.karim.aiprojectmanager

import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Simulated Android locale configurations only; no system locale or production data changes. */
@RunWith(AndroidJUnit4::class)
class LocaleDirectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun InLocale(tag: String, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        CompositionLocalProvider(
            LocalConfiguration provides configuration,
            LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                LayoutDirection.Rtl else LayoutDirection.Ltr
        ) { AppTheme(dark = false, content = content) }
    }

    private fun textLayout(tag: String): TextLayoutResult = textLayout(compose.onNodeWithTag(tag))

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(results))
        }
        return results.single()
    }

    @Test fun arabicParagraphStartsAtRightEvenInEnglishLayout() {
        compose.setContent {
            InLocale("en") { ParagraphText("عنوان عربي", Modifier.width(280.dp).testTag("paragraph")) }
        }
        val layout = textLayout("paragraph")
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertTrue(layout.getBoundingBox(0).right > layout.size.width * 0.9f)
        assertTrue(layout.getLineLeft(0) > layout.size.width * 0.25f)
    }

    @Test fun englishParagraphStartsAtLeftEvenInArabicLayout() {
        compose.setContent {
            InLocale("ar") { ParagraphText("Project title", Modifier.width(280.dp).testTag("paragraph")) }
        }
        val layout = textLayout("paragraph")
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        assertEquals(0f, layout.getLineLeft(0), 1f)
        assertTrue(layout.getLineRight(0) < layout.size.width * 0.75f)
    }

    @Test fun mixedParagraphsPreserveJsonIdentifiersAndNumbers() {
        val text = "مخطط Blueprint 123\n{\"id\":\"task-42\"}\nRoadmap 2026\n12345"
        compose.setContent {
            InLocale("ar") { ParagraphText(text, Modifier.width(280.dp).testTag("paragraph")) }
        }
        compose.onNodeWithTag("paragraph").assertTextEquals(text)
        val layout = textLayout("paragraph")
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, layout.getBidiRunDirection(text.indexOf("Blueprint")))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(text.indexOf('{')))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(text.indexOf("Roadmap")))
        assertEquals(ResolvedTextDirection.Ltr, layout.getBidiRunDirection(text.indexOf("12345")))
    }

    @Test fun appearanceControlOrderFollowsAndroidLocale() {
        val locale = mutableStateOf("en")
        compose.setContent {
            InLocale(locale.value) {
                Column(Modifier.width(300.dp)) {
                    AppearanceSettings(ThemeSettingsState(loading = false), onSelect = {}, onRetry = {})
                }
            }
        }
        fun labelCenter() = compose.onNodeWithText("Follow System", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        fun rowCenter() = compose.onNodeWithText("Follow System").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(labelCenter() < rowCenter())
        compose.runOnIdle { locale.value = "ar" }
        assertTrue(labelCenter() > rowCenter())
        compose.onNodeWithText("Follow System").assertIsSelected()
    }

    @Test fun isolatedCountHasLeftToRightGlyphOrderInBothLocales() {
        val locale = mutableStateOf("en")
        val arabic = "المهام المتحققة: " + taskCountExpression(0, 1)
        val english = "Verified tasks: " + taskCountExpression(0, 1)
        compose.setContent {
            InLocale(locale.value) {
                Column(Modifier.width(280.dp)) {
                    ParagraphText(arabic, Modifier.testTag("arabic-count"))
                    ParagraphText(english, Modifier.testTag("english-count"))
                }
            }
        }
        for (tag in listOf("en", "ar")) {
            compose.runOnIdle { locale.value = tag }
            for ((nodeTag, text) in listOf("arabic-count" to arabic, "english-count" to english)) {
                val layout = textLayout(nodeTag)
                val zero = layout.getBoundingBox(text.indexOf('0'))
                val slash = layout.getBoundingBox(text.indexOf('/'))
                val one = layout.getBoundingBox(text.indexOf('1'))
                assertTrue(zero.left < slash.left)
                assertTrue(slash.left < one.left)
            }
        }
    }

    @Test fun englishBlueprintInputStartsAtLeftUnderArabicLocale() = withProject { file, changeLocale ->
        val before = file.readBytes()
        changeLocale("ar")
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("Blueprint").performScrollTo().performClick()
        val field = compose.onNodeWithText("محتوى المستند").assertTextContains("Original")
        val layout = textLayout(field)
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        assertEquals(0f, layout.getLineLeft(0), 1f)
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun arabicRoadmapInputStartsAtRightUnderEnglishLocale() = withProject { file, _ ->
        val before = file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("Roadmap").performScrollTo().performClick()
        val field = compose.onNodeWithText("محتوى المستند").assertTextContains("Original")
        compose.mainClock.autoAdvance = false
        field.performTextReplacement("خطة عربية")
        compose.mainClock.advanceTimeBy(64)
        val layout = textLayout(field)
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(layout.size.width.toFloat(), layout.getLineRight(0), 1f)
        field.assertTextContains("خطة عربية")
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun mixedDocumentEditingPreservesSelectionTextAndParagraphDirections() = withProject { file, changeLocale ->
        val before = file.readBytes()
        changeLocale("ar")
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("Blueprint").performScrollTo().performClick()
        val field = compose.onNodeWithText("محتوى المستند").assertTextContains("Original")
        val text = "خطة عربية\nBlueprint task-42\n{\"count\":12345}"
        compose.mainClock.autoAdvance = false
        field.performTextReplacement(text)
        compose.mainClock.advanceTimeBy(64)
        val start = text.indexOf("42")
        field.performTextInputSelection(TextRange(start, start + 2))
        field.performTextInput("99")
        compose.mainClock.advanceTimeBy(64)
        field.assertTextContains(text.replace("42", "99"))
        field.assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange, TextRange(start + 2)))
        val layout = textLayout(field)
        assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(text.indexOf("Blueprint")))
        assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(text.indexOf('{')))
        assertEquals(0f, layout.getLineLeft(1), 1f)
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    private fun withProject(block: (File, (String) -> Unit) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "locale-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val file = File(root, "projects.json")
        file.writeText("""[{"id":"one","name":"Alpha","description":"Keep","blueprint":"Original","roadmap":"Original","legacy":7,"tasks":[]}]""")
        val locale = mutableStateOf("en")
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                if (visible.value) CompositionLocalProvider(LocalContext provides isolated) {
                    InLocale(locale.value) { ProjectManagerApp(ProjectStorage(file)) }
                }
            }
            block(file) { tag -> compose.runOnIdle { locale.value = tag } }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            root.deleteRecursively()
        }
    }

    @Test fun metadataDirectionChangeRetainsInputAndDiscardConfirmation() = withProject { file, changeLocale ->
        val before = file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("تعديل المشروع").performClick()
        compose.onNodeWithText("اسم المشروع").performTextReplacement("مشروع Pending 123")
        changeLocale("ar")
        compose.onNodeWithText("اسم المشروع").assertTextContains("مشروع Pending 123")
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.onNodeWithText("متابعة التحرير").performClick()
        compose.onNodeWithText("اسم المشروع").assertTextContains("مشروع Pending 123")
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }

    @Test fun documentDirectionChangeRetainsDraftAndDirtyNavigationGuard() = withProject { file, changeLocale ->
        val before = file.readBytes()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithText("Blueprint").performScrollTo().performClick()
        compose.onNodeWithText("محتوى المستند").assertTextContains("Original")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("محتوى المستند").performTextReplacement("مسودة Blueprint 123")
        compose.mainClock.advanceTimeBy(64)
        changeLocale("ar")
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("محتوى المستند").assertTextContains("مسودة Blueprint 123")
        compose.onNodeWithText("رجوع").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("تعديلات غير محفوظة").assertExists()
        compose.onNodeWithText("العودة إلى المحرر").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("محتوى المستند").assertTextContains("مسودة Blueprint 123")
        compose.runOnIdle { assertArrayEquals(before, file.readBytes()) }
    }
}
