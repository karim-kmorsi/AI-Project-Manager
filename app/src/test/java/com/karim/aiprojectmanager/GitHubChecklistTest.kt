package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubChecklistTest {
    @Test fun parsesTasksAndIgnoresFencedExamples() {
        val markdown = "- [x] Built\n  - [ ] Test\n1. [X] Reviewed\n~~~\n- [x] Example\n~~~"
        val result = parseGitHubChecklist(markdown)
        assertEquals(3, result.entries.size)
        assertEquals(2, result.checkedCount)
    }
    @Test fun checkedRemoteTaskIsNotVerified() {
        val additions = proposeGitHubChecklistTasks(emptyList(), parseGitHubChecklist("- [x] Built\n- [ ] Test"))
        assertEquals("IMPLEMENTED_UNVERIFIED", additions[0].status)
        assertEquals("NOT_STARTED", additions[1].status)
    }
    @Test fun manualTasksAreNotOverwritten() {
        val manual = listOf(TaskItem("local", "Built", "VERIFIED"))
        val additions = proposeGitHubChecklistTasks(manual, parseGitHubChecklist("- [ ] Built\n- [ ] Test"))
        assertEquals(1, additions.size)
        assertEquals("Test", additions.single().title)
        assertEquals("VERIFIED", manual.single().status)
    }
    @Test fun emptyDocumentDoesNotInventProgress() {
        assertTrue(parseGitHubChecklist("# No tasks").entries.isEmpty())
    }
}
