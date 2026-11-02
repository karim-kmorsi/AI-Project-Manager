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
}
