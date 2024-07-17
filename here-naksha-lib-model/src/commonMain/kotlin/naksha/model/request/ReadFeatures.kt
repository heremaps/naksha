@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.request.condition.Op
import naksha.model.request.condition.POp
import naksha.model.request.condition.PRef
import naksha.model.request.condition.SOp
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

@JsExport
open class ReadFeatures : ReadRequest<ReadFeatures>() {

    /**
     * true - includes deleted features in search.
     * Default: false
     */
    @JvmField
    var queryDeleted: Boolean = false

    fun withQueryDeleted(): ReadFeatures {
        queryDeleted = true
        return this
    }

    /**
     * true - includes historical versions of the feature.
     * Default: false
     */
    @JvmField
    var queryHistory: Boolean = false

    fun withQueryHistory(): ReadFeatures {
        queryHistory = true
        return this
    }

    /**
     * Defines how many versions of the feature might be returned.
     */
    @JvmField
    var limitVersions: Int = 1

    fun withLimitVersions(limitVersions: Int): ReadFeatures {
        this.limitVersions = limitVersions
        return this
    }

    /**
     * true - result will have a handle that allows fetching more data (beyond the limit).
     */
    @JvmField
    var returnHandle: Boolean = false

    fun withReturnHandle(): ReadFeatures {
        returnHandle = true
        return this
    }

    @JvmField
    var orderBy: String? = null

    fun withOrderBy(orderBy: String): ReadFeatures {
        this.orderBy = orderBy
        return this
    }

    /**
     * Collections to query.
     */
    @JvmField
    var collectionIds: MutableList<String> = mutableListOf()

    fun addCollectionId(id: String): ReadFeatures {
        collectionIds.add(id)
        return this
    }

    /**
     * op - gives ability to set conditions in `WHERE`.
     */
    @JvmField
    var op: Op? = null

    fun withOp(op: Op?): ReadFeatures {
        this.op = op
        return this
    }

    companion object ReadFeaturesCompanion {
        /**
         * Read the HEAD state using the given operation.
         * @param collectionId the collection to query.
         * @param op the operation to perform.
         */
        @JvmStatic
        @JsStatic
        fun readHeadBy(collectionId: String, op: Op) =
            ReadFeatures().addCollectionId(collectionId).withOp(op)

        /**
         * Returns a query that reads only the IDs, describes by the given operation.
         * @param collectionId the collection to read.
         * @param op the operation to perform.
         */
        @JvmStatic
        @JsStatic
        fun readIdsBy(collectionId: String, op: Op) = ReadFeatures()
            .addCollectionId(collectionId)
            .withOp(op)
            .withNoFeature()
            .withNoGeometry()
            .withNoMeta()
            .withNoTags()

        /**
         * Returns a query that reads a single feature.
         * @param collectionId the collection to query.
         * @param featureId the feature-id to query.
         */
        @JvmStatic
        @JsStatic
        fun readFeatureById(collectionId: String, featureId: String) =
            ReadFeatures().addCollectionId(collectionId).withOp(POp.eq(PRef.ID, featureId))
    }
}