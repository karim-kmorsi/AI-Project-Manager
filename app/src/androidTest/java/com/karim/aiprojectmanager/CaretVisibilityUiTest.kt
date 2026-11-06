package com.karim.aiprojectmanager

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Real system fonts/IME and rendered caret pixels; no production instrumentation hooks.
 * Run on an isolated API 35 phone with system font_scale=2.0 and TalkBack disabled.
 * A text-layout cursor height is only the expected height, never evidence of visibility.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 35)
class CaretVisibilityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun blueprintCaretVisibleLtr() = editor("Blueprint", LayoutDirection.Ltr)
    @Test fun blueprintCaretVisibleRtl() = editor("Blueprint", LayoutDirection.Rtl)
    @Test fun roadmapCaretVisibleLtr() = editor("Roadmap", LayoutDirection.Ltr)
    @Test fun roadmapCaretVisibleRtl() = editor("Roadmap", LayoutDirection.Rtl)

    private fun editor(document: String, direction: LayoutDirection) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires actual 2x system fonts; run this class in its dedicated environment",
            abs(context.resources.configuration.fontScale - 2f) < .01f)
        val root = File(context.cacheDir, "caret-${System.nanoTime()}").apply { assertTrue(mkdirs()) }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
        val file = File(root, "projects.json")
        val text = (0..11).joinToString("\n") { if (it % 2 == 0) "English line $it task-42" else "خطة عربية $it" }
        ProjectStorage(file).save(listOf(ProjectItem("caret", "Accessibility العربية English 42", "Synthetic", text, text)))
        val original = file.readBytes()
        var dark by mutableStateOf(false)
        var visible by mutableStateOf(true)
        compose.runOnUiThread { compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
        try {
            compose.setContent {
                if (visible) CompositionLocalProvider(LocalContext provides isolated, LocalLayoutDirection provides direction) {
                    AppTheme(dark) { ProjectManagerApp(ProjectStorage(file)) }
                }
            }
            compose.onNodeWithText("Accessibility العربية English 42").performClick()
            compose.onNodeWithText(document).performScrollTo().performClick()
            val field = compose.onNode(hasSetTextAction())
            field.performScrollTo().performClick()
            compose.mainClock.autoAdvance = false
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark = night }
                compose.mainClock.advanceTimeByFrame()
                for (keyboard in listOf(true, false, true)) {
                    setKeyboard(keyboard)
                    for (offset in listOf(0, text.indexOf("خطة", text.length / 2), text.length)) {
                        field.performTextInputSelection(TextRange(offset))
                        compose.mainClock.advanceTimeByFrame()
                        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(offset)))
                        field.assertIsFocused()
                        assertPaintedCaret(field, offset, night, "$document/$direction/dark=$night/ime=$keyboard/offset=$offset")
                    }
                    // Scroll within the document, then move the cursor to a different line. Starting
                    // at the top avoids overscrolling into the separate guidance/recovery region.
                    field.performTextInputSelection(TextRange(0))
                    compose.mainClock.advanceTimeByFrame()
                    field.performTouchInput { swipeUp() }
                    field.performTextInputSelection(TextRange(text.length))
                    compose.mainClock.advanceTimeByFrame()
                    field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(text.length)))
                    assertPaintedCaret(field, text.length, night, "$document/$direction/after-scroll")
                    compose.onNodeWithText("حفظ").assertIsDisplayed()
                    compose.onNodeWithText("Accessibility العربية English 42").assertIsDisplayed()
                }
            }
            field.assertTextContains(text)
            assertArrayEquals("Caret/IME navigation must not save or change project data", original, file.readBytes())
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            root.deleteRecursively() // This test's synthetic fixture only.
        }
    }

    private fun setKeyboard(show: Boolean) {
        val animations = java.util.concurrent.atomic.AtomicInteger()
        compose.runOnUiThread {
            compose.activity.window.decorView.setWindowInsetsAnimationCallback(object : android.view.WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: android.view.WindowInsetsAnimation) { animations.incrementAndGet() }
                override fun onProgress(insets: WindowInsets, runningAnimations: MutableList<android.view.WindowInsetsAnimation>) = insets
                override fun onEnd(animation: android.view.WindowInsetsAnimation) { animations.decrementAndGet() }
            })
            val view = compose.activity.window.decorView
            val manager = compose.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (show) manager.showSoftInput(view.findFocus(), 0) else manager.hideSoftInputFromWindow(view.windowToken, 0)
        }
        // Native IME/inset delivery is asynchronous and not driven by the Compose test clock.
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            var shown = false
            compose.runOnUiThread { shown = compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            shown == show && animations.get() == 0
        }
        compose.runOnUiThread { compose.activity.window.decorView.setWindowInsetsAnimationCallback(null) }
        compose.waitForIdle()
    }

    private fun assertPaintedCaret(field: SemanticsNodeInteraction, offset: Int, dark: Boolean, label: String) {
        val results = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        val expectedHeight = results.single().getCursorRect(offset).height
        val density = compose.activity.resources.displayMetrics.density
        val inset = ceil(8 * density).toInt() // Exclude the outline; not a caret-coordinate offset.
        val color = appearanceColors(dark).primary.toArgb()
        var best = 0
        // Caret blinks. Sample a full blink cycle in clock frames, not arbitrary wall-clock sleeps.
        var safe = android.graphics.RectF()
        repeat(5) {
            val node = field.fetchSemanticsNode()
            // Use the window's transform, not a scrolled node's position: semantics positionOnScreen
            // and positionInWindow can use different clipping origins for a partly scrolled field.
            val screen = IntArray(2)
            val window = IntArray(2)
            compose.runOnUiThread {
                compose.activity.window.decorView.getLocationOnScreen(screen)
                compose.activity.window.decorView.getLocationInWindow(window)
            }
            val clipped = node.boundsInWindow.translate(androidx.compose.ui.geometry.Offset(
                (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat()))
            val frame = Rect()
            compose.runOnUiThread { compose.activity.window.decorView.getWindowVisibleDisplayFrame(frame) }
            safe = android.graphics.RectF(max(clipped.left, frame.left.toFloat()), max(clipped.top, frame.top.toFloat()),
                min(clipped.right, frame.right.toFloat()), min(clipped.bottom, frame.bottom.toFloat()))
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Screenshot unavailable: $label")
            best = max(best, longestPaintedRun(bitmap, safe, inset, color))
            bitmap.recycle()
            // One physical pixel accounts for rasterized top/bottom edges.
            if (best + 1 >= expectedHeight) return
            compose.mainClock.advanceTimeBy(250)
            compose.waitForIdle()
        }
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(cache, "caret-failure.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        File(cache, "caret-failure.txt").writeText(compose.onAllNodes(isRoot()).printToString())
        fail("Active caret clipped/missing: $label; painted=$best expected=$expectedHeight safe=$safe")
    }

    private fun longestPaintedRun(bitmap: Bitmap, safe: android.graphics.RectF, inset: Int, color: Int): Int {
        var best = 0
        for (x in (ceil(safe.left).toInt() + inset).coerceAtLeast(0) until (safe.right.toInt() - inset).coerceAtMost(bitmap.width)) {
            var run = 0
            for (y in ceil(safe.top).toInt().coerceAtLeast(0) until safe.bottom.toInt().coerceAtMost(bitmap.height)) {
                val pixel = bitmap.getPixel(x, y)
                // Small RGB tolerance permits antialiasing; approved text differs from the cursor.
                val matches = listOf(0, 8, 16).all { shift -> abs((pixel shr shift and 255) - (color shr shift and 255)) <= 3 }
                run = if (matches) run + 1 else 0
                best = max(best, run)
            }
        }
        return best
    }
}
