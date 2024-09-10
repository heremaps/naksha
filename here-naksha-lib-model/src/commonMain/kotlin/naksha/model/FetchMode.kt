@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * The fetch modes supported by fetch operations.
 */
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
     * Fetch all data except for the [feature][Tuple.feature].
     */
    FETCH_ALL_BUT_FEATURE("all-but-feature"),

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