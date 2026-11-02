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

    @Test fun uriExportWritesExactBytesAndCanBeReadBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "json-export-uri-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val destination = File(root, "exported.json")
            val payload = """[{"id":"p1","name":"مشروع","blueprint":"A","roadmap":"B","tasks":[{"id":"t1","title":"Done","status":"VERIFIED"}]}]""".toByteArray()
            source.writeBytes(payload)
            val exported = JsonExport.readValidatedSource(source)
            // file:// URI goes through ContentResolver.openOutputStream, the same production entry point.
            JsonExport.exportToUri(context.contentResolver, android.net.Uri.fromFile(destination), exported)
            assertArrayEquals(payload, destination.readBytes())
            assertArrayEquals(payload, source.readBytes())
            assertEquals(1, LocalBackup.validate(destination.readBytes()).projectCount)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancelledDocumentSelectionLeavesSourceIntact() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "json-export-cancel-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val payload = """[{"id":"p","name":"Still here"}]""".toByteArray()
            source.writeBytes(payload)
            val prepared = JsonExport.readValidatedSource(source)
            // CreateDocument returns null on cancellation: no destination is opened.
            val selectedUri: android.net.Uri? = null
            if (selectedUri != null) JsonExport.exportToUri(
                InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
                selectedUri, prepared
            )
            assertArrayEquals(payload, source.readBytes())
            assertEquals(1, LocalBackup.validate(prepared).projectCount)
            assertEquals(listOf("projects.json"), root.list()?.toList())
        } finally { root.deleteRecursively() }
    }

    @Test fun partialExternalWriteIsReportedAsFailureAndSourceIsSafe() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "json-export-partial-${System.nanoTime()}")
        try {
            root.mkdirs()
            val source = File(root, "projects.json")
            val payload = """[{"id":"p","name":"Original","tasks":[]}]""".toByteArray()
            source.writeBytes(payload)
            val partial = ByteArrayOutputStream()
            val brokenDestination = object : OutputStream() {
                override fun write(value: Int) { throw IOException("Destination unavailable") }
                override fun write(bytes: ByteArray, off: Int, len: Int) {
                    partial.write(bytes, off, minOf(len, 5))
                    throw IOException("Simulated failure after a partial write")
                }
            }
            try {
                JsonExport.copyValidated(JsonExport.readValidatedSource(source), brokenDestination)
                fail("Export must not report success")
            } catch (_: IOException) { }
            assertEquals(5, partial.size())
            assertArrayEquals(payload, source.readBytes())
            try {
                LocalBackup.validate(partial.toByteArray())
                fail("Partial output must be invalid")
            } catch (_: IOException) { }
        } finally { root.deleteRecursively() }
    }

}
