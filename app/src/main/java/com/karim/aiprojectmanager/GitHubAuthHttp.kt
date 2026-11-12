package com.karim.aiprojectmanager

import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

internal class GitHubAuthHttp(
    private val openConnection: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) : GitHubAuthTransport {
    override suspend fun request(
        endpoint: GitHubAuthEndpoint,
        form: Map<String, String>,
        bearer: String?
    ): Map<String, Any> {
        val caller = currentCoroutineContext()
        return runInterruptible(Dispatchers.IO) {
            try {
                val connection = openConnection(URL(endpoint.url))
                try {
                    val deadline = SystemClock.elapsedRealtime() + REQUEST_BUDGET_MS
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 8_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.setRequestProperty("User-Agent", "AI-Project-Manager")
                    if (endpoint == GitHubAuthEndpoint.USER) {
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                        connection.setRequestProperty("Authorization", "Bearer $bearer")
                    } else {
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        val body = form.entries.joinToString("&") { (name, value) ->
                            "${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
                        }.encodeToByteArray()
                        connection.doOutput = true
                        connection.setFixedLengthStreamingMode(body.size)
                        val output = try {
                            connection.outputStream
                        } catch (_: UnknownHostException) {
                            caller.ensureActive()
                            throw GitHubAuthFailure(GitHubAuthError.DNS)
                        }
                        output.use { it.write(body) }
                    }

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val error = when {
                            endpoint == GitHubAuthEndpoint.USER && responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ->
                                GitHubAuthError.UNAUTHORIZED
                            else -> GitHubAuthError.HTTP
                        }
                        throw GitHubAuthFailure(error)
                    }
                    val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
                    val size = connection.inputStream.use { stream ->
                        var count = 0
                        while (count < bytes.size) {
                            caller.ensureActive()
                            if (SystemClock.elapsedRealtime() >= deadline) throw SocketTimeoutException()
                            val read = stream.read(bytes, count, bytes.size - count)
                            if (read == -1) break
                            count += read
                        }
                        count
                    }
                    caller.ensureActive()
                    if (SystemClock.elapsedRealtime() >= deadline) throw SocketTimeoutException()
                    if (size > MAX_RESPONSE_BYTES) throw GitHubAuthFailure(GitHubAuthError.INVALID_RESPONSE)
                    parse(endpoint, bytes, size)
                } finally {
                    connection.disconnect()
                }
            } catch (_: SocketTimeoutException) {
                caller.ensureActive()
                throw GitHubAuthFailure(GitHubAuthError.TIMEOUT)
            } catch (_: IOException) {
                caller.ensureActive()
                throw GitHubAuthFailure(GitHubAuthError.NETWORK)
            }
        }
    }

    private fun parse(endpoint: GitHubAuthEndpoint, bytes: ByteArray, size: Int): Map<String, Any> {
        val fields = when (endpoint) {
            GitHubAuthEndpoint.DEVICE_CODE -> setOf("device_code", "user_code", "verification_uri", "expires_in", "interval", "error")
            GitHubAuthEndpoint.TOKEN -> setOf("access_token", "token_type", "scope", "interval", "error")
            GitHubAuthEndpoint.USER -> setOf("login")
        }
        try {
            val result = mutableMapOf<String, Any>()
            JsonReader(StringReader(bytes.decodeToString(endIndex = size, throwOnInvalidSequence = true))).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    if (key !in fields) {
                        reader.skipValue()
                        continue
                    }
                    require(key !in result)
                    result[key] = if (key == "expires_in" || key == "interval") {
                        require(reader.peek() == JsonToken.NUMBER)
                        requireNotNull(reader.nextString().toIntOrNull())
                    } else {
                        require(reader.peek() == JsonToken.STRING)
                        reader.nextString()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT)
            }
            return result
        } catch (_: IOException) {
            throw GitHubAuthFailure(GitHubAuthError.INVALID_RESPONSE)
        } catch (_: IllegalArgumentException) {
            throw GitHubAuthFailure(GitHubAuthError.INVALID_RESPONSE)
        } catch (_: IllegalStateException) {
            throw GitHubAuthFailure(GitHubAuthError.INVALID_RESPONSE)
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val REQUEST_BUDGET_MS = 15_000L
    }
}
