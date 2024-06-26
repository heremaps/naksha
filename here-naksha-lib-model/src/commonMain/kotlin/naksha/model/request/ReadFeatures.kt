package naksha.model.request

import naksha.model.IReadRowFilter
import naksha.model.request.condition.Op
import naksha.model.request.condition.POp
import naksha.model.request.condition.PRef
import naksha.model.request.condition.SOp
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
     * op - gives ability to set conditions in `WHERE`.
     */
    val op: Op? = null,
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
        fun readSingleHead(collectionId: String) = ReadFeatures(collectionIds = arrayOf(collectionId))

        fun readHeadBy(collectionId: String, op: Op) =
            ReadFeatures(collectionIds = arrayOf(collectionId), op = op)

        fun readIdsBy(collectionId: String, op: Op) = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            op = op,
            noFeature = true,
            noGeometry = true,
            noMeta = true,
            noTags = true
        )

        fun readFeaturesByIdRequest(collectionId: String, featureId: String, limitVersions: Int = 1, queryDeleted: Boolean = false) = ReadFeatures(
            collectionIds = arrayOf(collectionId),
            op = POp.eq(PRef.ID, featureId),
            limitVersions = limitVersions,
            queryDeleted = queryDeleted
        )
    }
}