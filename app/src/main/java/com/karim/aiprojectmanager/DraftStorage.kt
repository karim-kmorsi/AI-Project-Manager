package com.karim.aiprojectmanager

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
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

    private fun parse(payload: String, projectId: String, document: String): Draft {
        val o = JSONObject(payload)
        if (o.getString("projectId") != projectId || o.getString("document") != document) {
            throw IOException("Draft identity mismatch")
        }
        return Draft(o.getString("text"), o.getString("baseText"))
    }

    fun load(projectId: String, document: String): Draft? {
        val file = fileFor(projectId, document)
        if (!file.exists() && !File(file.path + ".bak").exists() && !File(file.path + ".new").exists()) return null
        try {
            // Validate before AtomicFile recovery can replace a base or remove a pending sidecar.
            for (candidate in listOf(file, File(file.path + ".bak"), File(file.path + ".new")).filter { it.exists() }) {
                parse(candidate.readText(Charsets.UTF_8), projectId, document)
            }
            val payload = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(payload, projectId, document)
        } catch (e: Exception) {
            throw IOException("Draft could not be loaded; refusing to overwrite it", e)
        }
    }

    /** Explicit preservation only: no replacement, deletion, parsing recovery or automatic restore. */
    fun preserveUnreadable(projectId: String, document: String): File {
        val file = fileFor(projectId, document)
        val backup = File(file.path + ".bak")
        val sources = listOf(file, backup, File(file.path + ".new")).filter { it.exists() }
        val unreadable = sources.firstOrNull { candidate ->
            try {
                parse(candidate.readText(Charsets.UTF_8), projectId, document)
                false
            } catch (_: Exception) { true }
        } ?: if (!file.exists() && !backup.exists()) {
            // An orphan pending write cannot be opened by AtomicFile, even if its JSON is complete.
            sources.singleOrNull() ?: throw IOException("No draft to preserve")
        } else throw IOException("No unreadable draft to preserve; readable drafts are retained")
        val preservedRoot = File(directory, "preserved")
        if (!preservedRoot.isDirectory && !preservedRoot.mkdirs()) throw IOException("Cannot preserve draft")
        val destination = File(preservedRoot, UUID.randomUUID().toString())
        if (!destination.mkdir()) throw IOException("Cannot create unique preservation directory")
        // Keep the unreadable file in place until every other move succeeds, so partial failure
        // continues to block fresh writes. Files.move without REPLACE_EXISTING never overwrites.
        for (source in sources.filter { it != unreadable } + unreadable) {
            Files.move(source.toPath(), File(destination, source.name).toPath())
        }
        return destination
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
        writeVerifiedAtomic(file, payload.toByteArray(Charsets.UTF_8))
    }

    fun discard(projectId: String, document: String) {
        val file = fileFor(projectId, document)
        if (file.exists() && !file.delete()) throw IOException("Cannot delete completed draft")
        val backup = File(file.path + ".bak")
        if (backup.exists() && !backup.delete()) throw IOException("Cannot delete completed draft backup")
    }
}
