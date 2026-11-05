package com.karim.aiprojectmanager

/** Read-only title filtering, retaining original task instances and saved ordering. */
internal fun filterTasksByQuery(tasks: List<TaskItem>, query: String): List<TaskItem> {
    val term = query.trim()
    if (term.isEmpty()) return tasks
    return tasks.filter { it.title.contains(term, ignoreCase = true) }
}
