package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** AtomicFile logs some publication failures instead of throwing. Verify before reporting Saved. */
internal fun writeVerifiedAtomic(
    file: File,
    bytes: ByteArray,
    publish: (AtomicFile, FileOutputStream) -> Unit = { atomic, stream -> atomic.finishWrite(stream) }
) {
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(bytes)
        stream.fd.sync()
        publish(atomic, stream)
    } catch (error: Exception) {
        atomic.failWrite(stream)
        throw error
    }
    // Do not use openRead here: it could remove the staged data after a failed rename.
    if (File(file.path + ".new").exists() || !file.readBytes().contentEquals(bytes)) {
        throw IOException("Atomic publication could not be verified; recovery data retained")
    }
}
