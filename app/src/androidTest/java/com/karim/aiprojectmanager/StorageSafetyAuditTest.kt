package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class StorageSafetyAuditTest {
    private fun fixture(block: (File, ProjectStorage) -> Unit) {
        val dir = Files.createTempDirectory("storage-audit").toFile()
        try { val file = File(dir, "projects.json"); block(file, ProjectStorage(file)) }
        finally { dir.deleteRecursively() }
    }
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected safe rejection") } catch (_: IOException) { }
    }
    @Test fun malformedFieldsAndDuplicateIdsBlockWritesWithoutChangingBytes() = fixture { file, storage ->
        for (payload in listOf(
            """[{"id":"p","name":"P","tasks":{}}]""",
            """[{"id":"p","name":"P","blueprint":12}]""",
            """[{"id":12,"name":"P"}]""",
            """[{"id":"p","name":"P"},{"id":"p","name":"Other"}]""",
            """[{"id":"p","name":"P","tasks":[{"id":"t","title":"T"},{"id":"t","title":"Other"}]}]"""
        )) {
            file.writeText(payload)
            rejects { storage.load() }
            rejects { storage.save(emptyList()) }
            assertEquals(payload, file.readText())
        }
    }
    @Test fun unknownFieldsSurviveKnownFieldEdits() = fixture { file, storage ->
        file.writeText("""[{"id":"p","name":"P","extension":{"value":"keep"},"tasks":[{"id":"t","title":"T","extra":[1,2]}]}]""")
        val original = storage.load()
        storage.save(original.map { it.copy(blueprint = "تعديل English", tasks = it.tasks.map { t -> t.copy(status = "VERIFIED") }) })
        val project = JSONArray(file.readText()).getJSONObject(0)
        assertEquals("keep", project.getJSONObject("extension").getString("value"))
        assertEquals("[1,2]", project.getJSONArray("tasks").getJSONObject(0).getJSONArray("extra").toString())
        assertEquals("تعديل English", storage.load().single().blueprint)
    }
    @Test fun staleUiSnapshotCannotOverwriteAnotherWriter() = fixture { _, storage ->
        val original = listOf(ProjectItem("p", "P", ""))
        storage.save(original)
        val newer = original.map { it.copy(roadmap = "Other writer") }
        storage.save(newer)
        rejects { persistProjectChange(storage, original, original.map { it.copy(blueprint = "Stale") }) }
        assertEquals(newer, storage.load())
    }
    @Test fun invalidOutgoingModelsCannotCreateUnreadableStorage() = fixture { file, storage ->
        rejects { storage.save(listOf(ProjectItem("p", "P", ""), ProjectItem("p", "Other", ""))) }
        assertFalse(file.exists())
    }
}
