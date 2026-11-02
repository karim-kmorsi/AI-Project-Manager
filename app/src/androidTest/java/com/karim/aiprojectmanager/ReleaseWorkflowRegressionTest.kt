package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Release-gate tests use private temporary files, never actual projects.json. */
@RunWith(AndroidJUnit4::class)
class ReleaseWorkflowRegressionTest {
    private fun sandbox() = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "release-workflow-${System.nanoTime()}"
    ).apply { mkdirs() }

    @Test fun createEditTaskAndRestartPreserveAllProjectFields() {
        val dir = sandbox()
        try {
            val storage = ProjectStorage(File(dir, "test-projects.json"))
            val created = ProjectItem("p1", "Project", "First description")
            val first = persistProjectChange(storage, emptyList(), listOf(created))
            assertEquals(listOf(created), storage.load())
            val edited = created.copy(
                name = "Updated", description = "Changed",
                blueprint = "# Plan", roadmap = "Milestone 1",
                tasks = listOf(TaskItem("t1", "Check release", "IN_PROGRESS"))
            )
            persistProjectChange(storage, first, listOf(edited))
            val restartedStorage = ProjectStorage(File(dir, "test-projects.json"))
            assertEquals(listOf(edited), restartedStorage.load())
            assertEquals(1, LocalBackup.validate(File(dir, "test-projects.json").readBytes()).taskCount)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancelledPreviewAndExternalExportLeaveSavedProjectsUnchanged() {
        val dir = sandbox()
        try {
            val source = File(dir, "test-projects.json")
            val storage = ProjectStorage(source)
            storage.save(listOf(ProjectItem("p1", "Kept", "Data")))
            val original = source.readBytes()
            val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            assertNull(RestoreCandidate.inspectSelection(resolver, null))
            val selection = File(dir, "external.json")
            selection.writeBytes(original)
            val summary = RestoreCandidate.inspectSelection(resolver, android.net.Uri.fromFile(selection))
            assertEquals(1, summary?.projects?.size)
            val exported = File(dir, "export.json")
            JsonExport.exportToUri(resolver, android.net.Uri.fromFile(exported), JsonExport.readValidatedSource(source))
            assertArrayEquals(original, exported.readBytes())
            assertArrayEquals(original, source.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun verifiedTaskStatusPersistsAfterReloadAndCountsTowardProgress() {
        val dir = sandbox()
        try {
            val file = File(dir, "test-projects.json")
            val storage = ProjectStorage(file)
            val created = ProjectItem("p1", "Project", "Description",
                tasks = listOf(TaskItem("t1", "Implement", "IMPLEMENTED_UNVERIFIED"),
                    TaskItem("t2", "Review", "IN_PROGRESS")))
            storage.save(listOf(created))
            val verified = created.copy(tasks = created.tasks.map { task ->
                if (task.id == "t1") task.copy(status = "VERIFIED") else task
            })
            persistProjectChange(storage, listOf(created), listOf(verified))
            val reloaded = ProjectStorage(file).load().single()
            assertEquals("VERIFIED", reloaded.tasks.first { it.id == "t1" }.status)
            assertEquals("IN_PROGRESS", reloaded.tasks.first { it.id == "t2" }.status)
            assertEquals(1, reloaded.tasks.count { it.status == "VERIFIED" })
            assertEquals(2, reloaded.tasks.size)
        } finally { dir.deleteRecursively() }
    }

}
