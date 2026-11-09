package com.karim.aiprojectmanager

import android.os.SystemClock
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.json.JSONException
import org.json.JSONObject

internal data class GitHubRepository(
    val id: Long,
    val fullName: String,
    val description: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val archived: Boolean
)

internal data class GitHubRepositoryCursor(
    val installationsPage: Int? = 1,
    val installationIds: List<Long> = emptyList(),
    val repositoryPage: Int = 1
)

internal data class GitHubRepositoryPage(
    val repositories: List<GitHubRepository>,
    val next: GitHubRepositoryCursor?
)

internal enum class GitHubReadError {
    EXPIRED, FORBIDDEN, NOT_FOUND, EMPTY, RATE_LIMIT, NETWORK, TIMEOUT, INVALID_RESPONSE, HTTP
}

internal class GitHubReadFailure(
    val reason: GitHubReadError,
    val retryAfterSeconds: Long? = null
) : Exception(reason.name)

internal fun interface GitHubRepositorySource {
    suspend fun repositories(cursor: GitHubRepositoryCursor): GitHubRepositoryPage
}

internal class GitHubRepositoryApi(
    private val token: String,
    private val openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : GitHubRepositorySource, GitHubRepositoryPreviewSource {
    private val rateLimitedUntil = AtomicLong()

    override suspend fun repositories(cursor: GitHubRepositoryCursor): GitHubRepositoryPage {
        if (cursor.installationIds.isEmpty()) {
            val page = cursor.installationsPage ?: return GitHubRepositoryPage(emptyList(), null)
            val response = get("/user/installations?per_page=30&page=$page")
            val installations = response.json().getJSONArray("installations")
            val ids = List(installations.length()) { index ->
                installations.getJSONObject(index).positiveId()
            }.distinct()
            val nextPage = (page + 1).takeIf { response.hasNext }
            val next = GitHubRepositoryCursor(nextPage, ids).takeIf { ids.isNotEmpty() || nextPage != null }
            return GitHubRepositoryPage(emptyList(), next)
        }

        val installation = cursor.installationIds.first()
        val response = get("/user/installations/$installation/repositories?per_page=30&page=${cursor.repositoryPage}")
        val repositories = response.json().getJSONArray("repositories")
        val items = List(repositories.length()) { repositories.getJSONObject(it).repository() }
        val next = if (response.hasNext) cursor.copy(repositoryPage = cursor.repositoryPage + 1)
        else cursor.copy(installationIds = cursor.installationIds.drop(1), repositoryPage = 1)
        return GitHubRepositoryPage(items, next.takeIf { it.installationIds.isNotEmpty() || it.installationsPage != null })
    }

    override suspend fun branch(repository: GitHubRepository): GitHubBranchPreview = readJson {
        val ref = URLEncoder.encode(repository.defaultBranch, "UTF-8").replace("+", "%20")
        val branch = get("${repository.path()}/branches/$ref").json()
        val commit = branch.getJSONObject("commit")
        val metadata = commit.getJSONObject("commit")
        GitHubBranchPreview(
            name = branch.getString("name"),
            protected = branch.getBoolean("protected"),
            commitSha = commit.getString("sha").checkedSha(),
            treeSha = metadata.getJSONObject("tree").getString("sha").checkedSha(),
            message = metadata.getString("message").lineSequence().first().take(250),
            committedAt = metadata.optJSONObject("committer")?.optString("date")?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun readme(repository: GitHubRepository, commitSha: String): GitHubReadmePreview {
        val response = get(
            "${repository.path()}/readme?ref=${commitSha.checkedSha()}",
            accept = "application/vnd.github.raw+json", limit = 64 * 1024, allowTruncation = true
        )
        return GitHubReadmePreview(response.body, response.truncated)
    }

    override suspend fun tree(repository: GitHubRepository, treeSha: String): GitHubTreePreview = readJson {
        val response = get("${repository.path()}/git/trees/${treeSha.checkedSha()}").json()
        val tree = response.getJSONArray("tree")
        val entries = List(minOf(tree.length(), 30)) { index ->
            val entry = tree.getJSONObject(index)
            GitHubTreeEntry(entry.getString("path"), entry.getString("type") == "tree")
        }
        GitHubTreePreview(entries, response.getBoolean("truncated") || tree.length() > entries.size)
    }

    private fun GitHubRepository.path(): String {
        if (!REPOSITORY_NAME.matches(fullName) || fullName.substringAfter('/') in listOf(".", "..")) {
            throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)
        }
        return "/repos/$fullName"
    }

    private fun String.checkedSha(): String = takeIf(SHA::matches)
        ?: throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)

    private suspend fun <T> readJson(read: suspend () -> T): T = try {
        read()
    } catch (_: JSONException) {
        throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)
    }

    private suspend fun get(
        path: String,
        accept: String = "application/vnd.github+json",
        limit: Int = MAX_BYTES,
        allowTruncation: Boolean = false
    ): Response {
        val caller = currentCoroutineContext()
        return runInterruptible(Dispatchers.IO) {
            try {
                val remaining = rateLimitedUntil.get() - SystemClock.elapsedRealtime()
                if (remaining > 0) throw GitHubReadFailure(GitHubReadError.RATE_LIMIT, (remaining + 999) / 1_000)
                val connection = openConnection(URL("https://api.github.com$path"))
                try {
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 8_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", accept)
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("User-Agent", "AI-Project-Manager")
                    connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                    val deadline = SystemClock.elapsedRealtime() + 15_000L
                    val status = connection.responseCode
                    if (status != 200) throw failure(connection, status)
                    val response = connection.inputStream.use { stream ->
                        val bytes = ByteArray(limit + 1)
                        var size = 0
                        while (size < bytes.size) {
                            caller.ensureActive()
                            if (SystemClock.elapsedRealtime() >= deadline) throw SocketTimeoutException()
                            val count = stream.read(bytes, size, bytes.size - size)
                            if (count < 0) break
                            size += count
                        }
                        val truncated = size > limit
                        if (truncated && !allowTruncation) throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)
                        Response(
                            bytes.decodeToString(endIndex = minOf(size, limit)),
                            NEXT_LINK.containsMatchIn(connection.getHeaderField("Link").orEmpty()),
                            truncated
                        )
                    }
                    caller.ensureActive()
                    if (SystemClock.elapsedRealtime() >= deadline) throw SocketTimeoutException()
                    response
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                caller.ensureActive()
                throw GitHubReadFailure(GitHubReadError.TIMEOUT)
            } catch (_: IOException) {
                caller.ensureActive()
                throw GitHubReadFailure(GitHubReadError.NETWORK)
            }
        }
    }

    private fun failure(connection: HttpsURLConnection, status: Int): GitHubReadFailure {
        val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceAtLeast(0)
        val exhausted = connection.getHeaderField("X-RateLimit-Remaining") == "0"
        val reset = if (exhausted) connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
            ?.minus(System.currentTimeMillis() / 1_000L)?.coerceAtLeast(0) else null
        if (status == 429 || status == 403 && (retryAfter != null || exhausted || isSecondaryLimit(connection))) {
            val seconds = (retryAfter ?: reset ?: 60L).coerceIn(1L, 86_400L)
            rateLimitedUntil.accumulateAndGet(SystemClock.elapsedRealtime() + seconds * 1_000L, ::maxOf)
            return GitHubReadFailure(GitHubReadError.RATE_LIMIT, seconds)
        }
        return GitHubReadFailure(when (status) {
            401 -> GitHubReadError.EXPIRED
            403 -> GitHubReadError.FORBIDDEN
            404 -> GitHubReadError.NOT_FOUND
            409 -> GitHubReadError.EMPTY
            else -> GitHubReadError.HTTP
        })
    }

    private fun isSecondaryLimit(connection: HttpsURLConnection): Boolean {
        val errorBody = connection.errorStream?.use { stream ->
            val bytes = ByteArray(4_096)
            val count = stream.read(bytes)
            if (count > 0) bytes.decodeToString(endIndex = count) else ""
        } ?: return false
        return try {
            JSONObject(errorBody).optString("message").contains("secondary rate limit", ignoreCase = true)
        } catch (_: JSONException) {
            false
        }
    }

    private fun JSONObject.repository(): GitHubRepository {
        val name = getString("full_name")
        if (!REPOSITORY_NAME.matches(name)) throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)
        return GitHubRepository(
            positiveId(), name, optString("description").takeUnless { isNull("description") || it.isBlank() },
            getString("default_branch"), getBoolean("private"), getBoolean("archived")
        )
    }

    private fun JSONObject.positiveId(): Long = getLong("id").takeIf { it > 0 }
        ?: throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)

    private class Response(val body: String, val hasNext: Boolean, val truncated: Boolean) {
        fun json() = JSONObject(body)
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
        val NEXT_LINK = Regex("""(?:^|,)\s*<[^>]+>\s*;\s*rel\s*=\s*"next"(?:\s*[,;]|\s*$)""")
        val SHA = Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")
        val REPOSITORY_NAME = Regex("[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}")
    }
}

internal suspend fun loadGitHubRepositoryPage(
    source: GitHubRepositorySource,
    cursor: GitHubRepositoryCursor
): GitHubRepositoryPage = try {
    var page = source.repositories(cursor)
    repeat(3) {
        val next = page.next
        if (page.repositories.isEmpty() && next != null) {
            currentCoroutineContext().ensureActive()
            page = source.repositories(next)
        }
    }
    page
} catch (_: JSONException) {
    throw GitHubReadFailure(GitHubReadError.INVALID_RESPONSE)
}
