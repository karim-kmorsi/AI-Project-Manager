package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocalBackupTest {
    @Test fun snapshotPreservesExactLegacyBytesWithoutTouchingOriginal() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup-test-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val backup = File(root, "snapshot.json")
            val bytes = """[
              {"id":"p1","name":"مشروع","description":"original","blueprint":"Blueprint",
               "roadmap":"خطة","tasks":[{"id":"t1","title":"Done","status":"VERIFIED"}]}
            ]""".toByteArray(Charsets.UTF_8)
            source.writeBytes(bytes)
            val preview = LocalBackup.createSnapshot(source, backup)
            assertEquals(1, preview.projectCount)
            assertEquals(1, preview.taskCount)
            assertEquals(64, preview.sha256.length)
            assertArrayEquals(bytes, backup.readBytes())
            assertArrayEquals(bytes, source.readBytes())
            try {
                LocalBackup.createSnapshot(source, backup)
                fail("Existing backup must never be overwritten")
            } catch (_: IOException) { }
            assertArrayEquals(bytes, backup.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidAndDuplicateBackupsAreRejectedWithoutCreatingSnapshot() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup-invalid-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val backup = File(root, "snapshot.json")
            for (payload in listOf(
                """{"schemaVersion":2,"projects":[]}""",
                """[{"id":"same","name":"A"},{"id":"same","name":"B"}]""",
                """[{"id":"p","name":"A","tasks":[{"id":"x","title":"One"},{"id":"x","title":"Two"}]}]""",
                """[{"id":"p","name":"A","blueprint":123}]""",
                "broken"
            )) {
                source.writeText(payload)
                try {
                    LocalBackup.createSnapshot(source, backup)
                    fail("Invalid backup must be rejected")
                } catch (_: IOException) { }
                assertFalse(backup.exists())
                assertEquals(payload, source.readText())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun interruptedWriteNeverPublishesPartialBackup() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup-interrupted-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val destination = File(root, "backup.json")
            val bytes = """[{"id":"safe","name":"Original","tasks":[]}]""".toByteArray()
            source.writeBytes(bytes)
            try {
                LocalBackup.createSnapshotWithWriter(source, destination) { stream, input ->
                    stream.write(input, 0, input.size / 2)
                    throw IOException("Simulated disk full/interrupted write")
                }
                fail("Expected simulated failure")
            } catch (_: IOException) { }
            assertFalse("Incomplete backup must not be published", destination.exists())
            assertArrayEquals(bytes, source.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun existingIncompletePendingFileMustNotBeReusedOrPublished() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "backup-pending-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val destination = File(root, "backup.json")
            val bytes = """[{"id":"safe","name":"Original"}]""".toByteArray()
            source.writeBytes(bytes)
            val pending = File(destination.path + ".new")
            val incomplete = "incomplete backup".toByteArray()
            pending.writeBytes(incomplete)
            try {
                LocalBackup.createSnapshot(source, destination)
                fail("Existing pending file must block a new snapshot")
            } catch (_: IOException) { }
            assertFalse(destination.exists())
            assertArrayEquals(incomplete, pending.readBytes())
            assertArrayEquals(bytes, source.readBytes())
        } finally { root.deleteRecursively() }
    }

}
