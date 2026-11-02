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
}
