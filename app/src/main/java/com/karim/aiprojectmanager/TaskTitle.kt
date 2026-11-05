package com.karim.aiprojectmanager

/** Edit one existing task in its project without changing identity, status or ordering. */
internal fun editedTaskTitle(project: ProjectItem, taskId: String, title: String): ProjectItem {
    require(title.isNotBlank()) { "Task title must not be blank" }
    require(project.tasks.any { it.id == taskId }) { "Task does not belong to this project" }
    return project.copy(tasks = project.tasks.map { task ->
        if (task.id == taskId) task.copy(title = title.trim()) else task
    })
}
