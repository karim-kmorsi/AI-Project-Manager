package com.karim.aiprojectmanager

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Offline backup foundation. Deliberately does not write to the live projects.json.
 * Backup payload is the exact legacy JSON array, not a new on-disk schema.
 */
object LocalBackup {
    private const val MAX_BYTES = 8L * 1024 * 1024

    data class Preview(val projectCount: Int, val taskCount: Int, val sha256: String)

    fun validate(bytes: ByteArray): Preview {
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_BYTES) throw IOException("Backup size is invalid")
        val content = bytes.toString(Charsets.UTF_8)
        val root = try { JSONArray(content) } catch (e: Exception) {
            throw IOException("Only legacy JSON project arrays are currently supported", e)
        }
        val ids = HashSet<String>()
        var taskCount = 0
        try {
            for (i in 0 until root.length()) {
                val project = root.getJSONObject(i)
                val id = project.getString("id")
                val name = project.getString("name")
                if (id.isBlank() || name.isBlank() || !ids.add(id)) throw IOException("Invalid or duplicate project")
                for (field in listOf("description", "blueprint", "roadmap")) {
                    if (project.has(field) && project.get(field) !is String) throw IOException("Invalid project field")
                }
                val tasks = if (project.has("tasks")) project.getJSONArray("tasks") else JSONArray()
                val taskIds = HashSet<String>()
                for (j in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(j)
                    val taskId = task.getString("id")
                    task.getString("title")
                    if (taskId.isBlank() || !taskIds.add(taskId)) throw IOException("Invalid or duplicate task")
                    if (task.has("status") && task.get("status") !is String) throw IOException("Invalid task status")
                    taskCount++
                }
            }
        } catch (e: Exception) {
            throw IOException("Invalid project backup; no changes made", e)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return Preview(root.length(), taskCount, digest)
    }

    /** Copy the exact source bytes, never a lossy parse-and-reserialize. */
    fun createSnapshot(source: File, destination: File): Preview {
        if (!source.isFile || source.canonicalFile == destination.canonicalFile) {
            throw IOException("Source is missing or destination overlaps source")
        }
        val bytes = source.readBytes()
        val preview = validate(bytes)
        if (destination.exists()) throw IOException("Refusing to overwrite an existing backup")
        destination.outputStream().use { output -> output.write(bytes); output.fdSyncIfPossible() }
        try {
            if (!destination.readBytes().contentEquals(bytes)) throw IOException("Backup verification failed")
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
        return preview
    }

    private fun java.io.OutputStream.fdSyncIfPossible() {
        if (this is java.io.FileOutputStream) fd.sync()
    }
}
