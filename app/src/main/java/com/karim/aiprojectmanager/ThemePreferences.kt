package com.karim.aiprojectmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal enum class ThemePreferenceError { LOAD, SAVE }

internal data class ThemeSettingsState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val pendingMode: ThemeMode? = null,
    val error: ThemePreferenceError? = null
)

/** No project-storage dependency. The gate rejects conflicting requests instead of queuing them. */
internal class ThemePreferences(
    private val open: () -> AppPreferences,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    private val gate = Any()
    private val mutableState = MutableStateFlow(ThemeSettingsState())
    val state = mutableState.asStateFlow()
    private var loadInProgress = false
    private var loaded = false

    suspend fun load() {
        synchronized(gate) {
            if (loadInProgress || loaded || state.value.saving) return
            loadInProgress = true
            mutableState.value = state.value.copy(loading = true, error = null)
        }
        try {
            val mode = withContext(io) { ThemeMode.decode(open().readString(KEY, ThemeMode.SYSTEM.storedValue)) }
            synchronized(gate) {
                loaded = true
                mutableState.value = ThemeSettingsState(mode = mode, loading = false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = ThemeSettingsState(loading = false, error = ThemePreferenceError.LOAD)
        } finally {
            synchronized(gate) { loadInProgress = false }
        }
    }

    suspend fun select(mode: ThemeMode): Boolean {
        synchronized(gate) {
            if (state.value.loading || state.value.saving) return false
            if (state.value.mode == mode && state.value.error == null) return true
            mutableState.value = state.value.copy(saving = true, pendingMode = mode, error = null)
        }
        try {
            val saved = withContext(io) { open().writeString(KEY, mode.storedValue) }
            synchronized(gate) {
                if (saved) loaded = true
                mutableState.value = state.value.copy(
                    mode = if (saved) mode else state.value.mode,
                    saving = false,
                    pendingMode = if (saved) null else mode,
                    error = if (saved) null else ThemePreferenceError.SAVE
                )
            }
            return saved
        } catch (cancelled: CancellationException) {
            // A platform commit may already have started. Never claim success on cancellation.
            mutableState.value = state.value.copy(saving = false, error = ThemePreferenceError.SAVE)
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = state.value.copy(saving = false, error = ThemePreferenceError.SAVE)
            return false
        }
    }

    suspend fun retry() {
        val pending = state.value.pendingMode
        if (pending != null) select(pending) else load()
    }

    companion object { const val KEY = "appearance_mode" }
}
