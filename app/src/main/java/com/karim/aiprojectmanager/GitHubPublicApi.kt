package com.karim.aiprojectmanager

import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

internal data class PublicRepository(val fullName: String, val defaultBranch: String)

internal sealed interface GitHubResult {
    data class Success(val repository: PublicRepository) : GitHubResult
    data class HttpError(val statusCode: Int) : GitHubResult
    data object NetworkError : GitHubResult
    data object Timeout : GitHubResult
    data object InvalidResponse : GitHubResult
}

internal class GitHubPublicApi(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    /** Blocking HTTPS GET. Call from an IO dispatcher. */
    fun repository(owner: String, name: String): GitHubResult {
        require(owner.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")))
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && name != "." && name != "..")
        val expectedName = "$owner/$name"
        return try {
            val connection = openConnection(URL("https://api.github.com/repos/$expectedName"))
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 5_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AI-Project-Manager")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")

                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    GitHubResult.HttpError(status)
                } else {
                    val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
                    val size = connection.inputStream.use { stream ->
                        var count = 0
                        while (count < bytes.size) {
                            val read = stream.read(bytes, count, bytes.size - count)
                            if (read == -1) break
                            count += read
                        }
                        count
                    }
                    if (size > MAX_RESPONSE_BYTES) GitHubResult.InvalidResponse
                    else parseRepository(bytes.decodeToString(endIndex = size), expectedName)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: SocketTimeoutException) {
            GitHubResult.Timeout
        } catch (_: IOException) {
            GitHubResult.NetworkError
        }
    }

    private fun parseRepository(json: String, expectedName: String): GitHubResult {
        return try {
            JsonReader(StringReader(json)).use { reader ->
                var fullName: String? = null
                var defaultBranch: String? = null
                var isPrivate: Boolean? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "full_name" -> {
                            require(fullName == null && reader.peek() == JsonToken.STRING)
                            fullName = reader.nextString()
                        }
                        "default_branch" -> {
                            require(defaultBranch == null && reader.peek() == JsonToken.STRING)
                            defaultBranch = reader.nextString()
                        }
                        "private" -> {
                            require(isPrivate == null && reader.peek() == JsonToken.BOOLEAN)
                            isPrivate = reader.nextBoolean()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT)
                val name = requireNotNull(fullName)
                val branch = requireNotNull(defaultBranch)
                require(name.equals(expectedName, ignoreCase = true) && branch.isNotBlank() && isPrivate == false)
                GitHubResult.Success(PublicRepository(name, branch))
            }
        } catch (_: IOException) {
            GitHubResult.InvalidResponse
        } catch (_: IllegalArgumentException) {
            GitHubResult.InvalidResponse
        } catch (_: IllegalStateException) {
            GitHubResult.InvalidResponse
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
