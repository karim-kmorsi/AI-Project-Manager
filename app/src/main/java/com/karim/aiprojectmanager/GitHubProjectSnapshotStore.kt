package com.karim.aiprojectmanager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal interface GitHubSnapshotPreferences {
    fun readString(key: String): String?
    fun writeString(key: String, value: String): Boolean

    companion object {
        fun open(context: Context): GitHubSnapshotPreferences = AndroidGitHubSnapshotPreferences(
            context.applicationContext.getSharedPreferences("github_project_snapshots", Context.MODE_PRIVATE)
        )
    }
}

private class AndroidGitHubSnapshotPreferences(private val preferences: SharedPreferences) :
    GitHubSnapshotPreferences {
    override fun readString(key: String): String? = preferences.getString(key, null)

    // The caller must know persistence completed before replacing the previous baseline.
    @Suppress("ApplySharedPref", "UseKtx")
    override fun writeString(key: String, value: String): Boolean = try {
        preferences.edit().putString(key, value).commit()
    } catch (_: RuntimeException) {
        false
    }
}

internal sealed interface GitHubSnapshotRead {
    data object Missing : GitHubSnapshotRead
    data class Available(val history: GitHubSnapshotHistory) : GitHubSnapshotRead
    data object Invalid : GitHubSnapshotRead
}

/** Stores only successful remote snapshots; project JSON remains untouched. */
internal class GitHubProjectSnapshotStore(private val preferences: GitHubSnapshotPreferences) {
    fun read(repositoryId: Long): GitHubSnapshotRead = synchronized(lock) {
        require(repositoryId > 0)
        val raw = try {
            preferences.readString(snapshotKey(repositoryId))
        } catch (_: RuntimeException) {
            return@synchronized GitHubSnapshotRead.Invalid
        }
        if (raw == null) return@synchronized GitHubSnapshotRead.Missing
        try {
            GitHubSnapshotRead.Available(decodeHistory(JSONObject(raw), repositoryId))
        } catch (_: Exception) {
            GitHubSnapshotRead.Invalid
        }
    }

    fun save(
        repositoryId: Long,
        expected: GitHubSnapshotHistory?,
        next: GitHubSnapshotHistory
    ): Boolean = synchronized(lock) {
        require(repositoryId > 0)
        if (next.current.repository.id != repositoryId ||
            next.previous != expected?.current ||
            next.current.refreshedAt <= 0 ||
            (next.newCommitCount != null && next.newCommitCount < 0) ||
            ((next.previous == null) != (next.newCommitCount == null))
        ) return@synchronized false
        val encoded = try {
            val json = encodeHistory(next)
            if (decodeHistory(json, repositoryId) != next) return@synchronized false
            json.toString()
        } catch (_: Exception) {
            return@synchronized false
        }
        when (val current = read(repositoryId)) {
            GitHubSnapshotRead.Missing -> if (expected != null) return@synchronized false
            GitHubSnapshotRead.Invalid -> return@synchronized false
            is GitHubSnapshotRead.Available -> if (current.history != expected) return@synchronized false
        }
        try {
            preferences.writeString(snapshotKey(repositoryId), encoded)
        } catch (_: RuntimeException) {
            false
        }
    }

