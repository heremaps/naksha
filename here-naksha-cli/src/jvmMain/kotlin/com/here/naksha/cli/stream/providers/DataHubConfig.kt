package com.here.naksha.cli.stream.providers

import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.model.objects.NakshaStorage

/** Connection settings of a DataHub (XYZ Hub 1.24, branch `DHCodeFreeze`) instance. */
class DataHubConfig(
    val url: String,
    /** When _null_, the token is read from [TOKEN_ENV] on each request, so it never needs to be stored. */
    private val token: String?,
    val pageSize: Int,
    val maxRetries: Int,
    val timeoutMillis: Long,
) {
    fun token(): String? = token?.takeIf { it.isNotBlank() } ?: System.getenv(TOKEN_ENV)?.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_URL = "https://xyz.api.here.com/hub"
        const val TOKEN_ENV = "NAKSHA_STREAM_DATAHUB_TOKEN"
        const val DEFAULT_PAGE_SIZE = 10_000
        const val MAX_PAGE_SIZE = 100_000

        @JvmStatic
        fun from(storage: NakshaStorage): DataHubConfig {
            val p = storage.properties
            val url = (p.getRaw("url") as? String)?.trim()?.trimEnd('/')?.ifEmpty { null } ?: DEFAULT_URL
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid DataHub configuration: url must start with http:// or https://")
            }
            val pageSize = int(p.getRaw("pageSize"), "pageSize", DEFAULT_PAGE_SIZE)
            if (pageSize !in 1..MAX_PAGE_SIZE) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid DataHub configuration: pageSize must be between 1 and $MAX_PAGE_SIZE")
            }
            val maxRetries = int(p.getRaw("maxRetries"), "maxRetries", 5)
            val timeout = int(p.getRaw("timeoutSeconds"), "timeoutSeconds", 120)
            return DataHubConfig(url, p.getRaw("token") as? String, pageSize, maxOf(0, maxRetries), maxOf(1, timeout) * 1000L)
        }

        private fun int(value: Any?, key: String, default: Int): Int = when (value) {
            null -> default
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        } ?: throw NakshaException(ILLEGAL_ARGUMENT, "Invalid DataHub configuration: $key must be a number")
    }
}
