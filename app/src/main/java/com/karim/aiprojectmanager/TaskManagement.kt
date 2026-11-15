package com.karim.aiprojectmanager

internal fun deletedTask(project: ProjectItem, taskId: String): ProjectItem {
    require(project.tasks.any { it.id == taskId }) { "Task does not belong to this project" }
    return project.copy(tasks = project.tasks.filterNot { it.id == taskId })
}
