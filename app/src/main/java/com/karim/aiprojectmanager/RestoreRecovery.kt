package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.IOException

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
        authorized: Boolean
    ) = synchronized(ProjectWriteLock.monitor) {
        if (!authorized) throw IOException("Recovery requires explicit authorization")
        if (target.canonicalPath == safetySnapshot.canonicalPath) throw IOException("Recovery files overlap")
        if (!target.isFile || !safetySnapshot.isFile) throw IOException("Recovery input missing")
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
            stream.write(safety)
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
