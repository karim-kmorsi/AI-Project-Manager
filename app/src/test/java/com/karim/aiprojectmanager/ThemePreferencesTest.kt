package com.karim.aiprojectmanager

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThemePreferencesTest {
    private open class Store(var value: String? = null) : AppPreferences {
        var writes = 0
        var fail = false
        override fun readString(key: String, defaultValue: String): String {
            assertEquals("appearance_mode", key)
            return value ?: defaultValue
        }
        override fun writeString(key: String, value: String): Boolean {
            assertEquals("appearance_mode", key)
            writes++
            // Model SharedPreferences exposing the new in-memory value even if commit fails.
            this.value = value
            return !fail
        }
    }

    @Test fun stableSerializationAndUnsupportedValues() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.decode(it.storedValue)) }
        listOf(null, "", "light", "UNKNOWN", " DARK ").forEach { assertEquals(ThemeMode.SYSTEM, ThemeMode.decode(it)) }
    }

    @Test fun resolutionUsesSystemOnlyForSystemMode() {
        for (system in listOf(false, true)) {
            assertEquals(system, ThemeMode.SYSTEM.isDark(system))
            assertFalse(ThemeMode.LIGHT.isDark(system))
            assertTrue(ThemeMode.DARK.isDark(system))
        }
    }

    @Test fun missingAndInvalidValuesUseSystemWithoutRewriting() = runBlocking {
        for (value in listOf(null, "unsupported")) {
            val store = Store(value)
            val controller = ThemePreferences({ store })
            controller.load()
            assertEquals(ThemeMode.SYSTEM, controller.state.value.mode)
            assertFalse(controller.state.value.loading)
            assertEquals(0, store.writes)
            assertEquals(value, store.value)
        }
    }

    @Test fun savesAndReloadsExplicitChoice() = runBlocking {
        val store = Store()
        val controller = ThemePreferences({ store })
        controller.load()
        assertTrue(controller.select(ThemeMode.DARK))
        val next = ThemePreferences({ store })
        next.load()
        assertEquals(ThemeMode.DARK, next.state.value.mode)
        assertNull(controller.state.value.pendingMode)
        assertNull(controller.state.value.error)
        assertEquals(1, store.writes)
    }

    @Test fun failedCommitRetainsConfirmedModeAndRequestedChoiceUntilRetry() = runBlocking {
        val store = Store("LIGHT").apply { fail = true }
        val controller = ThemePreferences({ store })
        controller.load()
        assertFalse(controller.select(ThemeMode.DARK))
        assertEquals("DARK", store.value)
        assertEquals(ThemeMode.LIGHT, controller.state.value.mode)
        assertEquals(ThemeMode.DARK, controller.state.value.pendingMode)
        assertEquals(ThemePreferenceError.SAVE, controller.state.value.error)
        store.fail = false
        controller.retry()
        assertEquals(ThemeMode.DARK, controller.state.value.mode)
        assertNull(controller.state.value.error)
        assertEquals(2, store.writes)
    }

    @Test fun initializationFailureFallsBackWithoutWritesAndCanRetry() = runBlocking {
        val store = Store("DARK")
        var unavailable = true
        val controller = ThemePreferences({ if (unavailable) error("Cannot open preferences") else store })
        controller.load()
        assertEquals(ThemeMode.SYSTEM, controller.state.value.mode)
        assertEquals(ThemePreferenceError.LOAD, controller.state.value.error)
        assertFalse(controller.state.value.loading)
        unavailable = false
        controller.retry()
        assertEquals(ThemeMode.DARK, controller.state.value.mode)
        assertNull(controller.state.value.error)
        assertEquals(0, store.writes)
    }

    @Test fun writeOpeningExceptionPreservesRequestedSelectionForRetry() = runBlocking {
        val store = Store("LIGHT")
        var unavailable = false
        val controller = ThemePreferences({ if (unavailable) error("Cannot open preferences") else store })
        controller.load()
        unavailable = true
        assertFalse(controller.select(ThemeMode.DARK))
        assertEquals(ThemeMode.LIGHT, controller.state.value.mode)
        assertEquals(ThemeMode.DARK, controller.state.value.pendingMode)
        assertEquals(ThemePreferenceError.SAVE, controller.state.value.error)
        unavailable = false
        controller.retry()
        assertEquals(ThemeMode.DARK, controller.state.value.mode)
    }

    @Test fun repeatedConfirmedSelectionDoesNotWrite() = runBlocking {
        val store = Store("DARK")
        val controller = ThemePreferences({ store })
        controller.load()
        repeat(3) { assertTrue(controller.select(ThemeMode.DARK)) }
        assertEquals(0, store.writes)
    }

    @Test fun pendingWriteRunsOffCallerThreadAndRejectsConflictingRequests() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callerThread = Thread.currentThread().id
        var writeThread = callerThread
        val store = object : Store() {
            override fun writeString(key: String, value: String): Boolean {
                writeThread = Thread.currentThread().id
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return super.writeString(key, value)
            }
        }
        val controller = ThemePreferences({ store })
        controller.load()
        val job = async { controller.select(ThemeMode.DARK) }
        try {
            withContext(Dispatchers.Default) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            assertTrue(controller.state.value.saving)
            assertEquals(ThemeMode.SYSTEM, controller.state.value.mode)
            assertEquals(ThemeMode.DARK, controller.state.value.pendingMode)
            assertFalse(controller.select(ThemeMode.LIGHT))
            assertFalse(controller.select(ThemeMode.DARK))
            assertNotEquals(callerThread, writeThread)
        } finally { release.countDown() }
        assertTrue(job.await())
        assertEquals(1, store.writes)
        assertEquals(ThemeMode.DARK, controller.state.value.mode)
    }
}
