package com.karim.aiprojectmanager

import org.json.JSONArray
import java.io.File
import java.io.IOException

/**
 * Read-only restore preview. No write, merge, replace or import operation is exposed.
 * The existing projects.json schema remains unchanged.
 */
object RestorePreview {
    data class ProjectSummary(val id: String, val name: String, val taskCount: Int)
    data class Summary(
        val projects: List<ProjectSummary>,
        val totalTasks: Int,
        val sha256: String
    )

    fun inspect(file: File): Summary {
        if (!file.isFile || file.length() > 8L * 1024 * 1024) {
            throw IOException("Backup file is missing or too large")
        }
        return inspect(file.readBytes())
    }

    fun inspect(bytes: ByteArray): Summary {
        // Validate all project/task IDs and supported fields before showing any preview.
        val validated = LocalBackup.validate(bytes)
        val root = try { JSONArray(bytes.toString(Charsets.UTF_8)) } catch (e: Exception) {
            throw IOException("Invalid backup", e)
        }
        val projects = (0 until root.length()).map { index ->
            val project = root.getJSONObject(index)
            ProjectSummary(
                id = project.getString("id"),
                name = project.getString("name"),
                taskCount = project.optJSONArray("tasks")?.length() ?: 0
            )
        }
        return Summary(projects, validated.taskCount, validated.sha256)
    }
}
