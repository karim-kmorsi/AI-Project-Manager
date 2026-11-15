package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Atomic legacy-array restore, invoked only after the user confirms full project replacement. */
internal object RestoreTransaction {
    private const val MISSING_TARGET = "missing"

    internal fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun fingerprint(file: File): String {
        if (!file.exists()) return MISSING_TARGET
        if (!file.isFile) throw IOException("Project storage is not a regular file")
        return hash(file.readBytes())
    }

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
        if (target.exists() && !target.isFile || !candidate.isFile) throw IOException("Invalid restore input")
        if (snapshot.exists() || File(snapshot.path + ".new").exists() || File(snapshot.path + ".bak").exists()) {
            throw IOException("Refusing to overwrite previous safety copy")
        }
        if (candidate.length() > 8L * 1024 * 1024 ||
            target.isFile && target.length() > 8L * 1024 * 1024) {
            throw IOException("Restore file exceeds 8 MiB limit")
        }
        val candidateBytes = candidate.readBytes()
        val originalBytes = target.takeIf { it.isFile }?.readBytes()
        LocalBackup.validate(candidateBytes)
        if (fingerprint(target) != expectedTargetSha256 || hash(candidateBytes) != expectedCandidateSha256) {
            throw IOException("Restore files changed since preview; no changes made")
        }
        if (originalBytes != null) {
            snapshot.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create recovery directory")
            }
            writeSafetySnapshot(snapshot, originalBytes)
            if (!snapshot.readBytes().contentEquals(originalBytes)) {
                throw IOException("Safety snapshot does not match original")
            }
        }
        afterSnapshot()
        if (fingerprint(target) != expectedTargetSha256 || hash(candidate.readBytes()) != expectedCandidateSha256) {
            throw IOException("Restore inputs changed before commit")
        }
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create project storage directory")
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

    private fun writeSafetySnapshot(snapshot: File, bytes: ByteArray) {
        if (snapshot.exists() || File(snapshot.path + ".new").exists() || File(snapshot.path + ".bak").exists()) {
            throw IOException("Refusing to overwrite previous safety copy")
        }
        val atomic = AtomicFile(snapshot)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }
}
