package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/**
 * Executes on Android, where org.json has platform semantics.
 * Use a temporary, app-private file. Does not read or edit actual user data.
 */
@RunWith(AndroidJUnit4::class)
class LegacyStorageTest {
    @Test fun corruptJsonIsNotSilentlyReplaced() {
        val dir = Files.createTempDirectory("corrupt-storage-test").toFile()
        try {
            val file = File(dir, "projects.json")
            val original = "{definitely invalid JSON"
            file.writeText(original)
            val storage = ProjectStorage(file)
            try {
                storage.load()
                fail("Corrupt data should not be treated as empty")
            } catch (_: IOException) { }
            try {
                storage.save(emptyList())
                fail("Save must not overwrite an unreadable file")
            } catch (_: IOException) { }
            assertEquals(original, file.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun legacyProjectsSurviveReadWriteAndReopen() {
        val dir = Files.createTempDirectory("project-storage-test").toFile()
        try {
            val file = File(dir, "projects.json")
            file.writeText(
                """
                [
                  {
                    "id":"old-project-1",
                    "name":"مشروع تجريبي",
                    "description":"legacy project",
                    "blueprint":"اختبار حفظ بيانات المشروع 123",
                    "roadmap":"Step A",
                    "tasks":[
                      {"id":"task-1","title":"Demo","status":"NOT_STARTED"},
                      {"id":"task-2","title":"Verified legacy","status":"VERIFIED"}
                    ]
                  }
                ]
                """.trimIndent()
            )
            val initial = ProjectStorage(file).load()
            assertEquals(1, initial.size)
            assertEquals("اختبار حفظ بيانات المشروع 123", initial[0].blueprint)
            assertEquals("Step A", initial[0].roadmap)
            assertEquals("VERIFIED", initial[0].tasks[1].status)
            ProjectStorage(file).save(initial)
            val reopened = ProjectStorage(file).load()
            assertEquals(initial, reopened)
            assertTrue(file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
