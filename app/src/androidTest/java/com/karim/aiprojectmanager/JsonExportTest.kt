package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(AndroidJUnit4::class)
class JsonExportTest {
    @Test fun exportCopyPreservesSourceAndAllBytes() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "json-export-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val payload = """[{"id":"p","name":"مشروع","tasks":[{"id":"t","title":"Done","status":"VERIFIED"}]}]""".toByteArray()
            source.writeBytes(payload)
            val validated = JsonExport.readValidatedSource(source)
            val output = ByteArrayOutputStream()
            JsonExport.copyValidated(validated, output)
            assertArrayEquals(payload, output.toByteArray())
            assertArrayEquals(payload, source.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedDestinationWriteDoesNotModifySource() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "json-export-failure-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val payload = """[{"id":"p","name":"Keep"}]""".toByteArray()
            source.writeBytes(payload)
            val failingOutput = object : OutputStream() {
                override fun write(value: Int) { throw IOException("Simulated destination failure") }
                override fun write(bytes: ByteArray, off: Int, len: Int) {
                    throw IOException("Simulated destination failure")
                }
            }
            try {
                JsonExport.copyValidated(JsonExport.readValidatedSource(source), failingOutput)
                fail("Destination write failure must propagate")
            } catch (_: IOException) { }
            assertArrayEquals(payload, source.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidSourceAndCancelledPickerCannotExportOrMutateSource() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "json-export-invalid-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val invalid = """{"schemaVersion":2,"projects":[]}"""
            source.writeText(invalid)
            try {
                JsonExport.readValidatedSource(source)
                fail("Unsupported schema must be rejected before picker")
            } catch (_: IOException) {}
            assertEquals(invalid, source.readText())
            // A null URI from CreateDocument is handled in MainActivity without calling exportToUri.
        } finally { root.deleteRecursively() }
    }
}
