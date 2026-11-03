package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DraftStorageTest {
    @Test fun explicitPreservationKeepsEveryUnreadableDraftByteAndAllowsFreshDrafts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-preserve-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            for (document in listOf("blueprint", "roadmap")) {
                store.save("p", document, "A", "B")
                val base = root.listFiles()!!.single { it.isFile }
                val bytes = byteArrayOf(0xff.toByte(), 0, 123, 98, 97, 100)
                base.writeBytes(bytes)
                val backup = File(base.path + ".bak").apply { writeBytes(bytes + 1.toByte()) }
                val pending = File(base.path + ".new").apply { writeBytes(bytes + 2.toByte()) }
                val originals = listOf(base, backup, pending).associate { it.name to it.readBytes() }
                try { store.load("p", document); fail("Expected unreadable draft") } catch (_: IOException) { }
                originals.forEach { (name, expected) -> assertArrayEquals(expected, File(root, name).readBytes()) }
                val preserved = store.preserveUnreadable("p", document)
                assertEquals(originals.keys, preserved.list()!!.toSet())
                originals.forEach { (name, expected) -> assertArrayEquals(expected, File(preserved, name).readBytes()) }
                assertNull(store.load("p", document))
                store.save("p", document, "A", "C")
                assertEquals(DraftStorage.Draft("C", "A"), store.load("p", document))
                store.discard("p", document)
                originals.forEach { (name, expected) -> assertArrayEquals(expected, File(preserved, name).readBytes()) }
            }
            assertEquals(2, File(root, "preserved").listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun preservationRejectsReadableDraftAndLeavesItUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-preserve-valid-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("p", "blueprint", "A", "Legitimate B")
            val base = root.listFiles()!!.single()
            val bytes = base.readBytes()
            try { store.preserveUnreadable("p", "blueprint"); fail("Must retain readable draft") } catch (_: IOException) { }
            assertArrayEquals(bytes, base.readBytes())
            assertEquals(DraftStorage.Draft("Legitimate B", "A"), store.load("p", "blueprint"))
            assertFalse(File(root, "preserved").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun validBackupCannotSilentlyReplaceCorruptBaseBeforeExplicitPreservation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-preserve-backup-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("p", "roadmap", "A", "Valid backup")
            val base = root.listFiles()!!.single()
            val backup = File(base.path + ".bak")
            val validBytes = base.readBytes()
            backup.writeBytes(validBytes)
            val corruptBytes = "unreadable current draft".toByteArray()
            base.writeBytes(corruptBytes)
            try { store.load("p", "roadmap"); fail("Must preserve corrupt base") } catch (_: IOException) { }
            assertArrayEquals(corruptBytes, base.readBytes())
            assertArrayEquals(validBytes, backup.readBytes())
            val preserved = store.preserveUnreadable("p", "roadmap")
            assertArrayEquals(corruptBytes, File(preserved, base.name).readBytes())
            assertArrayEquals(validBytes, File(preserved, backup.name).readBytes())
            assertNull(store.load("p", "roadmap"))
        } finally { root.deleteRecursively() }
    }

    @Test fun unreadablePendingSidecarAloneStillBlocksWritesUntilPreserved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-preserve-pending-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("p", "blueprint", "A", "B")
            val base = root.listFiles()!!.single()
            val pending = File(base.path + ".new")
            val bytes = "interrupted unreadable draft".toByteArray()
            pending.writeBytes(bytes)
            assertTrue(base.delete())
            try { store.save("p", "blueprint", "A", "C"); fail("Must retain pending draft") } catch (_: IOException) { }
            assertArrayEquals(bytes, pending.readBytes())
            val preserved = store.preserveUnreadable("p", "blueprint")
            assertArrayEquals(bytes, File(preserved, pending.name).readBytes())
            store.save("p", "blueprint", "A", "C")
            assertEquals(DraftStorage.Draft("C", "A"), store.load("p", "blueprint"))
            assertArrayEquals(bytes, File(preserved, pending.name).readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun completeOrphanPendingWriteCanBeExplicitlyPreservedWithoutRestoringIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-preserve-orphan-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("p", "roadmap", "A", "Pending B")
            val base = root.listFiles()!!.single()
            val bytes = base.readBytes()
            val pending = File(base.path + ".new")
            pending.writeBytes(bytes)
            assertTrue(base.delete())
            try { store.load("p", "roadmap"); fail("No committed draft exists") } catch (_: IOException) { }
            assertArrayEquals(bytes, pending.readBytes())
            val preserved = store.preserveUnreadable("p", "roadmap")
            assertArrayEquals(bytes, File(preserved, pending.name).readBytes())
            assertNull(store.load("p", "roadmap"))
        } finally { root.deleteRecursively() }
    }

    @Test fun draftSurvivesStoreRecreationWithoutTouchingProjects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-test-${System.nanoTime()}")
        try {
            val store = DraftStorage(File(root, "drafts"))
            val projectFile = File(root, "projects.json")
            root.mkdirs()
            projectFile.writeText("[{\"id\":\"old-project\"}]")
            store.save("abc", "blueprint", "original", "unfinished changes")
            assertEquals("unfinished changes", DraftStorage(File(root, "drafts")).load("abc", "blueprint")?.text)
            assertEquals("original", store.load("abc", "blueprint")?.baseText)
            assertNull(store.load("other", "blueprint"))
            assertNull(store.load("abc", "roadmap"))
            assertEquals("[{\"id\":\"old-project\"}]", projectFile.readText())
            store.discard("abc", "blueprint")
            assertNull(store.load("abc", "blueprint"))
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptDraftCannotBeOverwritten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-corrupt-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("abc", "roadmap", "", "safe")
            val files = root.listFiles()!!
            assertEquals(1, files.size)
            files.single().writeText("broken payload")
            try {
                store.save("abc", "roadmap", "", "replacement")
                fail("Corruption should block writes")
            } catch (_: IOException) { }
            assertEquals("broken payload", files.single().readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun recoveryAfterSimulatedProcessRestartPreservesDraftAndProjectFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-restart-${System.nanoTime()}")
        try {
            root.mkdirs()
            val projectFile = File(root, "projects.json")
            val original = "[{\"id\":\"p1\",\"blueprint\":\"committed\"}]"
            projectFile.writeText(original)
            DraftStorage(File(root, "recovery-drafts"))
                .save("p1", "blueprint", "committed", "interrupted unsaved edit")
            // New instance simulates opening the app after its prior process disappears.
            val recovered = DraftStorage(File(root, "recovery-drafts"))
                .load("p1", "blueprint")
            assertNotNull(recovered)
            assertEquals("committed", recovered!!.baseText)
            assertEquals("interrupted unsaved edit", recovered.text)
            assertEquals(original, projectFile.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun newerCommittedContentMustNotMatchStaleDraftBase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "draft-conflict-${System.nanoTime()}")
        try {
            val store = DraftStorage(root)
            store.save("p1", "roadmap", "old committed text", "pending earlier changes")
            val saved = DraftStorage(root).load("p1", "roadmap")!!
            val newerCommitted = "newer independently saved roadmap"
            assertNotEquals(newerCommitted, saved.baseText)
            assertEquals("pending earlier changes", saved.text)
            // Conflict detection is read-only: neither side can be silently overwritten.
            assertEquals("pending earlier changes", store.load("p1", "roadmap")!!.text)
            assertNotEquals(newerCommitted, store.load("p1", "roadmap")!!.baseText)
        } finally { root.deleteRecursively() }
    }

    @Test fun staleSnapshotIsNotEligibleForAutosaveOrCleanup() {
        val earlier = "first edit"
        val latest = "first edit followed by new text"
        assertFalse(isLatestDraft(earlier, latest))
        assertTrue(isLatestDraft(latest, latest))
        // In particular a previously scheduled autosave cannot treat the
        // earlier snapshot as current after additional typing.
        assertFalse(isLatestDraft("pending recovery draft", "newer revision"))
    }


    @Test fun failedProjectWriteKeepsLastSavedProjectsIntact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "project-write-failure-${System.nanoTime()}")
        try {
            root.mkdirs()
            val projectFile = File(root, "projects.json")
            val storage = ProjectStorage(projectFile)
            val original = listOf(ProjectItem("p1", "Original", "safe", blueprint = "saved"))
            storage.save(original)
            val bytesBefore = projectFile.readBytes()
            // A broken on-disk payload must reject updates, never overwrite existing bytes.
            projectFile.writeText("{invalid json")
            val corruptBytes = projectFile.readBytes()
            try {
                persistProjectChange(
                    storage, original,
                    listOf(original.single().copy(blueprint = "should not persist"))
                )
                fail("Expected the corrupted project file to block all writes")
            } catch (_: IOException) { }
            assertArrayEquals(corruptBytes, projectFile.readBytes())
            assertFalse(projectFile.readBytes().contentEquals(bytesBefore))
        } finally { root.deleteRecursively() }
    }


    @Test fun legacyV01ProjectsSurviveRoundTripWithStatusesAndIds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "legacy-v01-${System.nanoTime()}")
        try {
            root.mkdirs()
            val file = File(root, "projects.json")
            file.writeText(
                """[
                  {"id":"legacy-project-1","name":"مشروع قديم","description":"Arabic and English",
                   "blueprint":"خطوة أولى\\nBlueprint line","roadmap":"مرحلة A",
                   "tasks":[
                     {"id":"t-verified","title":"مكتملة","status":"VERIFIED"},
                     {"id":"t-progress","title":"Working","status":"IN_PROGRESS"},
                     {"id":"t-unverified","title":"Code ready","status":"IMPLEMENTED_UNVERIFIED"}
                   ]},
                  {"id":"legacy-project-2","name":"Second","description":"",
                   "blueprint":"","roadmap":"Roadmap text",
                   "tasks":[{"id":"t-new","title":"Pending","status":"NOT_STARTED"}]}
                ]""".trimIndent()
            )
            val storage = ProjectStorage(file)
            val legacy = storage.load()
            assertEquals(2, legacy.size)
            assertEquals(listOf("legacy-project-1", "legacy-project-2"), legacy.map { it.id })
            assertEquals(listOf("t-verified", "t-progress", "t-unverified"), legacy.first().tasks.map { it.id })
            assertEquals(
                listOf("VERIFIED", "IN_PROGRESS", "IMPLEMENTED_UNVERIFIED"),
                legacy.first().tasks.map { it.status }
            )
            storage.save(legacy)
            val afterRestart = ProjectStorage(file).load()
            assertEquals(legacy, afterRestart)
            assertEquals("VERIFIED", afterRestart.first().tasks.first().status)
            assertEquals("Roadmap text", afterRestart[1].roadmap)
        } finally { root.deleteRecursively() }
    }

    @Test fun legacyV01MissingOptionalFieldsKeepDefaultsOnRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "legacy-v01-defaults-${System.nanoTime()}")
        try {
            root.mkdirs()
            val file = File(root, "projects.json")
            file.writeText(
                """[{"id":"legacy-minimal","name":"Minimal",
                    "tasks":[{"id":"task-with-default","title":"Untouched"}]}]"""
            )
            val storage = ProjectStorage(file)
            val before = storage.load()
            assertEquals("", before.single().description)
            assertEquals("", before.single().blueprint)
            assertEquals("", before.single().roadmap)
            assertEquals("NOT_STARTED", before.single().tasks.single().status)
            storage.save(before)
            assertEquals(before, ProjectStorage(file).load())
        } finally { root.deleteRecursively() }
    }

}
