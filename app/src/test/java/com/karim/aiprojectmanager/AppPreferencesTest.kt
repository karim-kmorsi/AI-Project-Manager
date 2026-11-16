package com.karim.aiprojectmanager

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class AppPreferencesTest {
    private class Store {
        val values = mutableMapOf<String, Any>()
        var failRead = false
        var failWrite = false
        var commitSucceeds = true
        var commits = 0
        private var pending: Pair<String, String>? = null
        private val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    if (failWrite) throw IllegalStateException("Unavailable")
                    pending = args!![0] as String to args[1] as String
                    proxy
                }
                "commit" -> {
                    commits++
                    if (commitSucceeds) pending?.let { values[it.first] = it.second }
                    commitSucceeds
                }
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        val platform = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> {
                    if (failRead) throw IllegalStateException("Unavailable")
                    val value = values[args!![0]]
                    if (value != null && value !is String) throw ClassCastException("Wrong type")
                    value ?: args[1]
                }
                "edit" -> editor
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as SharedPreferences
        val preferences = AndroidAppPreferences(platform)
    }

    @Test fun missingValueUsesDefaultWithoutWriting() {
        val store = Store()
        assertEquals("default", store.preferences.readString("fixture", "default"))
        assertTrue(store.values.isEmpty())
        assertEquals(0, store.commits)
    }

    @Test fun persistedUnicodeValueRoundTrips() {
        val store = Store()
        assertTrue(store.preferences.writeString("fixture", "العربية / English"))
        assertEquals("العربية / English", store.preferences.readString("fixture", "default"))
        assertEquals(1, store.commits)
    }

    @Test fun wrongStoredTypeUsesDefaultWithoutChangingIt() {
        val store = Store()
        store.values["fixture"] = 17
        assertEquals("default", store.preferences.readString("fixture", "default"))
        assertEquals(17, store.values["fixture"])
        assertEquals(0, store.commits)
    }

    @Test fun readFailureUsesDefault() {
        val store = Store().apply { failRead = true }
        assertEquals("default", store.preferences.readString("fixture", "default"))
    }

    @Test fun rejectedCommitDoesNotReportSuccess() {
        val store = Store().apply { commitSucceeds = false }
        assertFalse(store.preferences.writeString("fixture", "pending"))
        assertEquals(1, store.commits)
    }

    @Test fun writeExceptionDoesNotReportSuccess() {
        val store = Store().apply { failWrite = true }
        assertFalse(store.preferences.writeString("fixture", "pending"))
        assertEquals(0, store.commits)
    }

    @Test fun blankKeysAreRejectedBeforeStorageAccess() {
        val store = Store()
        assertThrows(IllegalArgumentException::class.java) { store.preferences.readString(" ", "default") }
        assertThrows(IllegalArgumentException::class.java) { store.preferences.writeString("", "value") }
        assertEquals(0, store.commits)
        assertTrue(store.values.isEmpty())
    }
}
