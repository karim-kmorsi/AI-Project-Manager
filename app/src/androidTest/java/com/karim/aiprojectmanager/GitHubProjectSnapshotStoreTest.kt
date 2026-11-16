package com.karim.aiprojectmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GitHubProjectSnapshotStoreTest {
    @Test fun reloadsSuccessfulBaselinesAndRejectsStaleOrCorruptWrites() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("github_project_snapshots", android.content.Context.MODE_PRIVATE)
        val repositoryId = (System.nanoTime() and Long.MAX_VALUE).coerceAtLeast(1)
        val snapshotKey = "github_project_snapshot_v1_$repositoryId"
        val attemptKey = "github_project_refresh_attempt_v1_$repositoryId"
        try {
            val store = GitHubProjectSnapshotStore(GitHubSnapshotPreferences.open(context))
            assertTrue(store.read(repositoryId) is GitHubSnapshotRead.Missing)
            assertTrue(store.save(repositoryId, null, history(repositoryId, 1_000)))
            assertTrue(store.markRefreshAttempt(repositoryId, 1_001))
            assertEquals(1_001L, GitHubProjectSnapshotStore(GitHubSnapshotPreferences.open(context))
                .lastRefreshAttemptAt(repositoryId))

            val baseline = (GitHubProjectSnapshotStore(GitHubSnapshotPreferences.open(context))
                .read(repositoryId) as GitHubSnapshotRead.Available).history
            assertTrue(store.save(repositoryId, baseline, history(repositoryId, 2_000, baseline.current)))
            assertFalse(store.save(repositoryId, null, history(repositoryId, 3_000)))
            val reloaded = (store.read(repositoryId) as GitHubSnapshotRead.Available).history
            assertEquals(2_000L, reloaded.current.refreshedAt)
            assertEquals(1_000L, reloaded.previous?.refreshedAt)

            preferences.edit().putString(snapshotKey, "{broken").commit()
            assertTrue(store.read(repositoryId) is GitHubSnapshotRead.Invalid)
            assertFalse(store.save(repositoryId, null, history(repositoryId, 4_000)))
            assertEquals("{broken", preferences.getString(snapshotKey, null))
        } finally {
            preferences.edit().remove(snapshotKey).remove(attemptKey).commit()
        }
    }

    private fun history(
        repositoryId: Long,
        refreshedAt: Long,
        previous: GitHubProjectSnapshot? = null
    ): GitHubSnapshotHistory {
        val repository = GitHubRepository(repositoryId, "owner/repo", "Description", "main", false, false)
        val snapshot = GitHubProjectSnapshot(
            repository,
            GitHubBranchPreview("main", false, "a".repeat(40), "b".repeat(40), "Commit", null),
            GitHubWorkflowSnapshot(0, null),
            GitHubIssuesSnapshot(0, 0, emptyList(), emptyList()),
            refreshedAt
        )
        return GitHubSnapshotHistory(previous, snapshot, if (previous == null) null else 1)
    }
}
