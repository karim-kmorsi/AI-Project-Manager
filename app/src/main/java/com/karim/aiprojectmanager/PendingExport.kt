package com.karim.aiprojectmanager

import java.io.File
import java.io.IOException
import java.util.UUID
import android.util.AtomicFile

/** Persist picker payload outside the activity/Bundle; no changes to the live JSON format. */
internal class PendingExport(private val directory: File) {
    private fun file(token: String): File {
        if (runCatching { UUID.fromString(token).toString() }.getOrNull() != token) throw IOException("Invalid export token")
        return File(directory, "$token.json")
    }
    fun stage(bytes: ByteArray): String {
        LocalBackup.validate(bytes)
        val token = UUID.randomUUID().toString()
        writeVerifiedAtomic(file(token), bytes)
        return token
    }
    fun read(token: String): ByteArray = JsonExport.readValidatedSource(file(token))
    fun discard(token: String) { AtomicFile(file(token)).delete() }
}
