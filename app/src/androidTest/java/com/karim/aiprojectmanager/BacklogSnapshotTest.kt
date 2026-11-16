package com.karim.aiprojectmanager

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BacklogSnapshotTest {
    private fun fixture(): String = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open(BacklogSnapshot.ASSET).bufferedReader().use { it.readText() }

    @Test fun fixturePreservesImportedFields() {
        val snapshot = BacklogSnapshot.read(fixture().reader())
        assertEquals("ai-progress-demo", snapshot.id)
        assertEquals(2, snapshot.milestones.size)
        assertEquals(5, snapshot.tasks.size)
        assertEquals("TASK-1", snapshot.tasks[0].id)
        assertEquals("Done", snapshot.tasks[0].status)
        assertEquals("unverified", snapshot.tasks[0].verification)
        assertEquals("verified", snapshot.tasks[1].verification)
        assertEquals(listOf("TASK-3"), snapshot.tasks[3].dependencies)
        assertEquals("TASK-3", snapshot.nextAction.taskId)
    }

    @Test fun rejectsUnsupportedVersionAndMissingDependency() {
        val json = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            BacklogSnapshot.read(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2").reader())
        }
        assertThrows(IllegalArgumentException::class.java) {
            BacklogSnapshot.read(json.replace("\"TASK-3\"\n      ]", "\"MISSING\"\n      ]").reader())
        }
    }
}
