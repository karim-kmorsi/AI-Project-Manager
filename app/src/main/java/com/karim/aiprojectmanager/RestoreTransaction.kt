package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Isolated restore transaction engine. Not connected to MainActivity or real storage.
 * Requires explicit authorization from a future, separately reviewed caller.
 */
internal object RestoreTransaction {
    internal fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    // Share the same mutex as ProjectStorage.save within this process.
    fun replace(
        target: File,
        candidate: File,
        snapshot: File,
        authorized: Boolean,
        expectedTargetSha256: String,
        expectedCandidateSha256: String,
        write: (FileOutputStream, ByteArray) -> Unit = { output, bytes -> output.write(bytes) },
        verifyCommitted: (File, ByteArray) -> Boolean = { file, expected -> file.readBytes().contentEquals(expected) },
        afterSnapshot: () -> Unit = {}
    ) = synchronized(ProjectWriteLock.monitor) {
        if (!authorized) throw IOException("Restore was not authorized")
        requireIndependentAtomicFiles(target, candidate, snapshot)
        for (input in listOf(target, candidate)) {
            if (File(input.path + ".new").exists() || File(input.path + ".bak").exists()) {
                throw IOException("Restore input has pending atomic writes")
            }
        }
        if (!target.isFile || !candidate.isFile) throw IOException("Missing restore input")
        if (snapshot.exists() || File(snapshot.path + ".new").exists() || File(snapshot.path + ".bak").exists()) {
            throw IOException("Refusing to overwrite previous safety copy")
        }
        if (candidate.length() > 8L * 1024 * 1024 || target.length() > 8L * 1024 * 1024) {
            throw IOException("Restore file exceeds 8 MiB limit")
        }
        val candidateBytes = candidate.readBytes()
        val originalBytes = target.readBytes()
        LocalBackup.validate(candidateBytes)
        LocalBackup.validate(originalBytes)
        if (hash(originalBytes) != expectedTargetSha256 || hash(candidateBytes) != expectedCandidateSha256) {
            throw IOException("Restore files changed since preview; no changes made")
        }
        // This immutable snapshot must exist and match the original before any target write.
        LocalBackup.createSnapshot(target, snapshot)
        if (!snapshot.readBytes().contentEquals(originalBytes)) {
            throw IOException("Safety snapshot does not match original")
        }
        afterSnapshot()
        if (hash(target.readBytes()) != expectedTargetSha256 || hash(candidate.readBytes()) != expectedCandidateSha256) {
            throw IOException("Restore inputs changed before commit")
        }
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try {
            write(output, candidateBytes)
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
        // Never automatically overwrite an existing target again on post-commit failure.
        if (!verifyCommitted(target, candidateBytes)) {
            throw IOException("Restore verification failed; recovery copy is retained")
        }
    }
}
