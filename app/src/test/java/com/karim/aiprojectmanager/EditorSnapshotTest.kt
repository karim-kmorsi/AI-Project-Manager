package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.runtime.saveable.SaverScope

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
    @Test fun saverRoundTripRetainsStaleBaseAndDoesNotAliasSavedOrRestoredLists() {
        val scope = SaverScope { true }
        val original = editorSnapshot(project)
        val saved = with(EditorSnapshot.saver) { scope.save(original) }!!
        val restored = EditorSnapshot.saver.restore(saved)!!
        saved.clear()
        assertEquals(original, restored)
        assertEquals(original.hashCode(), restored.hashCode())
        assertNotEquals(editorSnapshot(project.copy(name = "Newer saved name")), restored)
        val savedAgain = with(EditorSnapshot.saver) { scope.save(restored) }!!
        assertTrue(savedAgain.isNotEmpty())
    }

    @Test fun saverAcceptsPreviousArrayListRepresentationWithMixedLanguageText() {
        val fields = arrayListOf("p", "اسم English", "\nDescription", "Blueprint", "Roadmap", "1", "t", "عنوان", "FUTURE")
        val restored = EditorSnapshot.saver.restore(fields)!!
        fields[1] = "Changed after restoration"
        assertEquals(editorSnapshot(ProjectItem("p", "اسم English", "\nDescription", "Blueprint", "Roadmap",
            listOf(TaskItem("t", "عنوان", "FUTURE")))), restored)
    }

}
