package com.karim.aiprojectmanager

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

/** JVM measurements only: Java serialization is NOT Android Parcel/Bundle or a Binder budget. */
class SavedStatePayloadTest {
    private data class Case(val name: String, val units: Int, val tasks: Int, val pending: Int)

    private fun text(seed: String, units: Int) = seed.repeat((units + seed.length - 1) / seed.length).take(units)
    private fun fixture(units: Int, tasks: Int) = ProjectItem("p", "Name", "Description",
        text("Blueprint العربية 42\n", units),
        text("Roadmap English 123\n", units),
        (0 until tasks).map { TaskItem("task-$it", "Task $it " + "x".repeat(64), if (it % 2 == 0) "FUTURE" else "VERIFIED") })

    private fun legacy(project: ProjectItem) = arrayListOf(project.id, project.name, project.description,
        project.blueprint, project.roadmap, project.tasks.size.toString()).apply {
        project.tasks.forEach { add(it.id); add(it.title); add(it.status) }
    }
    private fun bytes(value: Any): Int = ByteArrayOutputStream().use { out ->
        ObjectOutputStream(out).use { it.writeObject(value) }
        out.size()
    }
    private fun current(project: ProjectItem) = with(EditorSnapshot.saver) { SaverScope { true }.save(editorSnapshot(project)) }!!

    @Test fun characterizeSmallLargeAndPendingSavedStateWithoutAndroidClaims() {
        for ((name, units, count, pendingUnits) in listOf(
            Case("small", 64, 3, 32), Case("large", 262_144, 2_000, 32),
            Case("large-pending", 262_144, 2_000, 131_072))) {
            val project = fixture(units, count)
            val old = legacy(project)
            val pending = text("Pending نص 42\n", pendingUnits)
            val saved = current(project)
            // Model only the baseline, pending form fields and relevant identity/confirmation flags.
            // Real Compose registries, Android envelopes and other screens are deliberately excluded.
            val state = arrayListOf<Any>(saved, pending, "pending description", "p", "task-0", true, "reload")
            println("SAVED_STATE_JVM case=$name documentUtf16UnitsEach=$units tasks=$count pendingUtf16Units=$pendingUnits " +
                "legacyStrings=${old.size} legacyRawUtf16Bytes=${old.sumOf { it.length.toLong() * 2 }} " +
                "legacyJavaBytes=${bytes(old)} currentJavaBytes=${bytes(saved)} modeledFormJavaBytes=${bytes(state)}")
            assertEquals(editorSnapshot(project), EditorSnapshot.saver.restore(saved))
            assertTrue(bytes(state) >= bytes(saved))
        }
        assertTrue(bytes(legacy(fixture(262_144, 2_000))) > bytes(legacy(fixture(64, 3))) * 100)
    }
}
