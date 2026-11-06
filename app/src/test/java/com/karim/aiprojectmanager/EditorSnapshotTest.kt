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

    @Test fun compactSaverIsBoundedAndDeterministicWithCanonicalGoldenVector() {
        val scope = SaverScope { true }
        val small = with(EditorSnapshot.saver) { scope.save(editorSnapshot(project)) }!!
        assertEquals(listOf("sha256-utf16-v1", "64be9f1ed2f0beee76e120f6d0a54de499c5cea9e3f902b9896fa6f96a56a75e"), small)
        val large = project.copy(blueprint = "x".repeat(262_144), roadmap = "خطة".repeat(100_000),
            tasks = (0 until 2_000).map { TaskItem("id-$it", "Long task $it", "FUTURE") })
        val saved = with(EditorSnapshot.saver) { scope.save(editorSnapshot(large)) }!!
        assertEquals(2, saved.size)
        assertEquals(small.sumOf { it.length }, saved.sumOf { it.length })
        assertEquals(editorSnapshot(large), EditorSnapshot.saver.restore(saved))
        assertEquals(saved, with(EditorSnapshot.saver) { scope.save(editorSnapshot(large)) })
    }

    @Test fun exactUtf16EmptyFieldsAndUnicodeNormalizationAreNotCollapsed() {
        for ((a, b) in listOf("\uD800" to "\uD801", "\uD800" to "?", "é" to "e\u0301", "" to "\u0000")) {
            assertNotEquals(editorSnapshot(project.copy(blueprint = a)), editorSnapshot(project.copy(blueprint = b)))
        }
        assertNotEquals(editorSnapshot(project.copy(name = "ab", description = "c")),
            editorSnapshot(project.copy(name = "a", description = "bc")))
    }

    @Test fun unsupportedOrMalformedRestoredTokensFailClosedInsteadOfAdoptingCurrentBase() {
        @Suppress("UNCHECKED_CAST")
        val nullLegacy = arrayListOf<Any?>("p", null, "", "", "", "0") as ArrayList<String>
        for (value in listOf(arrayListOf(), arrayListOf("sha256-utf16-v1", "invalid"),
            arrayListOf("future-format", "0".repeat(64)), nullLegacy)) {
            val restored = EditorSnapshot.saver.restore(value)
            assertNotNull(restored)
            assertNotEquals(editorSnapshot(project), restored)
            val saved = with(EditorSnapshot.saver) { SaverScope { true }.save(restored!!) }!!
            assertNotEquals(editorSnapshot(project), EditorSnapshot.saver.restore(saved))
        }
    }

    @Test fun legacyProjectIdResemblingFormatMarkerDoesNotChangeInterpretation() {
        val p = project.copy(id = "sha256-utf16-v1", tasks = emptyList())
        val legacy = arrayListOf(p.id, p.name, p.description, p.blueprint, p.roadmap, "0")
        assertEquals(editorSnapshot(p), EditorSnapshot.saver.restore(legacy))
    }

}
