package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class ExportAndAtomicSafetyTest {
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("export-atomic-audit").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    @Test fun silentlyUnpublishedAtomicWriteIsReportedAndStagingIsRetained() = fixture { root ->
        val file = File(root, "projects.json")
        file.writeText("original")
        try {
            writeVerifiedAtomic(file, "new bytes".toByteArray()) { _, stream -> stream.close() }
            fail("A silent publication failure must not be reported as Saved")
        } catch (_: IOException) { }
        assertEquals("original", file.readText())
        assertEquals("new bytes", File(file.path + ".new").readText())
    }
    @Test fun exportSnapshotSurvivesNewStoreInstanceAndDiscardsOnlyOwnPayload() = fixture { root ->
        val bytes = """[{"id":"p","name":"مشروع English"}]""".toByteArray()
        val store = PendingExport(root)
        val token = store.stage(bytes)
        val other = store.stage(bytes)
        assertArrayEquals(bytes, PendingExport(root).read(token))
        store.discard(token)
        try { store.read(token); fail("Discarded snapshot must be unavailable") } catch (_: IOException) { }
        assertArrayEquals(bytes, store.read(other))
        try { store.read("../projects"); fail("Path traversal must fail") } catch (_: IOException) { }
    }
    @Test fun interruptedFirstProjectWriteIsPreservedAndBlocksFreshSave() = fixture { root ->
        val file = File(root, "projects.json")
        val pending = File(root, "projects.json.new")
        pending.writeText("partial recoverable content")
        val storage = ProjectStorage(file)
        try { storage.load(); fail("Must not present interrupted write as empty storage") } catch (_: IOException) { }
        try { storage.save(emptyList()); fail("Must not overwrite interrupted write") } catch (_: IOException) { }
        assertEquals("partial recoverable content", pending.readText())
        assertFalse(file.exists())
    }
    @Test fun snapshotCannotTruncateSourceThroughAtomicSidecarAlias() = fixture { root ->
        val destination = File(root, "snapshot.json")
        val source = File(root, "snapshot.json.new")
        val bytes = """[{"id":"p","name":"Keep"}]""".toByteArray()
        source.writeBytes(bytes)
        try { LocalBackup.createSnapshot(source, destination); fail("Aliases must fail before writing") } catch (_: IOException) { }
        assertArrayEquals(bytes, source.readBytes())
        assertFalse(destination.exists())
    }
    @Test fun restoreRejectsSnapshotAliasingTargetSidecar() = fixture { root ->
        val target = File(root, "target.json")
        val candidate = File(root, "incoming.json")
        val original = """[{"id":"old","name":"Keep"}]""".toByteArray()
        val fresh = """[{"id":"new","name":"Incoming"}]""".toByteArray()
        target.writeBytes(original); candidate.writeBytes(fresh)
        val snapshot = File(target.path + ".new")
        try {
            RestoreTransaction.replace(target, candidate, snapshot, true, RestoreTransaction.hash(original), RestoreTransaction.hash(fresh))
            fail("Aliases must fail before snapshot or replacement")
        } catch (_: IOException) { }
        assertArrayEquals(original, target.readBytes()); assertArrayEquals(fresh, candidate.readBytes()); assertFalse(snapshot.exists())
    }
}
