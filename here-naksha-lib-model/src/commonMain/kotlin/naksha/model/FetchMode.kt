package naksha.model

import kotlin.js.JsExport

@JsExport
enum class FetchMode(val raw: String) {

    /**
     * Fetch only the `id`, other parts only from cache.
     */
    FETCH_ID("id"),

    /**
     * Fetch the [metadata][Metadata], other parts only from cache.
     */
    FETCH_META("meta"),

    /**
     * Fetch all columns.
     */
    FETCH_ALL("all"),

    /**
     * Fetch all columns, do not use the cache.
     */
    FETCH_ALL_NO_CACHE("all-no-cache"),

    /**
     * Only load form cache.
     */
    FETCH_CACHE("cache")
}