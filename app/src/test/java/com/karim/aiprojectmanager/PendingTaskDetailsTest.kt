package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTaskDetailsTest {
    @Test fun emptyAndFullyVerifiedProjectsHaveNoPendingDetails() {
        assertTrue(pendingTaskDetails(emptyList()).isEmpty())
        assertTrue(pendingTaskDetails(listOf(TaskItem("1", "Done", "VERIFIED"))).isEmpty())
    }

    @Test fun pendingTasksKeepTitlesStatusesAndOriginalOrder() {
        val tasks = listOf(
            TaskItem("1", "Plan"),
            TaskItem("2", "Build", "IN_PROGRESS"),
            TaskItem("3", "Review", "IMPLEMENTED_UNVERIFIED"),
            TaskItem("4", "Complete", "VERIFIED")
        )
        assertEquals(listOf(
            "Plan — لم تبدأ",
            "Build — قيد التنفيذ",
            "Review — منفذة دون تحقق"
        ), pendingTaskDetails(tasks))
        assertEquals("VERIFIED", tasks[3].status)
    }

    @Test fun unknownLegacyStatusRemainsVisible() {
        assertEquals(listOf("Legacy — حالة غير معروفة: CUSTOM"),
            pendingTaskDetails(listOf(TaskItem("1", "Legacy", "CUSTOM"))))
    }
}
