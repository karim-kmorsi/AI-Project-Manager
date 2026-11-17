package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TaskManagementTest {
    @Test fun deletionRemovesOnlyTheSelectedTaskAndPreservesProjectAndRemainingTasks() {
        val first = TaskItem("first", "First", "VERIFIED")
        val second = TaskItem("second", "Second", "FUTURE_STATUS")
        val project = ProjectItem("project", "Name", "Description", "Blueprint", "Roadmap", listOf(first, second))

        assertEquals(project.copy(tasks = listOf(second)), deletedTask(project, "first"))
        assertEquals(listOf(first, second), project.tasks)
    }

    @Test fun missingTaskCannotChangeProject() {
        val project = ProjectItem("project", "Name", "Description", tasks = listOf(TaskItem("t", "Task")))
        assertThrows(IllegalArgumentException::class.java) { deletedTask(project, "missing") }
        assertEquals(listOf(TaskItem("t", "Task")), project.tasks)
    }
}
