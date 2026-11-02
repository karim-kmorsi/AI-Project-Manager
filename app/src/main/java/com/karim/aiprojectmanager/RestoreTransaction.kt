package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Isolated restore transaction engine. Not connected to MainActivity or real storage.
 * Requires explicit authorization from a future, separately reviewed caller.
 */
internal object RestoreTransaction {
    fun replace(
        target: File,
        candidate: File,
        snapshot: File,
        authorized: Boolean,
        write: (FileOutputStream, ByteArray) -> Unit = { output, bytes -> output.write(bytes) }
    ) {
        if (!authorized) throw IOException("Restore was not authorized")
        val paths = listOf(target, candidate, snapshot).map { it.canonicalPath }
        if (paths.distinct().size != 3) throw IOException("Restore paths must be independent")
        if (!target.isFile || !candidate.isFile) throw IOException("Missing restore input")
        if (snapshot.exists() || File(snapshot.path + ".new").exists() || File(snapshot.path + ".bak").exists()) {
            throw IOException("Refusing to overwrite previous safety copy")
        }
        val candidateBytes = candidate.readBytes()
        val originalBytes = target.readBytes()
        LocalBackup.validate(candidateBytes)
        LocalBackup.validate(originalBytes)
        // This immutable snapshot must exist and match the original before any target write.
        LocalBackup.createSnapshot(target, snapshot)
        if (!snapshot.readBytes().contentEquals(originalBytes)) {
            throw IOException("Safety snapshot does not match original")
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
        if (!target.readBytes().contentEquals(candidateBytes)) {
            throw IOException("Restore verification failed; recovery copy is retained")
        }
    }
}
