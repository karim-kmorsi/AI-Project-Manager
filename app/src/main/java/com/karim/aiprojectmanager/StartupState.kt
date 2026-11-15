package com.karim.aiprojectmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Counts displayed foreground frames, excluding pauses and gaps across recreation. */
internal class StartupClock(initialVisibleNanos: Long = 0L) {
    var visibleNanos: Long = initialVisibleNanos.coerceAtLeast(0L)
        private set
    private var resumed = false
    private var previousFrame: Long? = null

    fun setResumed(value: Boolean) {
        resumed = value
        previousFrame = null
    }

    fun onFrame(nanos: Long) {
        if (!resumed) return
        previousFrame?.let { previous ->
            visibleNanos += (nanos - previous).coerceAtLeast(0L)
        }
        previousFrame = nanos
    }

    fun canDismiss(ready: Boolean): Boolean = ready && visibleNanos >= MINIMUM_NANOS

    companion object { const val MINIMUM_NANOS = 3_000_000_000L }
}

internal data class StartupLoadState(
    val loading: Boolean = true,
    val projects: List<ProjectItem>? = null,
    val failed: Boolean = false
)

/** One read at a time; successful data is reused, while failures permit explicit retry. */
internal class StartupLoader(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val read: suspend () -> List<ProjectItem>
) {
    private val gate = Mutex()
    private val mutableState = MutableStateFlow(StartupLoadState())
    val state = mutableState.asStateFlow()

    suspend fun load() {
        if (!gate.tryLock()) return
        try {
            if (state.value.projects != null) return
            mutableState.value = StartupLoadState()
            val projects = withContext(io) { read().toList() }
            mutableState.value = StartupLoadState(loading = false, projects = projects)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = StartupLoadState(loading = false, failed = true)
        } finally {
            gate.unlock()
        }
    }
}
