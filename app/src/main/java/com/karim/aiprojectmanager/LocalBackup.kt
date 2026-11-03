package com.karim.aiprojectmanager

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import android.util.AtomicFile
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
        val taskCount = validateArray(root)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return Preview(root.length(), taskCount, digest)
    }

    private fun strictString(obj: JSONObject, field: String): String =
        obj.get(field) as? String ?: throw IOException("Invalid string field")

    /** Shared validation for storage and backups; never coerces malformed fields or identifiers. */
    internal fun validateArray(root: JSONArray): Int {
        val ids = HashSet<String>()
        var taskCount = 0
        try {
            for (i in 0 until root.length()) {
                val project = root.getJSONObject(i)
                val id = strictString(project, "id")
                val name = strictString(project, "name")
                if (id.isBlank() || name.isBlank() || !ids.add(id)) throw IOException("Invalid or duplicate project")
                for (field in listOf("description", "blueprint", "roadmap")) {
                    if (project.has(field) && project.get(field) !is String) throw IOException("Invalid project field")
                }
                val tasks = if (project.has("tasks")) project.getJSONArray("tasks") else JSONArray()
                val taskIds = HashSet<String>()
                for (j in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(j)
                    val taskId = strictString(task, "id")
                    strictString(task, "title")
                    if (taskId.isBlank() || !taskIds.add(taskId)) throw IOException("Invalid or duplicate task")
                    if (task.has("status") && task.get("status") !is String) throw IOException("Invalid task status")
                    taskCount++
                }
            }
        } catch (e: Exception) {
            throw IOException("Invalid project backup; no changes made", e)
        }
        return taskCount
    }

    /** Stage, verify and atomically publish a snapshot; never replace an existing backup. */
    fun createSnapshot(source: File, destination: File): Preview =
        createSnapshotWithWriter(source, destination) { stream, bytes -> stream.write(bytes) }

    // Writer injection is used only by isolated failure tests.
    internal fun createSnapshotWithWriter(
        source: File,
        destination: File,
        writer: (FileOutputStream, ByteArray) -> Unit
    ): Preview {
        requireIndependentAtomicFiles(source, destination)
        if (!source.isFile) {
            throw IOException("Source is missing or destination overlaps source")
        }
        val bytes = source.readBytes()
        val preview = validate(bytes)
        val pending = File(destination.path + ".new")
        val backup = File(destination.path + ".bak")
        if (destination.exists() || pending.exists() || backup.exists()) {
            throw IOException("Destination or pending backup already exists; refusing overwrite")
        }
        val atomic = AtomicFile(destination)
        val stream = atomic.startWrite()
        var committed = false
        try {
            writer(stream, bytes)
            stream.fd.sync()
            // AtomicFile stages into <destination>.new before commit.
            if (!pending.readBytes().contentEquals(bytes)) {
                throw IOException("Incomplete backup; refusing publication")
            }
            atomic.finishWrite(stream)
            committed = true
            if (!destination.readBytes().contentEquals(bytes)) {
                throw IOException("Committed snapshot verification failed")
            }
            return preview
        } catch (error: Exception) {
            if (!committed) atomic.failWrite(stream)
            throw error
        }
    }
}
