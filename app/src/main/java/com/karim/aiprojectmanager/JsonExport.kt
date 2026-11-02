package com.karim.aiprojectmanager

import android.content.ContentResolver
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Export only. Never writes to the live project file or activates restore. */
object JsonExport {
    private const val MAX_BYTES = 8L * 1024 * 1024

    fun readValidatedSource(source: File): ByteArray {
        val atomic = AtomicFile(source)
        if (!source.exists() && !File(source.path + ".bak").exists()) {
            throw IOException("No saved project data to export")
        }
        val bytes = atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > MAX_BYTES) throw IOException("Export too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        LocalBackup.validate(bytes)
        return bytes
    }

    /** Does not close caller-owned output stream. Failure is reported, not treated as success. */
    internal fun copyValidated(bytes: ByteArray, output: OutputStream) {
        LocalBackup.validate(bytes)
        output.write(bytes)
        output.flush()
    }

    fun exportToUri(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        LocalBackup.validate(bytes)
        val stream = resolver.openOutputStream(uri, "w")
            ?: throw IOException("Cannot open selected export destination")
        stream.use { copyValidated(bytes, it) }
    }
}
