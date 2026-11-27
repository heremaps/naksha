package com.here.naksha.cli.stream.providers

import naksha.base.JvmList
import naksha.base.JvmMap
import naksha.base.NakshaError.NakshaErrorCompanion.BAD_GATEWAY
import naksha.base.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.base.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.base.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaError.NakshaErrorCompanion.PAYLOAD_TOO_LARGE
import naksha.base.NakshaError.NakshaErrorCompanion.TIMEOUT
import naksha.base.NakshaError.NakshaErrorCompanion.TOO_MANY_REQUESTS
import naksha.base.NakshaError.NakshaErrorCompanion.UNAUTHORIZED
import naksha.base.NakshaException
import naksha.base.Platform
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.zip.GZIPInputStream

/** Read-only client for the DataHub REST API. */
class DataHubClient @JvmOverloads constructor(
    private val config: DataHubConfig,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    class Space(val id: String, val globalVersioning: Boolean, val history: Boolean)

    class Page(val features: List<JvmMap>, val handle: String?)

    fun space(spaceId: String): Space {
        val json = getJson(spaceId, "/spaces/${enc(spaceId)}", emptyMap())
        return Space(
            id = json["id"] as? String ?: spaceId,
            globalVersioning = json["enableGlobalVersioning"] == true,
            history = json["enableHistory"] == true,
        )
    }

    /** The (possibly estimated) feature count, or -1 when unknown. */
    fun count(spaceId: String): Long = try {
        val count = getJson(spaceId, "/spaces/${enc(spaceId)}/statistics", emptyMap())["count"] as? Map<*, *>
        (count?.get("value") as? Number)?.toLong() ?: -1L
    } catch (_: NakshaException) {
        -1L
    }

    fun iterate(spaceId: String, handle: String?, limit: Int, version: Long?): Page {
        val query = linkedMapOf("limit" to limit.toString())
        if (handle != null) query["handle"] = handle
        if (version != null) query["v"] = version.toString()
        val json = getJson(spaceId, "/spaces/${enc(spaceId)}/iterate", query)
        val features = (json["features"] as? List<*>) ?: JvmList()
        return Page(features.map { it as? JvmMap ?: throw NakshaException(BAD_GATEWAY, "Invalid feature in DataHub response") }, json["handle"] as? String)
    }

    private fun getJson(spaceId: String, path: String, query: Map<String, String>): JvmMap {
        val bytes = get(spaceId, path, query)
        return Platform.fromJSON(bytes) as? JvmMap ?: throw NakshaException(BAD_GATEWAY, "DataHub returned no JSON object for $path")
    }

    private fun get(spaceId: String, path: String, query: Map<String, String>): ByteArray {
        val qs = if (query.isEmpty()) "" else query.entries.joinToString("&", "?") { "${it.key}=${enc(it.value)}" }
        val uri = try {
            URI.create(config.url + path + qs)
        } catch (e: IllegalArgumentException) {
            throw NakshaException(ILLEGAL_ARGUMENT, "Invalid DataHub URL: ${e.message}")
        }
        val builder = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(config.timeoutMillis))
            .header("Accept", "application/geo+json, application/json")
            .header("Accept-Encoding", "gzip")
        config.token()?.let { builder.header("Authorization", "Bearer $it") }
        val request = builder.build()

        var attempt = 0
        while (true) {
            val response: HttpResponse<ByteArray>
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
            } catch (e: HttpTimeoutException) {
                if (attempt++ < config.maxRetries) { backoff(attempt, null); continue }
                throw NakshaException(TIMEOUT, "DataHub request timed out: GET $path")
            } catch (e: IOException) {
                if (attempt++ < config.maxRetries) { backoff(attempt, null); continue }
                throw NakshaException(BAD_GATEWAY, "DataHub request failed: GET $path: ${e.message}")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw NakshaException(EXCEPTION, "Interrupted while waiting for DataHub")
            }
            val status = response.statusCode()
            if (status in 200..299) return body(response)
            if (status in RETRYABLE && attempt++ < config.maxRetries) {
                backoff(attempt, response.headers().firstValue("Retry-After").orElse(null))
                continue
            }
            val message = "DataHub responded $status to GET $path: ${errorMessage(response)}"
            throw NakshaException(
                when (status) {
                    401 -> UNAUTHORIZED
                    403 -> FORBIDDEN
                    404 -> COLLECTION_NOT_FOUND
                    413, 513 -> PAYLOAD_TOO_LARGE
                    429 -> TOO_MANY_REQUESTS
                    504 -> TIMEOUT
                    else -> BAD_GATEWAY
                },
                if (status == 404) "DataHub space not found: $spaceId" else message
            )
        }
    }

    private fun backoff(attempt: Int, retryAfter: String?) {
        val seconds = retryAfter?.trim()?.toLongOrNull()
        val millis = if (seconds != null) seconds * 1000 else minOf(30_000L, 500L shl minOf(attempt, 6))
        sleep(minOf(millis, 60_000L))
    }

    private fun body(response: HttpResponse<ByteArray>): ByteArray {
        val gzip = response.headers().firstValue("Content-Encoding").map { it.equals("gzip", true) }.orElse(false)
        return if (gzip) GZIPInputStream(response.body().inputStream()).use { it.readBytes() } else response.body()
    }

    private fun errorMessage(response: HttpResponse<ByteArray>): String {
        val text = try {
            body(response).decodeToString()
        } catch (_: IOException) {
            return "<unreadable body>"
        }
        val json = try { Platform.fromJSON(text) as? JvmMap } catch (_: Exception) { null }
        return (json?.get("errorMessage") as? String) ?: text.take(200)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    private companion object {
        val RETRYABLE = setOf(429, 500, 502, 503, 504)
    }
}
