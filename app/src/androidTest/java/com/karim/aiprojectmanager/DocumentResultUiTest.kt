package com.karim.aiprojectmanager

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList

/** Real MainActivity ActivityResult callbacks, with a debug-only picker and content provider.
 * This deliberately does not claim to exercise DocumentsUI or an external provider implementation.
 */
@RunWith(AndroidJUnit4::class)
class DocumentResultUiTest {
    @get:Rule val compose = createAndroidComposeRule<IsolatedWorkflowActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var monitor: Instrumentation.ActivityMonitor
    private lateinit var fixtureRoot: File
    private lateinit var fixtureCache: File
    private lateinit var source: File
    private lateinit var original: ByteArray
    private lateinit var provider: File
    private val launches = CopyOnWriteArrayList<String>()
    @Volatile private var unavailablePicker: String? = null
    private val snapshotDirectory get() = File(fixtureCache, "pending-exports")
    private val host get() = checkNotNull(IsolatedWorkflowActivity.current)

    @Before fun prepare() {
        fixtureRoot = compose.activity.fixtureRoot
        fixtureCache = compose.activity.cacheDir
        source = File(compose.activity.filesDir, "projects.json")
        original = source.readBytes()
        provider = File(compose.activity.fixtureRoot, "provider").apply { assertTrue(mkdir()) }
        DeterministicDocumentProvider.directory = provider
        DeterministicDocumentProvider.opens.set(0)
        DeterministicDocumentProvider.delayedStarted = CountDownLatch(1)
        DeterministicDocumentProvider.releaseDelayed = CountDownLatch(1)
        monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action in setOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT)) {
                    launches.add(intent.action!!)
                    if (intent.action == unavailablePicker) throw ActivityNotFoundException("Disposable picker unavailable")
                    intent.component = ComponentName(instrumentation.targetContext, DeterministicPickerActivity::class.java)
                    intent.setPackage(instrumentation.targetContext.packageName)
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
    }
    @After fun clean() {
        DeterministicDocumentProvider.releaseDelayed.countDown()
        instrumentation.removeMonitor(monitor)
        instrumentation.runOnMainSync { DeterministicPickerActivity.current?.deliver(Activity.RESULT_CANCELED) }
        compose.activityRule.scenario.close()
        IsolatedWorkflowActivity.current = null
        DeterministicDocumentProvider.directory = null
        fixtureRoot.deleteRecursively()
    }
    private fun launch(button: String, action: String) {
        compose.onNodeWithText(button).performClick()
        compose.waitUntil(5_000) { DeterministicPickerActivity.current?.ready == true }
        assertEquals(action, launches.last())
    }
    private fun deliver(result: Int, name: String? = null) {
        instrumentation.runOnMainSync {
            checkNotNull(DeterministicPickerActivity.current).deliver(result, name?.let { DeterministicDocumentProvider.uri(it) })
        }
        compose.waitUntil(5_000) { compose.activityRule.scenario.state == Lifecycle.State.RESUMED }
    }
    private fun awaitJobs() {
        lateinit var job: Job
        instrumentation.runOnMainSync { job = host.lifecycleScope.coroutineContext[Job]!! }
        compose.waitUntil(5_000) { job.children.none() }
        compose.waitForIdle()
    }
    private fun recreateBehindPicker() {
        val previous = host
        instrumentation.runOnMainSync { previous.recreate() }
        compose.waitUntil(5_000) { IsolatedWorkflowActivity.current?.let { it !== previous && it.fixtureRoot == fixtureRoot } == true }
    }
    private fun assertLiveUnchanged() { assertArrayEquals(original, source.readBytes()) }
    private fun assertSnapshotsCleared() { assertTrue(snapshotDirectory.listFiles().orEmpty().isEmpty()) }
    private fun backup(name: String) = """[{"id":"backup","name":"$name","tasks":[{"id":"t","title":"Keep","status":"VERIFIED"}]}]"""
    private fun preview() = launch("معاينة نسخة", Intent.ACTION_OPEN_DOCUMENT)
    private fun export() = launch("تصدير JSON", Intent.ACTION_CREATE_DOCUMENT)

    @Test fun cancelledExportClearsSnapshotWithoutOpeningProviderAndNextExportWorks() {
        export()
        assertEquals(1, snapshotDirectory.listFiles().orEmpty().size)
        deliver(Activity.RESULT_CANCELED)
        awaitJobs()
        assertSnapshotsCleared(); assertLiveUnchanged()
        assertEquals(0, DeterministicDocumentProvider.opens.get())
        assertTrue(host.notifications.isEmpty())
        export(); deliver(Activity.RESULT_OK, "export.json"); awaitJobs()
        assertArrayEquals(original, File(provider, "export.json").readBytes())
        assertTrue(host.notifications.contains("تم تصدير النسخة الاحتياطية"))
        assertSnapshotsCleared(); assertLiveUnchanged()
    }
    @Test fun exportSnapshotSurvivesActualActivityRecreationWhilePickerIsOpen() {
        export()
        val snapshot = snapshotDirectory.listFiles()!!.single().readBytes()
        recreateBehindPicker()
        // Change only this fixture after snapshot preparation: export must keep the selected bytes.
        val storage = ProjectStorage(source)
        storage.save(storage.load().map { it.copy(description = "Changed after picker opened") })
        val changed = source.readBytes()
        deliver(Activity.RESULT_OK, "export.json"); awaitJobs()
        assertArrayEquals(snapshot, File(provider, "export.json").readBytes())
        assertArrayEquals(changed, source.readBytes())
        assertTrue(host.notifications.contains("تم تصدير النسخة الاحتياطية"))
        assertSnapshotsCleared()
    }
    @Test fun missingExportSnapshotReportsFailureWithoutWritingDestination() {
        export()
        assertTrue(snapshotDirectory.listFiles()!!.single().delete())
        recreateBehindPicker()
        deliver(Activity.RESULT_OK, "export.json"); awaitJobs()
        assertTrue(host.notifications.contains("فشل التصدير؛ تحقق من الملف في مكان الحفظ"))
        assertFalse(File(provider, "export.json").exists())
        assertEquals(0, DeterministicDocumentProvider.opens.get())
        assertSnapshotsCleared(); assertLiveUnchanged()
    }
    @Test fun exportProviderErrorsPreserveSourceClearSnapshotAndReportFailure() {
        for (name in listOf("unavailable", "denied", "broken-output")) {
            export(); deliver(Activity.RESULT_OK, name); awaitJobs()
            assertEquals("فشل التصدير؛ تحقق من الملف في مكان الحفظ", host.notifications.last())
            assertSnapshotsCleared(); assertLiveUnchanged()
        }
    }
    @Test fun cancelledPreviewDoesNotReadProviderOrChangeSavedBytes() {
        preview(); deliver(Activity.RESULT_CANCELED); awaitJobs()
        assertEquals(0, DeterministicDocumentProvider.opens.get())
        compose.onNodeWithText("معاينة نسخة احتياطية").assertDoesNotExist()
        compose.onNodeWithText("تعذرت المعاينة").assertDoesNotExist()
        assertLiveUnchanged()
    }
    @Test fun unavailableExportPickerClearsPreparedSnapshotAndAllowsRetry() {
        unavailablePicker = Intent.ACTION_CREATE_DOCUMENT
        compose.onNodeWithText("تصدير JSON").performClick()
        awaitJobs()
        assertEquals(listOf(Intent.ACTION_CREATE_DOCUMENT), launches.toList())
        assertEquals(listOf("تعذّر تحضير النسخة الاحتياطية"), host.notifications.toList())
        assertSnapshotsCleared(); assertLiveUnchanged()
        assertEquals(0, DeterministicDocumentProvider.opens.get())
        unavailablePicker = null
        export(); deliver(Activity.RESULT_OK, "export.json"); awaitJobs()
        assertArrayEquals(original, File(provider, "export.json").readBytes())
        assertSnapshotsCleared(); assertLiveUnchanged()
    }
    @Test fun unavailablePreviewPickerReportsErrorWithoutReadingOrChangingData() {
        unavailablePicker = Intent.ACTION_OPEN_DOCUMENT
        compose.onNodeWithText("معاينة نسخة").performClick()
        compose.onNodeWithText("تعذرت المعاينة").assertExists()
        compose.onNodeWithText("تعذّر فتح منتقي الملفات. لم تتغير بياناتك.").assertExists()
        assertEquals(listOf(Intent.ACTION_OPEN_DOCUMENT), launches.toList())
        assertEquals(0, DeterministicDocumentProvider.opens.get())
        assertLiveUnchanged()
    }
    @Test fun selectedBackupUsesRealCallbackAndClosingPreviewIsReadOnly() {
        File(provider, "selected.json").writeText(backup("Selected backup"))
        preview(); deliver(Activity.RESULT_OK, "selected.json"); awaitJobs()
        compose.onNodeWithText("Selected backup — 1 مهمة").assertExists()
        compose.onNodeWithText("إغلاق").performClick()
        compose.onNodeWithText("معاينة نسخة احتياطية").assertDoesNotExist()
        assertEquals(1, DeterministicDocumentProvider.opens.get()); assertLiveUnchanged()
    }
    @Test fun malformedAndOversizedSelectedDocumentsReportErrorsWithoutMutatingProjects() {
        for (bytes in listOf("broken JSON".toByteArray(), ByteArray(8 * 1024 * 1024 + 1))) {
            File(provider, "selected.json").writeBytes(bytes)
            preview(); deliver(Activity.RESULT_OK, "selected.json"); awaitJobs()
            compose.onNodeWithText("تعذرت المعاينة").assertExists()
            compose.onNodeWithText("إغلاق").performClick()
            assertLiveUnchanged()
        }
    }
    @Test fun deniedAndUnavailablePreviewProvidersReportErrorsAndLeaveDataAlone() {
        for (name in listOf("denied", "unavailable")) {
            preview(); deliver(Activity.RESULT_OK, name); awaitJobs()
            compose.onNodeWithText("تعذرت المعاينة").assertExists()
            compose.onNodeWithText("إغلاق").performClick()
            assertLiveUnchanged()
        }
    }
    @Test fun olderPreviewCannotReopenAfterLaterPickerCancellation() {
        File(provider, "delayed.json").writeText(backup("Older backup"))
        preview(); deliver(Activity.RESULT_OK, "delayed.json")
        compose.waitUntil(5_000) { DeterministicDocumentProvider.delayedStarted.count == 0L }
        preview(); deliver(Activity.RESULT_CANCELED)
        DeterministicDocumentProvider.releaseDelayed.countDown(); awaitJobs()
        compose.onNodeWithText("معاينة نسخة احتياطية").assertDoesNotExist()
        compose.onNodeWithText("تعذرت المعاينة").assertDoesNotExist()
        assertLiveUnchanged()
    }
    @Test fun olderPreviewCannotReplaceNewerSelection() {
        File(provider, "delayed.json").writeText(backup("Older backup"))
        File(provider, "selected.json").writeText(backup("Newer backup"))
        preview(); deliver(Activity.RESULT_OK, "delayed.json")
        compose.waitUntil(5_000) { DeterministicDocumentProvider.delayedStarted.count == 0L }
        preview(); deliver(Activity.RESULT_OK, "selected.json")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Newer backup — 1 مهمة").fetchSemanticsNodes().isNotEmpty() }
        DeterministicDocumentProvider.releaseDelayed.countDown(); awaitJobs()
        compose.onNodeWithText("Newer backup — 1 مهمة").assertExists()
        compose.onNodeWithText("Older backup — 1 مهمة").assertDoesNotExist()
        assertLiveUnchanged()
    }
}
