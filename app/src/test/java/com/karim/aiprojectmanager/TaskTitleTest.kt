package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class TaskTitleTest {
    private val first = TaskItem("t2", "Original", "FUTURE_STATUS")
    private val second = TaskItem("t1", "Verified", "VERIFIED")
    private val project = ProjectItem("p", "Name", "Description", "Blueprint\n", "Roadmap\n", listOf(first, second))

    @Test fun editingChangesOnlySelectedTitleAndPreservesStatusAndOrder() {
        val edited = editedTaskTitle(project, "t2", "  عنوان English  ")
        assertEquals(project.copy(tasks = listOf(first.copy(title = "عنوان English"), second)), edited)
        assertSame(second, edited.tasks[1])
        assertEquals(listOf(first, second), project.tasks)
    }

    @Test fun verifiedStatusIsRetainedAndProjectAssociationIsUnchanged() {
        val other = project.copy(id = "other")
        val edited = editedTaskTitle(project, "t1", "Changed")
        assertEquals("p", edited.id)
        assertEquals("VERIFIED", edited.tasks[1].status)
        assertEquals(second, other.tasks[1])
    }

    @Test fun blankTitlesAreRejected() {
        for (blank in listOf("", "   ", "\n\t", "\u2003")) {
            assertThrows(IllegalArgumentException::class.java) { editedTaskTitle(project, "t2", blank) }
        }
    }

    @Test fun missingTaskCannotCreateOrReassignATask() {
        assertThrows(IllegalArgumentException::class.java) { editedTaskTitle(project, "missing", "New") }
        assertEquals(listOf(first, second), project.tasks)
    }
}
