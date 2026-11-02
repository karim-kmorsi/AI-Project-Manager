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

}
