package com.here.naksha.lib.base.request

import com.here.naksha.lib.base.IReadRowFilter
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ReadFeatures(
    /**
     * true - includes deleted features in search.
     * Default: false
     */
    val queryDeleted: Boolean = false,
    /**
     * true - includes historical versions of the feature.
     * Default: false
     */
    val queryHistory: Boolean = false,
    /**
     * Defines how many versions of the feature might be returned.
     */
    val limitVersions: Int = 1,
    /**
     * true - result will have a handle that allows fetching more data (beyond the limit).
     */
    val returnHandle: Boolean = false,
    val orderBy: String? = null,
    /**
     * Collections to query.
     */
    val collectionIds: Array<String>,
    /**
     * propertyOp - gives ability to set conditions in `WHERE`.
     */
    val propertyOp: POp? = null,
    /**
     * Additional conditions for geometry.
     */
    val spatialOp: SOp? = null,
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

    companion object {
        fun readHeadBy(collectionId: String, propertyOp: POp) = ReadFeatures(collectionIds = arrayOf(collectionId), propertyOp = POp())
    }
}