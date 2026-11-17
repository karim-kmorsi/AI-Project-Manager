package com.karim.aiprojectmanager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Synthetic storage and empty session/preferences; never launches MainActivity or a picker. */
@RunWith(AndroidJUnit4::class)
class RestoreConfirmationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private data class Fixture(
        val root: File,
        val appFiles: File,
        val context: Context,
        val storage: ProjectStorage,
        val original: ByteArray,
        val incoming: ByteArray,
        val staged: RestoreCandidate.Staged
    )

    private fun fixture(): Fixture {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "restore-confirmation-${System.nanoTime()}")
        assertTrue(root.mkdir())
        val appFiles = File(root, "isolated-app-files")
        val context = isolatedWorkflowContext(base, root, appFiles)
        val live = File(File(root, "live").apply { assertTrue(mkdirs()) }, "projects.json")
        val storage = ProjectStorage(live)
        storage.save(listOf(ProjectItem(
            "existing", "Disposable original", "Keep",
            tasks = listOf(TaskItem("old-task", "Existing verified task", "VERIFIED"))
        )))
        val original = live.readBytes()
        val incoming = """[{"id":"restored","name":"Disposable backup","tasks":[
            {"id":"pending","title":"Pending task","status":"NOT_STARTED"},
            {"id":"done","title":"Verified task","status":"VERIFIED"}]}]""".trimIndent().toByteArray()
        val candidate = File(root, "candidate.json").apply { writeBytes(incoming) }
        val summary = RestorePreview.inspect(incoming)
        return Fixture(
            root, appFiles, context, storage, original, incoming,
            RestoreCandidate.Staged(candidate, summary, RestoreTransaction.hash(original))
        )
    }

    private fun composeRestoreUi(fixture: Fixture): MutableState<RestoreCandidate.Staged?> {
        val preview = mutableStateOf<RestorePreview.Summary?>(fixture.staged.summary)
        val candidate = mutableStateOf<RestoreCandidate.Staged?>(fixture.staged)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides fixture.context) {
                AppTheme(dark = false) {
                    ProjectManagerApp(
                        fixture.storage,
                        restorePreview = preview.value,
                        restoreCandidate = candidate.value,
                        onDismissPreview = {
                            candidate.value?.file?.delete()
                            candidate.value = null
                            preview.value = null
                        }
                    )
                }
            }
        }
        return candidate
    }

    @Test fun cancellingConfirmationLeavesDisposableLiveDataUnchanged() {
        val fixture = fixture()
        try {
            val candidate = composeRestoreUi(fixture)
            compose.onLocalizedText("Review restore", "مراجعة الاستعادة").performClick()
            compose.onLocalizedText("Replace local projects?", "هل تريد استبدال المشاريع المحلية؟").assertIsDisplayed()
            compose.runOnIdle { assertArrayEquals(fixture.original, fixture.storage.file.readBytes()) }
            compose.onLocalizedText("Cancel", "إلغاء").performClick()
            compose.onLocalizedText("Replace local projects?", "هل تريد استبدال المشاريع المحلية؟").assertDoesNotExist()
            compose.runOnIdle {
                assertArrayEquals(fixture.original, fixture.storage.file.readBytes())
                assertEquals(1, fixture.storage.load().size)
                assertNotNull(candidate.value)
                assertFalse(File(fixture.appFiles, "restore-recovery").exists())
            }
        } finally {
            compose.activityRule.scenario.close()
            assertTrue(fixture.root.deleteRecursively())
        }
    }

    @Test fun confirmedRestoreReplacesOnlyDisposableDataAndKeepsSafetySnapshot() {
        val fixture = fixture()
        try {
            val candidate = composeRestoreUi(fixture)
            compose.onLocalizedText("Review restore", "مراجعة الاستعادة").performClick()
            compose.onLocalizedText("Replace local projects?", "هل تريد استبدال المشاريع المحلية؟").assertIsDisplayed()
            compose.runOnIdle { assertArrayEquals(fixture.original, fixture.storage.file.readBytes()) }
            compose.onLocalizedText("Replace projects", "استبدال المشاريع").performClick()
            compose.waitUntil(10_000) { candidate.value == null }
            compose.runOnIdle {
                assertArrayEquals(fixture.incoming, fixture.storage.file.readBytes())
                val restored = fixture.storage.load().single()
                assertEquals("Disposable backup", restored.name)
                assertEquals(
                    listOf(
                        TaskItem("pending", "Pending task", "NOT_STARTED"),
                        TaskItem("done", "Verified task", "VERIFIED")
                    ),
                    restored.tasks
                )
                val snapshots = File(fixture.appFiles, "restore-recovery").listFiles().orEmpty()
                assertEquals(1, snapshots.size)
                assertArrayEquals(fixture.original, snapshots.single().readBytes())
            }
        } finally {
            compose.activityRule.scenario.close()
            assertTrue(fixture.root.deleteRecursively())
        }
    }
}

/** No real session or snapshot preferences can be read or written by these UI fixtures. */
internal fun isolatedWorkflowContext(base: Context, root: File, appFiles: File): Context =
    object : ContextWrapper(base) {
        private val emptyPreferences = object : SharedPreferences {
            override fun getAll(): Map<String, *> = emptyMap<String, Any>()
            override fun getString(key: String?, defValue: String?): String? = defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                defValues?.toMutableSet()
            override fun getInt(key: String?, defValue: Int): Int = defValue
            override fun getLong(key: String?, defValue: Long): Long = defValue
            override fun getFloat(key: String?, defValue: Float): Float = defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
            override fun contains(key: String?): Boolean = false
            override fun edit(): SharedPreferences.Editor = error("Unexpected preference write in isolated UI test")
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = appFiles.apply { check(isDirectory || mkdirs()) }
        override fun getCacheDir(): File = File(root, "isolated-cache").apply { check(isDirectory || mkdirs()) }
        override fun getNoBackupFilesDir(): File =
            File(root, "isolated-no-backup").apply { check(isDirectory || mkdirs()) }
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = emptyPreferences
    }
