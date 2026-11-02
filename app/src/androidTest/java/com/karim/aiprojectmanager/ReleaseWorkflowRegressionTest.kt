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
}
