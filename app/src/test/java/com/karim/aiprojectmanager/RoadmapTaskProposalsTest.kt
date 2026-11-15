package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapTaskProposalsTest {
    @Test fun parsesPhasesCheckboxesNumberedTasksAndActionableBulletsConservatively() {
        val analysis = analyzeRoadmapTasks(
            """
                # Roadmap
                ## Phase 1: Foundation
                - [ ] **Implement** local persistence
                - Cloud integrations
                - User account
                1. Add backup export
                ### Phase 2 - Delivery
                - [x] Already finished
                - Create release checklist
            """.trimIndent(),
            emptyList()
        )

        assertEquals(
            listOf("Implement local persistence", "Add backup export", "Create release checklist"),
            analysis.proposals.map { it.title }
        )
        assertEquals(listOf("Phase 1: Foundation", "Phase 1: Foundation", "Phase 2 - Delivery"),
            analysis.proposals.map { it.phase })
        assertEquals(2, analysis.ignoredAmbiguousEntries)
        assertEquals(1, analysis.ignoredCompletedEntries)
    }

    @Test fun existingAndRepeatedTitlesAreFlaggedWithoutChangingTasks() {
        val existing = listOf(TaskItem("saved", "Add export"))
        val analysis = analyzeRoadmapTasks(
            "- Add export\n- Add export\n- Build dashboard",
            existing
        )

        assertEquals(listOf("Add export", "Build dashboard"), analysis.proposals.map { it.title })
        assertTrue(analysis.proposals.first().alreadyExists)
        assertFalse(analysis.proposals.last().alreadyExists)
        assertEquals(1, analysis.duplicateEntries)
        assertEquals(listOf(TaskItem("saved", "Add export")), existing)
    }

    @Test fun editedProposalDuplicatesAreDetectedAgainstExistingAndSelectedTitles() {
        assertEquals(
            setOf(0, 1, 2),
            duplicateRoadmapTaskProposalIndexes(
                listOf("Existing task", " New task ", "new   task"),
                listOf("existing TASK")
            )
        )
        assertTrue(duplicateRoadmapTaskProposalIndexes(listOf(" "), emptyList()).contains(0))
    }

    @Test fun excessiveCandidatesReportTheNumberNotShown() {
        val roadmap = (1..205).joinToString("\n") { "- Implement feature $it" }
        val analysis = analyzeRoadmapTasks(roadmap, emptyList())
        assertEquals(200, analysis.proposals.size)
        assertEquals(5, analysis.notShownEntries)
    }

    @Test fun oversizedRoadmapIsRejectedWithoutTruncation() {
        val analysis = analyzeRoadmapTasks("x".repeat(1_000_001), emptyList())
        assertTrue(analysis.sourceTooLarge)
        assertTrue(analysis.proposals.isEmpty())
    }
}