    fun lastRefreshAttemptAt(repositoryId: Long): Long? = synchronized(lock) {
        require(repositoryId > 0)
        try {
            preferences.readString(attemptKey(repositoryId))?.toLongOrNull()?.takeIf { it > 0 }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun markRefreshAttempt(repositoryId: Long, attemptedAt: Long): Boolean = synchronized(lock) {
        require(repositoryId > 0)
        attemptedAt > 0 && try {
            preferences.writeString(attemptKey(repositoryId), attemptedAt.toString())
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun snapshotKey(repositoryId: Long) = "github_project_snapshot_v1_$repositoryId"
    private fun attemptKey(repositoryId: Long) = "github_project_refresh_attempt_v1_$repositoryId"

    private fun encodeHistory(history: GitHubSnapshotHistory): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("previous", history.previous?.let(::encodeSnapshot) ?: JSONObject.NULL)
        .put("current", encodeSnapshot(history.current))
        .put("new_commit_count", history.newCommitCount ?: JSONObject.NULL)

    private fun decodeHistory(json: JSONObject, repositoryId: Long): GitHubSnapshotHistory {
        if (json.getInt("version") != VERSION) throw JSONException("Unknown GitHub snapshot version")
        val previous = json.optJSONObject("previous")?.let(::decodeSnapshot)
        val current = decodeSnapshot(json.getJSONObject("current"))
        val newCommitCount = if (json.isNull("new_commit_count")) null else json.getInt("new_commit_count")
        if (current.repository.id != repositoryId ||
            (previous != null && previous.repository.id != repositoryId) ||
            (newCommitCount != null && newCommitCount < 0) ||
            ((previous == null) != (newCommitCount == null))
        ) throw JSONException("Invalid GitHub snapshot identity")
        return GitHubSnapshotHistory(previous, current, newCommitCount)
    }

    private fun encodeSnapshot(snapshot: GitHubProjectSnapshot): JSONObject = JSONObject()
        .put("repository", encodeRepository(snapshot.repository))
        .put("branch", encodeBranch(snapshot.branch))
        .put("workflows", encodeWorkflows(snapshot.workflows))
        .put("issues", encodeIssues(snapshot.issues))
        .put("refreshed_at", snapshot.refreshedAt)

    private fun decodeSnapshot(json: JSONObject): GitHubProjectSnapshot {
        val repository = decodeRepository(json.getJSONObject("repository"))
        val branch = decodeBranch(json.getJSONObject("branch"))
        val workflows = decodeWorkflows(json.getJSONObject("workflows"))
        val issues = decodeIssues(json.getJSONObject("issues"))
        val refreshedAt = json.getLong("refreshed_at")
        if (refreshedAt <= 0 || !branch.commitSha.matches(SHA) || !branch.treeSha.matches(SHA) ||
            !isGitHubRepositoryName(repository.fullName)
        ) throw JSONException("Invalid GitHub snapshot")
        return GitHubProjectSnapshot(repository, branch, workflows, issues, refreshedAt)
    }

    private fun encodeRepository(repository: GitHubRepository) = JSONObject()
        .put("id", repository.id)
        .put("full_name", repository.fullName)
        .put("description", repository.description ?: JSONObject.NULL)
        .put("default_branch", repository.defaultBranch)
        .put("private", repository.isPrivate)
        .put("archived", repository.archived)

    private fun decodeRepository(json: JSONObject) = GitHubRepository(
        id = json.getLong("id").takeIf { it > 0 } ?: throw JSONException("Invalid repository id"),
        fullName = json.getString("full_name"),
        description = json.optionalString("description"),
        defaultBranch = json.getString("default_branch"),
        isPrivate = json.getBoolean("private"),
        archived = json.getBoolean("archived")
    )

    private fun encodeBranch(branch: GitHubBranchPreview) = JSONObject()
        .put("name", branch.name)
        .put("protected", branch.protected)
        .put("commit_sha", branch.commitSha)
        .put("tree_sha", branch.treeSha)
        .put("message", branch.message)
        .put("committed_at", branch.committedAt ?: JSONObject.NULL)

    private fun decodeBranch(json: JSONObject) = GitHubBranchPreview(
        name = json.getString("name"),
        protected = json.getBoolean("protected"),
        commitSha = json.getString("commit_sha"),
        treeSha = json.getString("tree_sha"),
        message = json.getString("message"),
        committedAt = json.optionalString("committed_at")
    )

    private fun encodeWorkflows(snapshot: GitHubWorkflowSnapshot): JSONObject {
        val latest = snapshot.latest?.let { run ->
            JSONObject()
                .put("id", run.id)
                .put("name", run.name)
                .put("run_number", run.runNumber)
                .put("status", run.status)
                .put("conclusion", run.conclusion ?: JSONObject.NULL)
                .put("created_at", run.createdAt)
                .put("updated_at", run.updatedAt)
                .put("url", run.url)
        } ?: JSONObject.NULL
        return JSONObject().put("total_count", snapshot.totalCount).put("latest", latest)
    }

    private fun decodeWorkflows(json: JSONObject): GitHubWorkflowSnapshot {
        val count = json.getInt("total_count")
        if (count < 0) throw JSONException("Invalid workflow count")
        val run = json.optJSONObject("latest")?.let {
            GitHubWorkflowRun(
                id = it.getLong("id").takeIf { id -> id > 0 } ?: throw JSONException("Invalid workflow id"),
                name = it.getString("name"),
                runNumber = it.getLong("run_number"),
                status = it.getString("status"),
                conclusion = it.optionalString("conclusion"),
                createdAt = it.getString("created_at"),
                updatedAt = it.getString("updated_at"),
                url = it.getString("url")
            )
        }
        if ((count == 0) != (run == null)) throw JSONException("Inconsistent workflow summary")
        return GitHubWorkflowSnapshot(count, run)
    }

    private fun encodeIssues(snapshot: GitHubIssuesSnapshot) = JSONObject()
        .put("open_count", snapshot.openCount)
        .put("closed_count", snapshot.closedCount)
        .put("open", JSONArray().apply { snapshot.open.forEach { put(encodeIssue(it)) } })
        .put("closed", JSONArray().apply { snapshot.closed.forEach { put(encodeIssue(it)) } })

    private fun decodeIssues(json: JSONObject): GitHubIssuesSnapshot {
        val openCount = json.getInt("open_count")
        val closedCount = json.getInt("closed_count")
        val open = json.getJSONArray("open").toIssueList()
        val closed = json.getJSONArray("closed").toIssueList()
        if (openCount < open.size || closedCount < closed.size ||
            open.any { it.state != "open" } || closed.any { it.state != "closed" } ||
            (open + closed).map { it.number }.distinct().size != open.size + closed.size
        ) throw JSONException("Invalid issue snapshot")
        return GitHubIssuesSnapshot(openCount, closedCount, open, closed)
    }

    private fun encodeIssue(issue: GitHubIssue) = JSONObject()
        .put("id", issue.id)
        .put("number", issue.number)
        .put("title", issue.title)
        .put("state", issue.state)
        .put("created_at", issue.createdAt)
        .put("updated_at", issue.updatedAt)
        .put("url", issue.url)

    private fun JSONArray.toIssueList(): List<GitHubIssue> = List(length()) { index ->
        getJSONObject(index).let {
            GitHubIssue(
                id = it.getLong("id").takeIf { id -> id > 0 } ?: throw JSONException("Invalid issue id"),
                number = it.getLong("number").takeIf { number -> number > 0 }
                    ?: throw JSONException("Invalid issue number"),
                title = it.getString("title"),
                state = it.getString("state"),
                createdAt = it.getString("created_at"),
                updatedAt = it.getString("updated_at"),
                url = it.getString("url")
            )
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private companion object {
        const val VERSION = 1
        val SHA = Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")
        val lock = Any()
    }
}
