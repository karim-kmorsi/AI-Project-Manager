package com.karim.aiprojectmanager

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Read-only preparation of a potential restore via Android's document provider.
 * No method here can modify the live projects file.
 */
object RestoreCandidate {
    private const val MAX_BYTES = 8L * 1024 * 1024

    data class Staged(val file: File, val summary: RestorePreview.Summary, val targetFingerprint: String)

    /** A cancelled picker returns null and performs no file access. */
    fun inspectSelection(resolver: ContentResolver, selected: Uri?): RestorePreview.Summary? {
        if (selected == null) return null
        val stream = resolver.openInputStream(selected)
            ?: throw IOException("Cannot open selected backup")
        val bytes = stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size().toLong() + count > MAX_BYTES) {
                    throw IOException("Selected backup exceeds allowed size")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return RestorePreview.inspect(bytes)
    }

    fun stageSelection(resolver: ContentResolver, selected: Uri, destination: File): RestorePreview.Summary {
        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot create restore staging directory")
        }
        if (!destination.createNewFile()) throw IOException("Restore staging file already exists")
        try {
            val input = resolver.openInputStream(selected)
                ?: throw IOException("Cannot open selected backup")
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Selected backup exceeds allowed size")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            return RestorePreview.inspect(destination)
        } catch (error: Exception) {
            if (destination.exists() && !destination.delete()) {
                error.addSuppressed(IOException("Could not remove incomplete restore staging file"))
            }
            throw error
        }
    }
}
