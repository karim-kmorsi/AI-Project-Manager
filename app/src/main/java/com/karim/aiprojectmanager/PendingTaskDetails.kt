package com.karim.aiprojectmanager

/**
 * Current, read-only task details; not an activity history or a verification result.
 * Unknown legacy statuses remain visible rather than being silently omitted.
 */
internal fun pendingTaskDetails(tasks: List<TaskItem>): List<String> =
    tasks.filter { it.status != "VERIFIED" }.map { task ->
        val label = when (task.status) {
            "NOT_STARTED" -> "لم تبدأ"
            "IN_PROGRESS" -> "قيد التنفيذ"
            "IMPLEMENTED_UNVERIFIED" -> "منفذة دون تحقق"
            else -> "حالة غير معروفة: ${task.status}"
        }
        "${task.title} — $label"
    }
