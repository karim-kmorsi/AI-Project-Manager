package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RestoreTransactionTest {
    private fun root() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "isolated-restore-${System.nanoTime()}"
    ).apply { mkdirs() }

    @Test fun authorizedRestoreKeepsVerifiableSafetyCopy() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val incoming = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            val fresh = """[{"id":"new","name":"Incoming","tasks":[]}]""".toByteArray()
            target.writeBytes(old)
            incoming.writeBytes(fresh)
            RestoreTransaction.replace(target, incoming, snapshot, authorized = true)
            assertArrayEquals(fresh, target.readBytes())
            assertArrayEquals(old, snapshot.readBytes())
            assertArrayEquals(fresh, incoming.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectionAndMissingAuthorizationNeverModifySource() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val incoming = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            target.writeBytes(old)
            incoming.writeText("""[{"id":"new","name":"Candidate"}]""")
            try {
                RestoreTransaction.replace(target, incoming, snapshot, authorized = false)
                fail("Explicit authorization is mandatory")
            } catch (_: IOException) {}
            assertFalse(snapshot.exists())
            incoming.writeText("corrupt")
            try {
                RestoreTransaction.replace(target, incoming, snapshot, authorized = true)
                fail("Corrupt input must be rejected")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertFalse(snapshot.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun interruptedWriteRestoresOriginalAndRetainsSafetyCopy() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val incoming = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            val fresh = """[{"id":"new","name":"Candidate"}]""".toByteArray()
            target.writeBytes(old)
            incoming.writeBytes(fresh)
            try {
                RestoreTransaction.replace(target, incoming, snapshot, authorized = true) { output, bytes ->
                    output.write(bytes, 0, bytes.size / 2)
                    throw IOException("Simulated interrupted write")
                }
                fail("Write failure must propagate")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(old, snapshot.readBytes())
            assertArrayEquals(fresh, incoming.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
