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
            RestoreTransaction.replace(target, incoming, snapshot, authorized = true, expectedTargetSha256 = RestoreTransaction.hash(old), expectedCandidateSha256 = RestoreTransaction.hash(fresh))
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
                RestoreTransaction.replace(target, incoming, snapshot, authorized = false, expectedTargetSha256 = RestoreTransaction.hash(old), expectedCandidateSha256 = RestoreTransaction.hash(incoming.readBytes()))
                fail("Explicit authorization is mandatory")
            } catch (_: IOException) {}
            assertFalse(snapshot.exists())
            incoming.writeText("corrupt")
            try {
                RestoreTransaction.replace(target, incoming, snapshot, authorized = true, expectedTargetSha256 = RestoreTransaction.hash(old), expectedCandidateSha256 = RestoreTransaction.hash(incoming.readBytes()))
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
                RestoreTransaction.replace(target, incoming, snapshot, authorized = true, expectedTargetSha256 = RestoreTransaction.hash(old), expectedCandidateSha256 = RestoreTransaction.hash(fresh), write = { output, bytes ->
                    output.write(bytes, 0, bytes.size / 2)
                    throw IOException("Simulated interrupted write")
                })
                fail("Write failure must propagate")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(old, snapshot.readBytes())
            assertArrayEquals(fresh, incoming.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun preexistingSafetyCopyBlocksRestoreWithoutChangingAnything() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val incoming = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            val fresh = """[{"id":"new","name":"Candidate"}]""".toByteArray()
            val previous = """[{"id":"previous","name":"Keep backup"}]""".toByteArray()
            target.writeBytes(old)
            incoming.writeBytes(fresh)
            snapshot.writeBytes(previous)
            try {
                RestoreTransaction.replace(target, incoming, snapshot, authorized = true, expectedTargetSha256 = RestoreTransaction.hash(old), expectedCandidateSha256 = RestoreTransaction.hash(fresh))
                fail("Existing snapshot must never be overwritten")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(previous, snapshot.readBytes())
            assertArrayEquals(fresh, incoming.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun postCommitVerificationFailureKeepsSafetyCopyForManualRecovery() {
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
                RestoreTransaction.replace(
                    target, incoming, snapshot, authorized = true,
                    expectedTargetSha256 = RestoreTransaction.hash(old),
                    expectedCandidateSha256 = RestoreTransaction.hash(fresh),
                    verifyCommitted = { _, _ -> false }
                )
                fail("Failed verification must be reported")
            } catch (_: IOException) {}
            assertArrayEquals(old, snapshot.readBytes())
            assertArrayEquals(fresh, incoming.readBytes())
            // A committed write must not be silently overwritten after verification fails.
            assertArrayEquals(fresh, target.readBytes())
        } finally { dir.deleteRecursively() }
    }


    @Test fun changedCandidateAfterPreviewIsRejectedBeforeSafetySnapshot() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Original"}]""".toByteArray()
            val reviewed = """[{"id":"new","name":"Reviewed"}]""".toByteArray()
            val changed = """[{"id":"new","name":"Modified"}]""".toByteArray()
            target.writeBytes(old)
            candidate.writeBytes(reviewed)
            val targetHash = RestoreTransaction.hash(old)
            val candidateHash = RestoreTransaction.hash(reviewed)
            candidate.writeBytes(changed)
            try {
                RestoreTransaction.replace(target, candidate, snapshot, true, targetHash, candidateHash)
                fail("Changed candidate must be rejected")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(changed, candidate.readBytes())
            assertFalse(snapshot.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun changedCurrentDataAfterPreviewIsRejected() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val reviewed = """[{"id":"old","name":"Reviewed"}]""".toByteArray()
            val newer = """[{"id":"old","name":"Unsaved update"}]""".toByteArray()
            val incoming = """[{"id":"new","name":"Incoming"}]""".toByteArray()
            target.writeBytes(reviewed)
            candidate.writeBytes(incoming)
            val reviewedHash = RestoreTransaction.hash(reviewed)
            target.writeBytes(newer)
            try {
                RestoreTransaction.replace(target, candidate, snapshot, true, reviewedHash, RestoreTransaction.hash(incoming))
                fail("Changed current data must be rejected")
            } catch (_: IOException) {}
            assertArrayEquals(newer, target.readBytes())
            assertFalse(snapshot.exists())
        } finally { dir.deleteRecursively() }
    }


    @Test fun candidateChangingAfterSnapshotIsRejectedWithoutTargetWrite() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            val incoming = """[{"id":"new","name":"Reviewed"}]""".toByteArray()
            val changed = """[{"id":"new","name":"Changed"}]""".toByteArray()
            target.writeBytes(old)
            candidate.writeBytes(incoming)
            try {
                RestoreTransaction.replace(
                    target, candidate, snapshot, true,
                    RestoreTransaction.hash(old), RestoreTransaction.hash(incoming),
                    afterSnapshot = { candidate.writeBytes(changed) }
                )
                fail("Changed candidate must be rejected before writing target")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(old, snapshot.readBytes())
            assertArrayEquals(changed, candidate.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun snapshotCreationFailureKeepsLiveAndCandidateIntact() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val pending = File(snapshot.path + ".new")
            val old = """[{"id":"old","name":"Existing"}]""".toByteArray()
            val incoming = """[{"id":"new","name":"Reviewed"}]""".toByteArray()
            val leftover = "incomplete previous snapshot".toByteArray()
            target.writeBytes(old)
            candidate.writeBytes(incoming)
            pending.writeBytes(leftover)
            try {
                RestoreTransaction.replace(
                    target, candidate, snapshot, true,
                    RestoreTransaction.hash(old), RestoreTransaction.hash(incoming)
                )
                fail("Pending incomplete safety snapshot must block restore")
            } catch (_: IOException) {}
            assertArrayEquals(old, target.readBytes())
            assertArrayEquals(incoming, candidate.readBytes())
            assertArrayEquals(leftover, pending.readBytes())
            assertFalse(snapshot.exists())
        } finally { dir.deleteRecursively() }
    }


    @Test fun currentProjectChangingAfterSnapshotBlocksReplacement() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val original = """[{"id":"p","name":"Before"}]""".toByteArray()
            val updated = """[{"id":"p","name":"Edited during restore"}]""".toByteArray()
            val incoming = """[{"id":"q","name":"Candidate"}]""".toByteArray()
            target.writeBytes(original)
            candidate.writeBytes(incoming)
            try {
                RestoreTransaction.replace(
                    target, candidate, snapshot, true,
                    RestoreTransaction.hash(original), RestoreTransaction.hash(incoming),
                    afterSnapshot = { target.writeBytes(updated) }
                )
                fail("Concurrent changes to current projects must block restore")
            } catch (_: IOException) {}
            assertArrayEquals(updated, target.readBytes())
            assertArrayEquals(original, snapshot.readBytes())
            assertArrayEquals(incoming, candidate.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedWriteRetainsIndependentRecoveryCopyWithoutPublishingPartialData() {
        val dir = root()
        try {
            val target = File(dir, "test-live.json")
            val candidate = File(dir, "candidate.json")
            val snapshot = File(dir, "safety.json")
            val original = """[{"id":"p","name":"Saved"}]""".toByteArray()
            val incoming = """[{"id":"q","name":"Replacement"}]""".toByteArray()
            target.writeBytes(original)
            candidate.writeBytes(incoming)
            try {
                RestoreTransaction.replace(
                    target, candidate, snapshot, true,
                    RestoreTransaction.hash(original), RestoreTransaction.hash(incoming),
                    write = { stream, bytes ->
                        stream.write(bytes, 0, bytes.size / 2)
                        throw IOException("Simulated write failure")
                    }
                )
                fail("Interrupted write cannot succeed")
            } catch (_: IOException) {}
            assertArrayEquals(original, android.util.AtomicFile(target).openRead().use { it.readBytes() })
            assertArrayEquals(original, snapshot.readBytes())
            assertEquals(RestoreTransaction.hash(original), RestoreTransaction.hash(snapshot.readBytes()))
            assertArrayEquals(incoming, candidate.readBytes())
        } finally { dir.deleteRecursively() }
    }

}
