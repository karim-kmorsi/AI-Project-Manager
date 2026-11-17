package com.karim.aiprojectmanager

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StartupStateTest {
    @Test fun readyStartupCannotDismissBeforeThreeDisplayedSeconds() {
        val clock = StartupClock()
        clock.setResumed(true)
        clock.onFrame(10_000_000_000L)
        clock.onFrame(12_999_999_999L)
        assertFalse(clock.canDismiss(ready = true))
        clock.onFrame(13_000_000_000L)
        assertTrue(clock.canDismiss(ready = true))
        assertFalse(clock.canDismiss(ready = false))
    }

    @Test fun pausesDoNotCountAndSavedVisibleTimeSurvivesRecreation() {
        val clock = StartupClock()
        clock.setResumed(true)
        clock.onFrame(0)
        clock.onFrame(1_000_000_000L)
        clock.setResumed(false)
        clock.onFrame(90_000_000_000L)
        clock.setResumed(true)
        clock.onFrame(100_000_000_000L)
        assertEquals(1_000_000_000L, clock.visibleNanos)
        val recreated = StartupClock(clock.visibleNanos)
        recreated.setResumed(true)
        recreated.onFrame(101_000_000_000L)
        recreated.onFrame(103_000_000_000L)
        assertTrue(recreated.canDismiss(ready = true))
    }

    @Test fun successfulInitializationIsNotReadAgain() = runBlocking {
        var reads = 0
        val projects = listOf(ProjectItem("p", "Keep", "", tasks = listOf(TaskItem("t", "Keep", "VERIFIED"))))
        val loader = StartupLoader { reads++; projects }
        loader.load()
        loader.load()
        assertEquals(1, reads)
        assertEquals(projects, loader.state.value.projects)
        assertFalse(loader.state.value.loading)
        assertFalse(loader.state.value.failed)
    }

    @Test fun failedInitializationCanRetryWithoutRestartingTheMinimumTimer() = runBlocking {
        var reads = 0
        val loader = StartupLoader {
            if (++reads == 1) throw java.io.IOException("Synthetic unavailable storage")
            emptyList()
        }
        val clock = StartupClock(StartupClock.MINIMUM_NANOS)
        loader.load()
        assertTrue(loader.state.value.failed)
        assertFalse(clock.canDismiss(loader.state.value.projects != null))
        loader.load()
        assertEquals(2, reads)
        assertFalse(loader.state.value.failed)
        assertTrue(clock.canDismiss(loader.state.value.projects != null))
    }

    @Test fun concurrentRequestsDoNotDuplicateInitialization() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var reads = 0
        val loader = StartupLoader {
            reads++
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            emptyList()
        }
        val first = async { loader.load() }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            loader.load()
            assertTrue(loader.state.value.loading)
            assertEquals(1, reads)
        } finally { release.countDown() }
        first.await()
        assertNotNull(loader.state.value.projects)
    }
}
