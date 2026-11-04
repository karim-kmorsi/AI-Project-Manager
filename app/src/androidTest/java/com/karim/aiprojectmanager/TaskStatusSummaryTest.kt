package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskStatusSummaryTest {
    @Test fun emptyProjectShowsZeroForEveryStatus() {
        val summary = taskStatusSummary(emptyList())
        assertTrue(summary.contains("لم تبدأ 0"))
        assertTrue(summary.contains("قيد التنفيذ 0"))
        assertTrue(summary.contains("منفذة دون تحقق 0"))
        assertTrue(summary.contains("موسومة كمتحقق منها 0"))
    }

    @Test fun countsCurrentStatusesWithoutChangingTasks() {
        val tasks = listOf(
            TaskItem("1", "First"),
            TaskItem("2", "Second", "IN_PROGRESS"),
            TaskItem("3", "Third", "IMPLEMENTED_UNVERIFIED"),
            TaskItem("4", "Fourth", "VERIFIED"),
            TaskItem("5", "Fifth", "IN_PROGRESS")
        )
        val original = tasks.toList()
        val summary = taskStatusSummary(tasks)
        assertTrue(summary.contains("لم تبدأ 1"))
        assertTrue(summary.contains("قيد التنفيذ 2"))
        assertTrue(summary.contains("منفذة دون تحقق 1"))
        assertTrue(summary.contains("موسومة كمتحقق منها 1"))
        assertTrue(tasks == original)
    }

    @Test fun unknownStatusIsReportedNotSilentlyDiscarded() {
        val summary = taskStatusSummary(listOf(TaskItem("1", "Legacy", "LEGACY_STATUS")))
        assertTrue(summary.contains("حالات أخرى 1"))
    }
}
