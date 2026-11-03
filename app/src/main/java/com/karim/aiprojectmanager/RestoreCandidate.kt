package com.karim.aiprojectmanager

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Read-only preparation of a potential restore via Android's document provider.
 * No method here can modify the live projects file.
 */
object RestoreCandidate {
    private const val MAX_BYTES = 8L * 1024 * 1024

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
}
