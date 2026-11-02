package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RestoreRecoveryTest {
    private fun isolatedRoot() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "isolated-recovery-${System.nanoTime()}"
    ).apply { mkdirs() }

    @Test fun explicitRecoveryRestoresVerifiedSafetyCopyAndKeepsIt() {
        val dir = isolatedRoot()
        try {
            val target = File(dir, "test-live.json")
            val safety = File(dir, "safety.json")
            val before = """[{"id":"old","name":"Original"}]""".toByteArray()
            val after = """[{"id":"new","name":"Committed"}]""".toByteArray()
            target.writeBytes(after)
            safety.writeBytes(before)
            RestoreRecovery.recover(
                target, safety, RestoreTransaction.hash(after),
                RestoreTransaction.hash(before), authorized = true
            )
            assertArrayEquals(before, target.readBytes())
            assertArrayEquals(before, safety.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancelledOrStaleRecoveryRefusesToOverwrite() {
        val dir = isolatedRoot()
        try {
            val target = File(dir, "test-live.json")
            val safety = File(dir, "safety.json")
            val before = """[{"id":"old","name":"Original"}]""".toByteArray()
            val after = """[{"id":"new","name":"Committed"}]""".toByteArray()
            target.writeBytes(after)
            safety.writeBytes(before)
            try {
                RestoreRecovery.recover(target, safety, RestoreTransaction.hash(after),
                    RestoreTransaction.hash(before), authorized = false)
                fail("Cancellation must stop recovery")
            } catch (_: IOException) {}
            val changed = """[{"id":"new","name":"Edited later"}]""".toByteArray()
            target.writeBytes(changed)
            try {
                RestoreRecovery.recover(target, safety, RestoreTransaction.hash(after),
                    RestoreTransaction.hash(before), authorized = true)
                fail("Stale recovery must be rejected")
            } catch (_: IOException) {}
            assertArrayEquals(changed, target.readBytes())
            assertArrayEquals(before, safety.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidSafetyCopyIsRejectedWithoutChangingTarget() {
        val dir = isolatedRoot()
        try {
            val target = File(dir, "test-live.json")
            val safety = File(dir, "safety.json")
            val after = """[{"id":"new","name":"Committed"}]""".toByteArray()
            target.writeBytes(after)
            safety.writeText("broken")
            try {
                RestoreRecovery.recover(target, safety, RestoreTransaction.hash(after),
                    RestoreTransaction.hash(safety.readBytes()), authorized = true)
                fail("Corrupt safety snapshot must not be restored")
            } catch (_: IOException) {}
            assertArrayEquals(after, target.readBytes())
        } finally { dir.deleteRecursively() }
    }
}
