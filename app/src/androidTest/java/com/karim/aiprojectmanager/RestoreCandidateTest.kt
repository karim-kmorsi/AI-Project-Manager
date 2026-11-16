package com.karim.aiprojectmanager

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RestoreCandidateTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun validSelectedDocumentCanBePreviewedWithoutChangingLiveData() {
        val root = File(context.cacheDir, "restore-document-${System.nanoTime()}")
        try {
            assertTrue(root.mkdirs())
            val live = File(root, "projects.json")
            val selected = File(root, "candidate.json")
            val original = """[{"id":"live","name":"Unchanged"}]""".toByteArray()
            val incoming = """[{"id":"a","name":"مشروع","tasks":[{"id":"t","title":"Done","status":"VERIFIED"}]}]""".toByteArray()
            live.writeBytes(original)
            selected.writeBytes(incoming)
            val preview = RestoreCandidate.inspectSelection(context.contentResolver, Uri.fromFile(selected))
            assertNotNull(preview)
            assertEquals(1, preview!!.projects.size)
            assertEquals("مشروع", preview.projects[0].name)
            assertEquals(1, preview.totalTasks)
            assertArrayEquals(original, live.readBytes())
            assertArrayEquals(incoming, selected.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationDoesNotReadOrWriteFiles() {
        val root = File(context.cacheDir, "restore-cancel-${System.nanoTime()}")
        try {
            assertTrue(root.mkdirs())
            val live = File(root, "projects.json")
            val original = """[{"id":"live","name":"Safe"}]""".toByteArray()
            live.writeBytes(original)
            assertNull(RestoreCandidate.inspectSelection(context.contentResolver, null))
            assertArrayEquals(original, live.readBytes())
            assertEquals(listOf("projects.json"), root.list()?.toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidOrOversizedSelectionIsRejectedWithoutChangingLiveData() {
        val root = File(context.cacheDir, "restore-reject-uri-${System.nanoTime()}")
        try {
            assertTrue(root.mkdirs())
            val live = File(root, "projects.json")
            val selected = File(root, "candidate.json")
            val original = """[{"id":"live","name":"Still safe"}]""".toByteArray()
            live.writeBytes(original)
            for (invalid in listOf("broken", """{"schemaVersion":2,"projects":[]}""",
                    """[{"id":"x","name":"A"},{"id":"x","name":"B"}]""")) {
                selected.writeText(invalid)
                try {
                    RestoreCandidate.inspectSelection(context.contentResolver, Uri.fromFile(selected))
                    fail("Invalid candidate should be rejected")
                } catch (_: IOException) {}
                assertArrayEquals(original, live.readBytes())
            }
            selected.writeBytes(ByteArray(8 * 1024 * 1024 + 1) { 'x'.code.toByte() })
            try {
                RestoreCandidate.inspectSelection(context.contentResolver, Uri.fromFile(selected))
                fail("Oversized backup should be rejected")
            } catch (_: IOException) {}
            assertArrayEquals(original, live.readBytes())
        } finally { root.deleteRecursively() }
    }
    @Test fun missingDocumentFailsSafelyAndLiveDataRemainsUnchanged() {
        val root = File(context.cacheDir, "restore-missing-${System.nanoTime()}")
        try {
            assertTrue(root.mkdirs())
            val live = File(root, "projects.json")
            val original = """[{"id":"live","name":"Original"}]""".toByteArray()
            live.writeBytes(original)
            val missing = File(root, "nonexistent.json")
            try {
                RestoreCandidate.inspectSelection(context.contentResolver, Uri.fromFile(missing))
                fail("Missing document must not be accepted")
            } catch (_: IOException) { }
            assertArrayEquals(original, live.readBytes())
            assertFalse(missing.exists())
        } finally { root.deleteRecursively() }
    }

}
