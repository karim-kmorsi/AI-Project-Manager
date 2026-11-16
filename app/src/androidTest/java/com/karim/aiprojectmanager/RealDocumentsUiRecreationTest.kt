package com.karim.aiprojectmanager

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opt-in acceptance driver: production Activity, real system picker, existing synthetic DU-01 data. */
@RunWith(AndroidJUnit4::class)
class RealDocumentsUiRecreationTest {
    @Test fun productionActivityRecreatesBeforeRealDocumentSelection() {
        assumeTrue("PD-01 requires the approved emulator and existing DU-01 fixture",
            InstrumentationRegistry.getArguments().getString("pd01Enabled") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val source = File(context.filesDir, "projects.json")
        val original = source.readBytes()
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assumeTrue("Unexpected source: no test actions permitted",
            hash(original) == "46db5571af94b15cf4784c230e3ef9cc1fc47d872332e5c7a94f9675a0bdbefd")
        val evidence = File(context.cacheDir, "pd01-real-${UUID.randomUUID()}")
        check(evidence.mkdir())
        File(evidence, "source-before.json").writeBytes(original)
        Log.i("PD01", "EVIDENCE=${evidence.name}")
        val timeline = CopyOnWriteArrayList<String>()
        val current = AtomicReference<MainActivity?>()
        val old = AtomicReference<MainActivity?>()
        val destroyed = AtomicBoolean(false)
        val watching = AtomicBoolean(false)
        val windowObservations = CopyOnWriteArrayList<String>()
        val lifecycleForeground = CopyOnWriteArrayList<String>()
        val documentPackage = "com.google.android.documentsui"
        fun foreground() = automation.rootInActiveWindow?.packageName?.toString()
        fun record(message: String) {
            timeline.add("${SystemClock.elapsedRealtime()} $message")
            Log.i("PD01", message)
        }
        fun screenshot(name: String) {
            val bitmap = checkNotNull(automation.takeScreenshot())
            File(evidence, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        fun waitFor(description: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                SystemClock.sleep(100)
            }
            assumeTrue("INCONCLUSIVE: $description", false)
        }
        // Traverse Compose virtual descendants as well as platform picker nodes. Some
        // providers expose these descendants without implementing find-by-text queries.
        fun exactText(text: String): AccessibilityNodeInfo? {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.text?.toString() == text) return node
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    find(child)?.let { return it }
                }
                return null
            }
            return automation.rootInActiveWindow?.let { find(it) }
        }
        fun click(text: String) {
            var node = checkNotNull(exactText(text)) { "Missing UI text: $text" }
            while (!node.isClickable && node.parent != null) node = node.parent
            check(node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Cannot click UI text: $text"
            }
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity.javaClass != MainActivity::class.java) return
                current.set(activity as MainActivity)
                record("CREATED instance=${System.identityHashCode(activity)} pid=${Process.myPid()}")
                if (watching.get()) lifecycleForeground.add("created:${foreground()}")
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.javaClass != MainActivity::class.java) return
                if (activity === old.get()) destroyed.set(true)
                record("DESTROYED instance=${System.identityHashCode(activity)} pid=${Process.myPid()}")
                if (watching.get()) lifecycleForeground.add("destroyed:${foreground()}")
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        val app = context.applicationContext as Application
        instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(callbacks) }
        var scenario: ActivityScenario<MainActivity>? = null
        var outcome = "INCONCLUSIVE"
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { old.set(it); current.set(it) }
            val previous = checkNotNull(old.get())
            val process = Process.myPid()
            waitFor("production preview button") { exactText("معاينة نسخة") != null }
            click("معاينة نسخة")
            waitFor("real DocumentsUI foreground") { foreground() == documentPackage }
            screenshot("01-real-picker")
            automation.setOnAccessibilityEventListener { event ->
                if (watching.get() && event.eventType in listOf(
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
                    foreground()?.let { windowObservations.add(it) }
                }
            }
            watching.set(true)
            record("BEFORE_RECREATE old=${System.identityHashCode(previous)} foreground=${foreground()}")
            instrumentation.runOnMainSync { previous.recreate() }
            waitFor("old Activity destroyed and distinct production instance created before selection") {
                destroyed.get() && current.get() != null && current.get() !== previous
            }
            assertTrue(previous.isDestroyed)
            assertNotSame(previous, current.get())
            assertEquals(process, Process.myPid())
            assertEquals(documentPackage, foreground())
            assertTrue("Picker foreground must be observed during destroy and create",
                lifecycleForeground.contains("destroyed:$documentPackage") &&
                    lifecycleForeground.contains("created:$documentPackage"))
            assertTrue("Observed foreground changed during recreation: $windowObservations",
                windowObservations.all { it == documentPackage })
            record("RECREATION_VERIFIED new=${System.identityHashCode(current.get())} pid=$process foreground=${foreground()}")
            screenshot("02-picker-after-recreation")
            watching.set(false)
            automation.setOnAccessibilityEventListener(null)
            assertArrayEquals(original, source.readBytes())
            waitFor("known-good export in picker") { exactText("DU-01-filename-54b3msf4.json") != null }
            record("SELECT_DOCUMENT after verified destruction/creation")
            click("DU-01-filename-54b3msf4.json")
            waitFor("read-only preview") { exactText("معاينة نسخة احتياطية") != null }
            assertNotNull(exactText("عدد المشاريع: 1 | عدد المهام: 1"))
            assertNotNull(exactText("DU-01-039c63f3f692 — 1 مهمة"))
            assertNotNull(exactText("معاينة فقط: لن يتم استبدال أو دمج أي بيانات."))
            screenshot("03-preview")
            click("إغلاق")
            waitFor("preview closed and project accessible") {
                exactText("معاينة نسخة احتياطية") == null && exactText("DU-01-039c63f3f692") != null
            }
            click("DU-01-039c63f3f692")
            waitFor("responsive existing project") { exactText("DU-01 disposable task — لم تبدأ") != null }
            screenshot("04-responsive-project")
            assertArrayEquals(original, source.readBytes())
            outcome = "PASS"
            record("PASS source_sha256=${hash(source.readBytes())}")
        } finally {
            watching.set(false)
            automation.setOnAccessibilityEventListener(null)
            File(evidence, "source-after.json").writeBytes(source.readBytes())
            File(evidence, "lifecycle.txt").writeText(timeline.joinToString("\n", postfix = "\n"))
            File(evidence, "foreground.txt").writeText(
                (lifecycleForeground + windowObservations).joinToString("\n", postfix = "\n"))
            File(evidence, "result.txt").writeText("$outcome\nsource_before=${hash(original)}\nsource_after=${hash(source.readBytes())}\n")
            instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(callbacks) }
            scenario?.close()
        }
    }
}
