package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.FileOutputStream

/**
 * Explicit, isolated recovery from a retained safety snapshot.
 * Deliberately not connected to UI or device storage.
 */
internal object RestoreRecovery {
    fun recover(
        target: File,
        safetySnapshot: File,
        expectedFailedTargetSha256: String,
        expectedSafetySha256: String,
        authorized: Boolean,
        write: (FileOutputStream, ByteArray) -> Unit = { stream, bytes -> stream.write(bytes) }
    ) = synchronized(ProjectWriteLock.monitor) {
        if (!authorized) throw IOException("Recovery requires explicit authorization")
        val targetPath = target.canonicalPath
        val safetyPath = safetySnapshot.canonicalPath
        if (targetPath == safetyPath || targetPath == safetyPath + ".new" ||
            targetPath == safetyPath + ".bak" || safetyPath == targetPath + ".new" ||
            safetyPath == targetPath + ".bak") {
            throw IOException("Recovery files overlap with atomic storage sidecars")
        }
        if (File(safetySnapshot.path + ".new").exists() || File(safetySnapshot.path + ".bak").exists()) {
            throw IOException("Safety snapshot has pending atomic writes")
        }
        if (!target.isFile || !safetySnapshot.isFile) throw IOException("Recovery input missing")
        if (target.length() > 8L * 1024 * 1024 || safetySnapshot.length() > 8L * 1024 * 1024) {
            throw IOException("Recovery file exceeds 8 MiB limit")
        }
        val current = target.readBytes()
        val safety = safetySnapshot.readBytes()
        LocalBackup.validate(current)
        LocalBackup.validate(safety)
        if (RestoreTransaction.hash(current) != expectedFailedTargetSha256 ||
            RestoreTransaction.hash(safety) != expectedSafetySha256) {
            throw IOException("Recovery data changed; refusing overwrite")
        }
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            write(stream, safety)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        if (!AtomicFile(target).openRead().use { it.readBytes().contentEquals(safety) }) {
            throw IOException("Recovery verification failed; safety snapshot retained")
        }
        // Never remove the independent recovery copy automatically.
    }
}
