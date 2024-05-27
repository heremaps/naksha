@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.base.request

import com.here.naksha.lib.base.IReadRowFilter
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Read collections request. Designed to return information from `naksha~collections`.
 */
@JsExport
class ReadCollections(
    /**
     * Ids of collections to search.
     */
    val ids: Array<String>,
    /**
     * true - includes deleted features in search.
     * Default: false
     */
    val queryDeleted: Boolean = false,
    /**
     * @see ReadRequest.limit
     * default: DEFAULT_LIMIT
     */
    limit: Int = DEFAULT_LIMIT,
    /**
     * @see Request.noFeature
     * default: false
     */
    noFeature: Boolean = false,
    /**
     * @see Request.noGeometry
     * default: false
     */
    noGeometry: Boolean = false,
    /**
     * @see Request.noMeta
     * default: false
     */
    noMeta: Boolean = false,
    /**
     * @see Request.noTags
     * default: false
     */
    noTags: Boolean = false,
    /**
     * @see Request.resultFilter
     * default: empty
     */
    resultFilter: Array<IReadRowFilter> = emptyArray()
) : ReadRequest(limit, noFeature, noGeometry, noMeta, noTags, resultFilter) {
}