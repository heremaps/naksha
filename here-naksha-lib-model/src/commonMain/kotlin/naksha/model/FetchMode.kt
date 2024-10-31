@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * The fetch modes supported by [fetchTuples][ISession.fetchTuples] operations.
 */
@JsExport
class FetchMode private constructor() {
    companion object FetchMode_C {
        /**
         * Fetch the `id`.
         */
        const val FETCH_ID: FetchBits = 1

        /**
         * Fetch the [metadata][Tuple.meta], which includes the `id`.
         */
        const val FETCH_META: FetchBits = 2

        /**
         * Fetch the [feature][Tuple.feature].
         */
        const val FETCH_FEATURE: FetchBits = 4

        /**
         * Fetch the [geometry][Tuple.geo].
         */
        const val FETCH_GEOMETRY: FetchBits = 8

        /**
         * Fetch the [reference-point][Tuple.referencePoint].
         */
        const val FETCH_REFERENCE_POINT: FetchBits = 16

        /**
         * Fetch the [tags][Tuple.tags].
         */
        const val FETCH_TAGS: FetchBits = 32

        /**
         * Fetch the [attachment][Tuple.attachment].
         */
        const val FETCH_ATTACHMENT: FetchBits = 64

        /**
         * Fetch the full [Tuple].
         */
        const val FETCH_ALL: FetchBits = 127

        /**
         * Fetch the full [Tuple], do not use cache.
         */
        const val FETCH_ALL_NO_CACHE: FetchBits = 255

        /**
         * Fetch only from cache, can't be combined with other fetch-modes!
         */
        const val FETCH_CACHE: FetchBits = 0

        /**
         * Do not fetch from cache.
         */
        const val FETCH_NO_CACHE: FetchBits = 256
    }
}