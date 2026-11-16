package com.karim.aiprojectmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsNotEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performScrollTo().performTextReplacement("Synthetic العربية 42")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_description)).performScrollTo().performTextReplacement("Pending description وصف")
        compose.runOnIdle { bottom = insetPixels }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_description)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_confirm)).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_creation_error))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_name)).performScrollTo().assertTextContains("Synthetic العربية 42")
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_project_description)).performScrollTo().assertTextContains("Pending description وصف")
        val cancel = compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dashboard_create_cancel)).assertIsDisplayed().assertHasClickAction()
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
                ScrollableDocumentBody(Modifier.width(360.dp).height(height.dp), action = {
                    TextButton(onClick = {}) { Text("حفظ") }
                }, chrome = {
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
        compose.onNodeWithText("حفظ").assertIsDisplayed().assertHasClickAction()
    }


    @Test fun focusedEditorSaveAndLongHeadingRespectInsetsLtr() = editorInsets(LayoutDirection.Ltr)
    @Test fun focusedEditorSaveAndLongHeadingRespectInsetsRtl() = editorInsets(LayoutDirection.Rtl)

    @Composable
    private fun InsetPhone(direction: LayoutDirection, dark: Boolean, content: @Composable () -> Unit) {
        // Fit the simulated 360x640dp phone inside the test window; its real system bars
        // must not shrink that fixture again before the deliberately injected insets.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = minOf(constraints.maxWidth / 360f, constraints.maxHeight / 640f)
            CompositionLocalProvider(LocalDensity provides Density(scale, 2f), LocalLayoutDirection provides direction) {
                AppTheme(dark, content)
            }
        }
    }

    /** Simulates delivered insets; actual Android window panning/IME still needs live acceptance. */
    @OptIn(ExperimentalTestApi::class)
    private fun editorInsets(direction: LayoutDirection) {
        // This fixture injects its own IME/status insets. A late real keyboard must not
        // pan/resize the test host as well; MainActivity's native resize is tested live.
        compose.runOnUiThread {
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        var imeBottom by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        var text by mutableStateOf("English 42\nخطة عربية\n{\"n\":1}")
        var saved: String? = null
        var backs = 0
        var statusTop = 0
        var keyboardHeight = 0
        var minimumFieldHeight = 0
        compose.setContent {
            InsetPhone(direction, dark) {
                AppTheme(dark) {
                    statusTop = with(LocalDensity.current) { 45.dp.roundToPx() }
                    keyboardHeight = with(LocalDensity.current) { 299.dp.roundToPx() }
                    minimumFieldHeight = with(LocalDensity.current) {
                        (MaterialTheme.typography.bodyLarge.lineHeight.toDp() +
                            MaterialTheme.typography.bodySmall.lineHeight.toDp() + 32.dp).roundToPx()
                    }
                    val navigationBottom = with(LocalDensity.current) { 24.dp.roundToPx() }
                    Box(Modifier.width(360.dp).height(640.dp).testTag("viewport")) {
                        Scaffold(contentWindowInsets = WindowInsets(top = statusTop, bottom = navigationBottom),
                            topBar = {
                                AccessibleAppBar("Accessibility العربية English 42", navigation = {
                                    TextButton(onClick = { backs++ }) { Text("رجوع") }
                                }, actions = {}, windowInsets = WindowInsets(top = statusTop))
                            }) { padding ->
                            Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
                                // Use the VNext project's actual compact tab-strip geometry.
                                // Material ScrollableTabRow can grow differently at 2x font size.
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("نظرة عامة", "Blueprint", "Roadmap", "المهام").forEach { label ->
                                        Surface(onClick = {}, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(Modifier.size(18.dp))
                                                Text(label, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                ScrollableDocumentBody(
                                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets(bottom = imeBottom)),
                                    action = { FilledActionButton(onClick = { saved = text }) { Text("حفظ") } },
                                    chrome = {
                                        ParagraphText("المخطط الهندسي للمشروع")
                                        ParagraphText("إرشاد للمحرر\nEditor guidance\nDraft recovery status\nMore guidance")
                                    }, editor = {
                                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("inset-field"),
                                            textStyle = contentTextStyle(), label = { Text("محتوى المستند") })
                                    })
                            }
                        }
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("inset-field")
        field.performScrollTo().performTextInputSelection(TextRange(8, 10))
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { imeBottom = keyboardHeight }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(8, 10)))
        field.assertIsFocused()
        // No performScrollTo on Save: focus-driven scrolling must never move it off screen.
        fun assertSafeGeometry() {
            val root = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val title = compose.onNodeWithText("Accessibility العربية English 42").assertIsDisplayed()
            val titleBounds = title.fetchSemanticsNode().boundsInRoot
            assertTrue("Heading must remain below the status-bar inset", titleBounds.top >= root.top + statusTop - 1f)
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertFalse("Long heading must remain fully laid out", layouts.single().hasVisualOverflow)
            val save = compose.onNodeWithText("حفظ").assertIsDisplayed().assertIsEnabled()
            val bounds = save.fetchSemanticsNode().boundsInRoot
            assertTrue("Save must remain below the heading", bounds.top >= titleBounds.bottom)
            assertTrue("Save must be entirely above the IME", bounds.bottom <= root.bottom - keyboardHeight + 1f)
            assertTrue("Save must fit horizontally", bounds.left >= root.left && bounds.right <= root.right)
            compose.onNodeWithText("رجوع").assertIsDisplayed().assertHasClickAction()
            val fieldBounds = field.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Visible field must fit a text line, label and padding: actual=${fieldBounds.height}, minimum=$minimumFieldHeight, root=${root.height}, keyboard=$keyboardHeight, field=$fieldBounds",
                fieldBounds.height >= minimumFieldHeight - 1f)
            assertTrue("Visible field must be above the IME", fieldBounds.bottom <= root.bottom - keyboardHeight + 1f)
            assertTrue("Save must not cover the input", bounds.right <= fieldBounds.left || bounds.left >= fieldBounds.right ||
                bounds.bottom <= fieldBounds.top)
        }
        assertSafeGeometry()
        field.performTextInput("99")
        compose.runOnIdle { dark = true }
        field.assertTextContains("English 99\nخطة عربية\n{\"n\":1}")
        assertSafeGeometry()
        compose.onNodeWithText("حفظ").performClick()
        compose.runOnIdle { assertEquals("English 99\nخطة عربية\n{\"n\":1}", saved) }
        compose.onNodeWithText("رجوع").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
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
