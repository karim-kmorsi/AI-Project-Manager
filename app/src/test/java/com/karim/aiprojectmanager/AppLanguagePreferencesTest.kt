package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test

class AppLanguagePreferencesTest {
    private class Store(initial: String? = null) : AppPreferences {
        val values = mutableMapOf("unrelated" to "keep").apply {
            if (initial != null) put("language", initial)
        }
        var writes = 0
        var succeeds = true
        override fun readString(key: String, defaultValue: String): String {
            assertEquals("language", key)
            return values[key] ?: defaultValue
        }
        override fun writeString(key: String, value: String): Boolean {
            assertEquals("language", key)
            writes++
            // Model Android's in-memory update before a potentially failed disk commit.
            values[key] = value
            return succeeds
        }
    }

    @Test fun existingExplicitSelectionsLoadWithoutWrites() {
        listOf("ar" to AppLanguage.ARABIC, "en" to AppLanguage.ENGLISH).forEach { (raw, expected) ->
            val store = Store(raw)
            assertEquals(expected, AppLanguagePreferences(store).current())
            assertEquals(raw, store.values["language"])
            assertEquals(0, store.writes)
        }
    }

    @Test fun missingAndInvalidValuesFollowDeviceWithoutRewriting() {
        listOf(null, "", "unknown", "ARABIC").forEach { raw ->
            val store = Store(raw)
            val before = store.values.toMap()
            assertEquals(AppLanguage.SYSTEM, AppLanguagePreferences(store).current())
            assertEquals(before, store.values)
            assertEquals(0, store.writes)
        }
    }

    @Test fun everyChoicePersistsAndReloadsThroughANewController() {
        AppLanguage.entries.forEach { choice ->
            val store = Store()
            val preferences = AppLanguagePreferences(store)
            assertTrue(preferences.select(choice))
            assertEquals(choice.tag, store.values["language"])
            assertEquals(choice, preferences.current())
            assertEquals(choice, AppLanguagePreferences(store).current())
            assertEquals("keep", store.values["unrelated"])
            assertEquals(1, store.writes)
        }
    }

    @Test fun rejectedWriteRetainsConfirmedChoiceDespiteInMemoryChange() {
        val store = Store("en").apply { succeeds = false }
        val preferences = AppLanguagePreferences(store)
        assertFalse(preferences.select(AppLanguage.ARABIC))
        assertEquals(AppLanguage.ENGLISH, preferences.current())
        assertEquals("keep", store.values["unrelated"])
        store.succeeds = true
        assertTrue(preferences.select(AppLanguage.ARABIC))
        assertEquals(AppLanguage.ARABIC, preferences.current())
        assertEquals(2, store.writes)
    }
}
