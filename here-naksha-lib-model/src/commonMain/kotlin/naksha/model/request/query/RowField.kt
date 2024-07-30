@file:Suppress("OPT_IN_USAGE")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.model.request.query.*
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A reference to a row-field.
 */
@JsExport
open class RowField() : AnyObject() {

    /**
     * Create a field reference.
     * @param name the field name.
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }

    override fun toString(): String = getOr("name", "")
    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?): Boolean = toString() == other.toString()

    companion object RowField_C {
        /**
         * The reference to the [feature-id][naksha.model.Row.id].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp.EQUALS]
         * - [StringOp.STARTS_WITH]
         * - [AnyOp.IS_ANY_OF]
         */
        const val ID = "id"

        /**
         * The reference to the [creation timestamp][naksha.model.Metadata.createdAt].
         *
         * This value is exposed through [naksha.model.XyzNs.createdAt].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CREATED_AT = "createdAt"

        /**
         * The reference to the [update timestamp][naksha.model.Metadata.updatedAt].
         *
         * This value is exposed through [naksha.model.XyzNs.updatedAt].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val UPDATED_AT = "updatedAt"

        /**
         * The reference to the [encoding flags and actions][naksha.model.Metadata.flags].
         *
         * This value is partially exposed through [naksha.model.XyzNs.action].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         */
        const val FLAGS = "flags"

        /**
         * The reference to the [hash][naksha.model.Metadata.hash].
         *
         * This value is exposed through [naksha.model.XyzNs.hash].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp.EQ]
         * - [AnyOp.IS_ANY_OF]
         */
        const val HASH = "hash"

        /**
         * The reference to the [transaction number][naksha.model.Metadata.version].
         *
         * This value is exposed through [naksha.model.XyzNs.version].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val TXN = "txn"

        /**
         * The reference to the [previous transaction number][naksha.model.Metadata.prevVersion].
         *
         * This value is part of the [naksha.model.XyzNs.puuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val PTXN = "ptxn"

        /**
         * The reference to the [transaction local identifier][naksha.model.Metadata.uid].
         *
         * This value is part of the [naksha.model.XyzNs.uuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val UID = "uid"

        /**
         * The reference to the [previous transaction local identifier][naksha.model.Metadata.puid].
         *
         * This value is part of the [naksha.model.XyzNs.puuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val PUID = "puid"

        /**
         * The reference to the [reference point HERE tile identifier in level 15][naksha.model.Metadata.geoGrid].
         *
         * The [binary tile identifier][naksha.geo.HereTile] where the [reference-point][naksha.model.NakshaFeature.referencePoint] of the feature is located. It is possible to search directly the grid, but another options is to use the specialise [SpRefInHereTile] query. While this is more flexible, the specialised query will have a much better cache rate, and may run much faster.
         *
         * This value is part of the [naksha.model.XyzNs.geoGrid].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val GEO_GRID = "geoGrid"

        /**
         * The property reference to the [author][naksha.model.Metadata.author].
         *
         * This value is exposed as [naksha.model.XyzNs.author].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp.EQUALS]
         * - [StringOp.STARTS_WITH]
         * - [AnyOp.IS_ANY_OF]
         */
        const val AUTHOR = "author"

        /**
         * The property reference to the [author change timestamp][naksha.model.Metadata.authorTs].
         *
         * This value is exposed as [naksha.model.XyzNs.authorTs].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val AUTHOR_TS = "author_ts"

        /**
         * The property reference to the [author change timestamp][naksha.model.Metadata.appId].
         *
         * This value is exposed as [naksha.model.XyzNs.appId].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp.EQUALS]
         * - [StringOp.STARTS_WITH]
         * - [AnyOp.IS_ANY_OF]
         */
        const val APP_ID = "app_id"

        /**
         * The reference to the [feature][naksha.model.Row.feature].
         *
         * This can only be queried using a special [property query][IPropertyQuery].
         */
        const val FEATURE = "feature"

        /**
         * The reference to the [geometry][naksha.model.Row.geo].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val GEOMETRY = "geo"

        /**
         * The reference to the [reference point][naksha.model.Row.referencePoint].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val REF_POINT = "referencePoint"

        /**
         * The reference to the [tags][naksha.model.Row.tags].
         *
         * This can only be queried using a special [tag query][ITagQuery].
         */
        const val TAGS = "tags"

        private val STRING = NotNullProperty<RowField, String>(String::class) { _, _ -> "" }
    }

    /**
     * The name of the field.
     */
    var name by STRING
}