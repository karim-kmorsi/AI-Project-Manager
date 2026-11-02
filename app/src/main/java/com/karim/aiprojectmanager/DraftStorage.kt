package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import org.json.JSONObject

/**
 * Separate, atomic recovery drafts. Never writes to projects.json.
 * The expected base text prevents old drafts from silently replacing newer data.
 */
class DraftStorage(private val directory: File) {
    private fun fileFor(projectId: String, document: String): File {
        require(document == "blueprint" || document == "roadmap")
        require(projectId.isNotBlank())
        val safe = projectId.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(directory, "$safe-$document.json")
    }

    data class Draft(val text: String, val baseText: String)

    fun load(projectId: String, document: String): Draft? {
        val file = fileFor(projectId, document)
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        try {
            val payload = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(payload)
            if (o.getString("projectId") != projectId || o.getString("document") != document) {
                throw IOException("Draft identity mismatch")
            }
            return Draft(o.getString("text"), o.getString("baseText"))
        } catch (e: Exception) {
            throw IOException("Draft could not be loaded; refusing to overwrite it", e)
        }
    }

    fun save(projectId: String, document: String, baseText: String, text: String) {
        val file = fileFor(projectId, document)
        // A corrupt draft must never be overwritten.
        load(projectId, document)
        directory.mkdirs()
        val payload = JSONObject()
            .put("projectId", projectId)
            .put("document", document)
            .put("baseText", baseText)
            .put("text", text)
            .toString()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(payload.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }

    fun discard(projectId: String, document: String) {
        val file = fileFor(projectId, document)
        if (file.exists() && !file.delete()) throw IOException("Cannot delete completed draft")
        val backup = File(file.path + ".bak")
        if (backup.exists() && !backup.delete()) throw IOException("Cannot delete completed draft backup")
    }
}
