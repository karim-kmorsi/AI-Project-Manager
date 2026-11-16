package com.karim.aiprojectmanager

/** Read-only title AND status filtering; null status includes every stored status string. */
internal fun filterTasksByQuery(tasks: List<TaskItem>, query: String, status: String? = null): List<TaskItem> {
    val term = query.trim()
    if (term.isEmpty() && status == null) return tasks
    return tasks.filter {
        (term.isEmpty() || it.title.contains(term, ignoreCase = true)) &&
            (status == null || it.status == status)
    }
}
