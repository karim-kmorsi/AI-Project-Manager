package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class TaskStatusFilterTest {
    private val tasks = listOf(
        TaskItem("a", "Alpha pending", "NOT_STARTED"),
        TaskItem("b", "Beta review", "IN_PROGRESS"),
        TaskItem("c", "Alpha review", "IN_PROGRESS"),
        TaskItem("d", "Alpha code", "IMPLEMENTED_UNVERIFIED"),
        TaskItem("e", "Alpha verified", "VERIFIED"),
        TaskItem("f", "Alpha unknown", "FUTURE_STATUS")
    )

    @Test fun allStatusesIncludesUnknownStatusesAndKeepsOriginalList() {
        assertSame(tasks, filterTasksByQuery(tasks, "  ", null))
        assertEquals(listOf(tasks[0], tasks[2], tasks[3], tasks[4], tasks[5]),
            filterTasksByQuery(tasks, "alpha", null))
    }

    @Test fun eachSupportedStatusSelectsOnlyItsStoredValue() {
        val expected = listOf(
            "NOT_STARTED" to listOf(tasks[0]),
            "IN_PROGRESS" to listOf(tasks[1], tasks[2]),
            "IMPLEMENTED_UNVERIFIED" to listOf(tasks[3]),
            "VERIFIED" to listOf(tasks[4])
        )
        for ((status, matches) in expected) {
            assertEquals(matches, filterTasksByQuery(tasks, "", status))
        }
    }

    @Test fun titleAndStatusMustBothMatchWithTrimmedCaseInsensitiveSearch() {
        assertEquals(listOf(tasks[2]), filterTasksByQuery(tasks, "  ALpHa \n", "IN_PROGRESS"))
        assertEquals(listOf(tasks[1], tasks[2]), filterTasksByQuery(tasks, " \t ", "IN_PROGRESS"))
        assertTrue(filterTasksByQuery(tasks, "Beta", "VERIFIED").isEmpty())
    }

    @Test fun missingTitlesStatusesAndEmptyInputsGiveEmptyResults() {
        assertTrue(filterTasksByQuery(tasks, "missing", "IN_PROGRESS").isEmpty())
        assertTrue(filterTasksByQuery(tasks, "", "ABSENT_STATUS").isEmpty())
        assertTrue(filterTasksByQuery(emptyList(), "", "NOT_STARTED").isEmpty())
        assertTrue(filterTasksByQuery(emptyList(), "", null).isEmpty())
    }

    @Test fun matchingTasksKeepSavedOrderAndIdentityWithoutMutatingInput() {
        val original = tasks.toList()
        val matches = filterTasksByQuery(tasks, "review", "IN_PROGRESS")
        assertEquals(listOf("b", "c"), matches.map { it.id })
        assertSame(tasks[1], matches[0])
        assertSame(tasks[2], matches[1])
        assertEquals(original, tasks)
    }

    @Test fun storedStatusMatchingIsExactAndDoesNotNormalizeUnknownValues() {
        assertTrue(filterTasksByQuery(tasks, "", "verified").isEmpty())
        assertEquals(listOf(tasks[5]), filterTasksByQuery(tasks, "alpha", "FUTURE_STATUS"))
        assertEquals("FUTURE_STATUS", tasks[5].status)
    }
}
