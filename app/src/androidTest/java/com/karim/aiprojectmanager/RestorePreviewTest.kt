package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RestorePreviewTest {
    @Test fun previewSummarizesProjectsAndLeavesLiveDataUnchanged() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "restore-preview-${System.nanoTime()}")
        try {
            root.mkdirs()
            val live = File(root, "projects.json")
            val backup = File(root, "candidate.json")
            val saved = """[{"id":"live","name":"Existing","tasks":[]}]""".toByteArray()
            val candidate = """[
              {"id":"p1","name":"مشروع","blueprint":"Text","tasks":[{"id":"t1","title":"Done","status":"VERIFIED"}]},
              {"id":"p2","name":"Second","tasks":[{"id":"t2","title":"Pending"},{"id":"t3","title":"Working"}]}
            ]""".toByteArray()
            live.writeBytes(saved)
            backup.writeBytes(candidate)
            val summary = RestorePreview.inspect(backup)
            assertEquals(2, summary.projects.size)
            assertEquals(listOf("p1", "p2"), summary.projects.map { it.id })
            assertEquals(listOf("مشروع", "Second"), summary.projects.map { it.name })
            assertEquals(listOf(1, 2), summary.projects.map { it.taskCount })
            assertEquals(3, summary.totalTasks)
            assertEquals(64, summary.sha256.length)
            assertArrayEquals(saved, live.readBytes())
            assertArrayEquals(candidate, backup.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectedBackupDoesNotTouchLiveFile() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "restore-reject-${System.nanoTime()}")
        try {
            root.mkdirs()
            val live = File(root, "projects.json")
            val source = File(root, "candidate.json")
            val original = """[{"id":"safe","name":"Keep"}]"""
            live.writeText(original)
            for (invalid in listOf(
                "not-json",
                """{"schemaVersion":2,"projects":[]}""",
                """[{"id":"duplicate","name":"One"},{"id":"duplicate","name":"Two"}]""",
                """[{"id":"p","name":"Wrong","tasks":[{"id":"x","title":"A"},{"id":"x","title":"B"}]}]"""
            )) {
                source.writeText(invalid)
                try {
                    RestorePreview.inspect(source)
                    fail("Invalid source must fail validation")
                } catch (_: IOException) {}
                assertEquals(original, live.readText())
                assertEquals(invalid, source.readText())
            }
        } finally { root.deleteRecursively() }
    }
}
