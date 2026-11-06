package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Geometry under injected constraints/insets, not real IME or spoken TalkBack acceptance. */
@RunWith(AndroidJUnit4::class)
class AccessibilityLayoutUiTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Narrow(direction: LayoutDirection, font: Float, content: @Composable () -> Unit) {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        CompositionLocalProvider(
            LocalDensity provides Density(maxOf(metrics.widthPixels / 360f, metrics.heightPixels / 640f), font),
            LocalLayoutDirection provides direction
        ) { AppTheme(dark = false, content = content) }
    }

    @Test fun creationReservesInsetsAndRetainsFailedInputLtr() = creation(LayoutDirection.Ltr)
    @Test fun creationReservesInsetsAndRetainsFailedInputRtl() = creation(LayoutDirection.Rtl)

    private fun creation(direction: LayoutDirection) {
        var name by mutableStateOf("")
        var details by mutableStateOf("")
        var error by mutableStateOf(false)
        var dismissed = false
        var bottom by mutableIntStateOf(0)
        var insetPixels = 0
        compose.setContent {
            Narrow(direction, 2f) {
                insetPixels = with(LocalDensity.current) { 299.dp.roundToPx() }
                ProjectCreationDialog(name, details, error, { name = it }, { details = it },
                    onDismiss = { dismissed = true }, onCreate = { error = true },
                    insets = WindowInsets(bottom = bottom))
            }
        }
        compose.onNodeWithText("إنشاء").assertIsNotEnabled()
        compose.onNodeWithText("اسم المشروع").performScrollTo().performTextReplacement("Synthetic العربية 42")
        compose.onNodeWithText("وصف مختصر").performScrollTo().performTextReplacement("Pending description وصف")
        compose.runOnIdle { bottom = insetPixels }
        compose.onNodeWithText("وصف مختصر").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("إنشاء").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("تعذّر حفظ المشروع. لم تُعتمد التغييرات؛ حاول مجددًا.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("اسم المشروع").performScrollTo().assertTextContains("Synthetic العربية 42")
        compose.onNodeWithText("وصف مختصر").performScrollTo().assertTextContains("Pending description وصف")
        val cancel = compose.onNodeWithText("إلغاء").assertIsDisplayed().assertHasClickAction()
        val node = cancel.fetchSemanticsNode()
        val root = generateSequence(node) { it.parent }.last()
        assertTrue("Button must remain above the reserved bottom area",
            node.boundsInRoot.bottom <= root.boundsInRoot.bottom - insetPixels + 1f)
        cancel.performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals("Synthetic العربية 42", name) }
    }

    @Test fun compactEditorDrawsTextAndRetainsSelectionLtr() = editor(LayoutDirection.Ltr)
    @Test fun compactEditorDrawsTextAndRetainsSelectionRtl() = editor(LayoutDirection.Rtl)

    @OptIn(ExperimentalTestApi::class)
    private fun editor(direction: LayoutDirection) {
        var height by mutableIntStateOf(300)
        var value by mutableStateOf("English 42\nخطة عربية\n{\"n\":1}")
        compose.setContent {
            Narrow(direction, 1f) {
                ScrollableDocumentBody(Modifier.width(360.dp).height(height.dp), chrome = {
                    TextButton(onClick = {}) { Text("حفظ") }
                    ParagraphText("إرشاد طويل للعربية والإنجليزية\nEditor guidance and recovery status\nMore guidance")
                    ParagraphText("تم الحفظ")
                }, editor = {
                    OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth().testTag("field"),
                        textStyle = contentTextStyle(), label = { Text("محتوى المستند") })
                })
            }
        }
        val field = compose.onNodeWithTag("field")
        field.performScrollTo().performTextInputSelection(TextRange(8, 10))
        compose.mainClock.advanceTimeByFrame()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(8, 10)))
        compose.runOnIdle { height = 180 }
        field.performScrollTo().assertIsDisplayed()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(8, 10)))
        val results = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        val text = results.single()
        for (index in listOf(0, 11, value.lastIndex)) {
            val glyph = text.getBoundingBox(index)
            assertTrue("Glyph must have drawable height", glyph.height > 0f)
            assertTrue("Glyph must fit the text viewport", glyph.top >= 0f && glyph.bottom <= text.size.height + 1f)
        }
        compose.runOnIdle { assertEquals("English 42\nخطة عربية\n{\"n\":1}", value) }
        compose.onNodeWithText("حفظ").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun toolbarWrapsWithoutClippingTitleOrLosingActionsLtr() = toolbar(LayoutDirection.Ltr)
    @Test fun toolbarWrapsWithoutClippingTitleOrLosingActionsRtl() = toolbar(LayoutDirection.Rtl)

    private fun toolbar(direction: LayoutDirection) {
        var exports = 0
        var previews = 0
        compose.setContent {
            Narrow(direction, 2f) {
                Box(Modifier.width(320.dp)) {
                    AccessibleAppBar("إدارة المشاريع", navigation = {}, actions = {
                        TextButton(onClick = { exports++ }) { Text("تصدير JSON") }
                        TextButton(onClick = { previews++ }) { Text("معاينة نسخة") }
                    })
                }
            }
        }
        val title = compose.onNodeWithText("إدارة المشاريع").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertFalse("Title must not overflow its measured text area", layouts.single().hasVisualOverflow)
        val headingBounds = title.fetchSemanticsNode().boundsInRoot
        for (label in listOf("تصدير JSON", "معاينة نسخة")) {
            val action = compose.onNodeWithText(label).assertIsDisplayed().assertHasClickAction()
            assertTrue("Actions must not overlap title", action.fetchSemanticsNode().boundsInRoot.top >= headingBounds.bottom)
            action.performClick()
        }
        compose.runOnIdle { assertEquals(1, exports); assertEquals(1, previews) }
    }
}
