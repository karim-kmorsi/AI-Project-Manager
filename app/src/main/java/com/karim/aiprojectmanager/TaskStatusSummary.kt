package com.karim.aiprojectmanager

/**
 * A snapshot of task statuses in the currently selected project.
 * No timestamps, activity history, or verification evidence are inferred.
 */
internal fun taskStatusSummary(tasks: List<TaskItem>): String {
    val notStarted = tasks.count { it.status == "NOT_STARTED" }
    val inProgress = tasks.count { it.status == "IN_PROGRESS" }
    val implemented = tasks.count { it.status == "IMPLEMENTED_UNVERIFIED" }
    val verified = tasks.count { it.status == "VERIFIED" }
    val other = tasks.size - notStarted - inProgress - implemented - verified
    return buildString {
        append("حالات المهام الحالية: لم تبدأ $notStarted | قيد التنفيذ $inProgress | منفذة دون تحقق $implemented | موسومة كمتحقق منها $verified")
        if (other > 0) append(" | حالات أخرى $other")
    }
}
