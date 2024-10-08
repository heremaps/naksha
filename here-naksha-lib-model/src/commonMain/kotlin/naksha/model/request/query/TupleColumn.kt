@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.model.request.query.*
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A reference to a column of a virtual [tuple][naksha.model.Tuple].
 */
@JsExport
open class TupleColumn() : AnyObject() {

    /**
     * Create a column reference.
     * @param name the field name.
     */
    @JsName("of")
    constructor(name: String) : this() {
        this.name = name
    }

    override fun toString(): String = getOr("name", "")
    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?): Boolean = toString() == other.toString()

    companion object TupleColumn_C {
        /**
         * The reference to the [feature-id][naksha.model.Tuple.id].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp.EQUALS]
         * - [StringOp.STARTS_WITH]
         * - [AnyOp.IS_ANY_OF]
         */
        const val ID = "id"

        /**
         * Returns a new row-column for [ID].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun id(): TupleColumn = TupleColumn(ID)

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
         * Returns a new row-column for [CREATED_AT].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun createdAt(): TupleColumn = TupleColumn(CREATED_AT)

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
         * Returns a new row-column for [UPDATED_AT].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun updatedAt(): TupleColumn = TupleColumn(UPDATED_AT)

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
         * Returns a new row-column for [FLAGS].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun flags(): TupleColumn = TupleColumn(FLAGS)

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
         * Returns a new row-column for [HASH].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun hash(): TupleColumn = TupleColumn(HASH)

        /**
         * The reference to the [change-count][naksha.model.Metadata.changeCount].
         *
         * This value is exposed through [naksha.model.XyzNs.changeCount].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp.EQ]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CHANGE_COUNT = "changeCount"

        /**
         * Returns a new row-column for [CHANGE_COUNT].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun changeCount(): TupleColumn = TupleColumn(CHANGE_COUNT)

        /**
         * The reference to the [next version][naksha.model.Metadata.nextVersion].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val NEXT_VERSION = "txn_next"

        /**
         * Returns a new row-column for [NEXT_VERSION].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun nextVersion(): TupleColumn = TupleColumn(NEXT_VERSION)

        /**
         * The reference to the [version][naksha.model.Metadata.version] (_transaction number_).
         *
         * This value is exposed through [naksha.model.XyzNs.txn].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val VERSION = "txn"

        /**
         * Returns a new row-column for [VERSION].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun version(): TupleColumn = TupleColumn(VERSION)

        /**
         * The reference to the [previous version][naksha.model.Metadata.prevVersion].
         *
         * This value is part of the [naksha.model.XyzNs.puuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [QueryNumber.*][DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val PREV_VERSION = "ptxn"

        /**
         * Returns a new row-column for [PREV_VERSION].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun prevVersion(): TupleColumn = TupleColumn(PREV_VERSION)

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
         * Returns a new row-column for [UID].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun uid(): TupleColumn = TupleColumn(UID)

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
         * Returns a new row-column for [PUID].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun puid(): TupleColumn = TupleColumn(PUID)

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
         * Returns a new row-column for [PUID].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun geoGrid(): TupleColumn = TupleColumn(GEO_GRID)

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
         * Returns a new row-column for [AUTHOR].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun author(): TupleColumn = TupleColumn(AUTHOR)

        /**
         * The property reference to the [type][naksha.model.Metadata.type].
         *
         * This value is exposed as [properties.featureType][naksha.model.objects.NakshaProperties.featureType].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp.EQUALS]
         * - [StringOp.STARTS_WITH]
         * - [AnyOp.IS_ANY_OF]
         */
        const val TYPE = "type"

        /**
         * Returns a new row-column for [TYPE].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun type(): TupleColumn = TupleColumn(TYPE)

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
         * Returns a new row-column for [AUTHOR_TS].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun authorTs(): TupleColumn = TupleColumn(AUTHOR_TS)

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
         * Returns a new row-column for [APP_ID].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun appId(): TupleColumn = TupleColumn(APP_ID)

        /**
         * The reference to the [feature][naksha.model.Tuple.feature].
         *
         * This can only be queried using a special [property query][IPropertyQuery].
         */
        const val FEATURE = "feature"

        /**
         * Returns a new row-column for [FEATURE].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun feature(): TupleColumn = TupleColumn(FEATURE)

        /**
         * The reference to the [geometry][naksha.model.Tuple.geo].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val GEOMETRY = "geo"

        /**
         * Returns a new row-column for [GEOMETRY].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun geometry(): TupleColumn = TupleColumn(GEOMETRY)

        /**
         * The reference to the [reference point][naksha.model.Tuple.referencePoint].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val REF_POINT = "referencePoint"

        /**
         * Returns a new row-column for [REF_POINT].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun referencePoint(): TupleColumn = TupleColumn(REF_POINT)

        /**
         * The reference to the [tags][naksha.model.Tuple.tags].
         *
         * This can only be queried using a special [tag query][ITagQuery].
         */
        const val TAGS = "tags"

        /**
         * Returns a new row-column for [TAGS].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun tags(): TupleColumn = TupleColumn(TAGS)

        /**
         * The reference to the [attachment][naksha.model.Tuple.attachment].
         *
         * This can only be queried using a special [property query][IPropertyQuery].
         */
        const val ATTACHMENT = "attachment"

        /**
         * Returns a new row-column for [ATTACHMENT].
         * @return a new row-column.
         */
        @JvmStatic
        @JsStatic
        fun attachment(): TupleColumn = TupleColumn(ATTACHMENT)

        private val STRING = NotNullProperty<TupleColumn, String>(String::class) { _, _ -> "" }
    }

    /**
     * The name of the field.
     */
    var name by STRING
}