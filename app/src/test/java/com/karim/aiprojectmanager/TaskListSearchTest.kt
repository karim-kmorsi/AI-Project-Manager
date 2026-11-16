package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class TaskListSearchTest {
    private val tasks = listOf(
        TaskItem("first", "Alpha task", "FUTURE_STATUS"),
        TaskItem("second", "Beta", "VERIFIED"),
        TaskItem("third", "alpha review", "NOT_STARTED"),
        TaskItem("fourth", "Alpha task", "IN_PROGRESS"),
        TaskItem("fifth", "مراجعة المهام", "IMPLEMENTED_UNVERIFIED")
    )

    @Test fun emptyAndWhitespaceQueriesReturnAllTasksUnchanged() {
        for (query in listOf("", "  ", "\n\t", "\u2003")) {
            assertSame(tasks, filterTasksByQuery(tasks, query))
        }
    }

    @Test fun titleSubstringMatchesIgnoreCaseAndOuterWhitespace() {
        assertEquals(listOf(tasks[0], tasks[2], tasks[3]), filterTasksByQuery(tasks, "  aLpHa \n"))
        assertEquals(listOf(tasks[2]), filterTasksByQuery(tasks, "REVIEW"))
    }

    @Test fun arabicTitleMatchesWithoutChangingUserText() {
        assertEquals(listOf(tasks[4]), filterTasksByQuery(tasks, "  مراجعة  "))
    }

    @Test fun matchingTasksKeepOrderIdentityStatusesAndDuplicateTitles() {
        val matches = filterTasksByQuery(tasks, "alpha")
        for ((match, index) in matches.zip(listOf(0, 2, 3))) assertSame(tasks[index], match)
        assertEquals(listOf("first", "third", "fourth"), matches.map { it.id })
        assertEquals(listOf("FUTURE_STATUS", "NOT_STARTED", "IN_PROGRESS"), matches.map { it.status })
    }

    @Test fun noMatchOrNonTitleMatchReturnsEmptyWithoutMutatingInput() {
        val original = tasks.toList()
        for (query in listOf("missing", "VERIFIED", "first")) {
            assertTrue(filterTasksByQuery(tasks, query).isEmpty())
        }
        assertEquals(original, tasks)
    }

    @Test fun emptyTaskListsRemainEmptyForAnyQuery() {
        assertTrue(filterTasksByQuery(emptyList(), "").isEmpty())
        assertTrue(filterTasksByQuery(emptyList(), "task").isEmpty())
    }
}
