package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class ProjectMetadataTest {
    @Test fun metadataEditPreservesIdentityDocumentsTasksAndOtherProject() {
        val tasks = listOf(TaskItem("t2", "الثانية", "FUTURE_STATUS"), TaskItem("t1", "First", "VERIFIED"))
        val original = ProjectItem("stable-id", "Old", "Old description", " Blueprint\n", "Roadmap\nالعربية", tasks)
        val other = ProjectItem("other", "Untouched", "Keep")
        val projects = listOf(original, other)
        val edited = editedProjectMetadata(original, "  عنوان English  ", "  وصف\nDescription  ")
        assertEquals("عنوان English", edited.name)
        assertEquals("  وصف\nDescription  ", edited.description)
        assertEquals(original, edited.copy(name = original.name, description = original.description))
        assertSame(tasks, edited.tasks)
        assertEquals(listOf(original, other), projects)
    }

    @Test fun blankNamesAreRejectedBeforeProducingChanges() {
        val original = ProjectItem("p", "Keep", "Description")
        for (blank in listOf("", "   ", "\n\t", "\u2003")) {
            assertThrows(IllegalArgumentException::class.java) { editedProjectMetadata(original, blank, "Changed") }
        }
        assertEquals("Keep", original.name)
    }

    @Test fun descriptionCanBeClearedWithoutChangingOtherFields() {
        val original = ProjectItem("p", "Name", "Description", tasks = listOf(TaskItem("t", "Task")))
        assertEquals(original.copy(description = ""), editedProjectMetadata(original, "Name", ""))
    }
}
