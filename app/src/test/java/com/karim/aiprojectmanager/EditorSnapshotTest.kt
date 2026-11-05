package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class EditorSnapshotTest {
    private val project = ProjectItem("p", "Name", "Description", "Blueprint", "Roadmap",
        listOf(TaskItem("a", "First", "FUTURE"), TaskItem("b", "Second", "VERIFIED")))

    @Test fun snapshotIsStableAndDoesNotAliasMutableTasks() {
        val tasks = project.tasks.toMutableList()
        val snapshot = editorSnapshot(project.copy(tasks = tasks))
        tasks.clear()
        assertEquals(editorSnapshot(project), snapshot)
        assertNotEquals(snapshot, editorSnapshot(project.copy(tasks = tasks)))
    }

    @Test fun everyEditableOrPreservedFieldAndTaskOrderChangesTheBase() {
        val changed = listOf(project.copy(id = "other"), project.copy(name = "Other"),
            project.copy(description = "Other"), project.copy(blueprint = "Other"), project.copy(roadmap = "Other"),
            project.copy(tasks = project.tasks.reversed()),
            project.copy(tasks = listOf(project.tasks[0].copy(id = "other"), project.tasks[1])),
            project.copy(tasks = listOf(project.tasks[0].copy(title = "Other"), project.tasks[1])),
            project.copy(tasks = listOf(project.tasks[0].copy(status = "VERIFIED"), project.tasks[1])))
        changed.forEach { assertNotEquals(editorSnapshot(project), editorSnapshot(it)) }
    }

    @Test fun delimitersAndNewlinesCannotMakeDifferentFieldsCompareEqual() {
        assertNotEquals(editorSnapshot(project.copy(name = "a, b", description = "c\n")),
            editorSnapshot(project.copy(name = "a", description = "b, c\n")))
    }
}
