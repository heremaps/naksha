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
 * The columns of a [tuple][naksha.model.Tuple], that can be used in [queries][naksha.model.request.RequestQuery] via [MetaQuery][naksha.model.request.query.MetaQuery].
 * @since 3.0.0
 */
@JsExport
open class MetaColumn() : AnyObject() {

    /**
     * Create a column reference.
     * @param name the field name.
     * @since 3.0.0
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
         * - [StringOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val ID = "id"

        /**
         * Returns a new meta-column for [ID].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun id(): MetaColumn = MetaColumn(ID)

        /**
         * The reference to the [creation timestamp][naksha.model.Metadata.createdAt].
         *
         * This value is exposed through [naksha.model.XyzNs.createdAt].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CREATED_AT = "createdAt"

        /**
         * Returns a new meta-column for [CREATED_AT].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun createdAt(): MetaColumn = MetaColumn(CREATED_AT)

        /**
         * The reference to the [update timestamp][naksha.model.Metadata.updatedAt].
         *
         * This value is exposed through [naksha.model.XyzNs.updatedAt].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val UPDATED_AT = "updatedAt"

        /**
         * Returns a new meta-column for [UPDATED_AT].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun updatedAt(): MetaColumn = MetaColumn(UPDATED_AT)

        /**
         * The reference to the [encoding flags and actions][naksha.model.Metadata.flags].
         *
         * This value is exposed through [naksha.model.XyzNs.flags].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val FLAGS = "flags"

        /**
         * Returns a new meta-column for [FLAGS].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun flags(): MetaColumn = MetaColumn(FLAGS)

        /**
         * The reference to the binary [operation][naksha.model.Operation], which actually is a subset of bits from the [flags][naksha.model.Metadata.flags].
         *
         * This value is exposed through [naksha.model.XyzNs.operation].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val OPERATION = "operation"

        /**
         * Returns a new meta-column for [OPERATION].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun operation(): MetaColumn = MetaColumn(OPERATION)

        /**
         * The reference to the binary [action][naksha.model.Action], which actually is a subset of bits from the [flags][naksha.model.Metadata.flags].
         *
         * This value is exposed through [naksha.model.XyzNs.action].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val ACTION = "action"

        /**
         * Returns a new meta-column for [ACTION].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun action(): MetaColumn = MetaColumn(ACTION)

        /**
         * The reference to the [hash][naksha.model.Metadata.calculateHash].
         *
         * This value is exposed through [naksha.model.XyzNs.hash].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val HASH = "hash"

        /**
         * Returns a new meta-column for [HASH].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun hash(): MetaColumn = MetaColumn(HASH)

        /**
         * The reference to the [change-count][naksha.model.Metadata.changeCount].
         *
         * This value is exposed through [naksha.model.XyzNs.changeCount].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CHANGE_COUNT = "changeCount"

        /**
         * Returns a new meta-column for [CHANGE_COUNT].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun changeCount(): MetaColumn = MetaColumn(CHANGE_COUNT)

        /**
         * The reference to the [next version][naksha.model.Metadata.nextVersion].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val NEXT_VERSION = "txn_next"

        /**
         * Returns a new meta-column for [NEXT_VERSION].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun nextVersion(): MetaColumn = MetaColumn(NEXT_VERSION)

        /**
         * The reference to the [version][naksha.model.Metadata.version] (_transaction number_).
         *
         * This value is exposed through [naksha.model.XyzNs.version].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val VERSION = "txn"

        /**
         * Returns a new meta-column for [VERSION].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun version(): MetaColumn = MetaColumn(VERSION)

        /**
         * The reference to the [tuple-number][naksha.model.Metadata.tupleNumber].
         *
         * This value is part of the [naksha.model.XyzNs.uuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_ANY_OF]
         */
        const val TUPLE_NUMBER = "tuple_number"

        /**
         * Returns a new meta-column for [TUPLE_NUMBER].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun tupleNumber(): MetaColumn = MetaColumn(TUPLE_NUMBER)

        /**
         * The reference to the [previous tuple-number][naksha.model.Metadata.prevTupleNumber].
         *
         * This value is part of the [naksha.model.XyzNs.puuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val PREV_TUPLE_NUMBER = "prev_tn"

        /**
         * Returns a new meta-column for [PREV_TUPLE_NUMBER].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun prevTupleNumber(): MetaColumn = MetaColumn(PREV_TUPLE_NUMBER)

        /**
         * The reference to the [previous tuple-number][naksha.model.Metadata.baseTupleNumber].
         *
         * This value is part of the [naksha.model.XyzNs.muuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val BASE_TUPLE_NUMBER = "base_tn"

        /**
         * Returns a new meta-column for [PREV_TUPLE_NUMBER].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun baseTupleNumber(): MetaColumn = MetaColumn(BASE_TUPLE_NUMBER)

        /**
         * The reference to the [binary HERE tile number][naksha.geo.HereTile], indexing the [metadata HERE tile number][naksha.model.Metadata.hereTile].
         *
         * The [binary HERE tile number][naksha.geo.HereTile] where the [reference-point][naksha.model.objects.NakshaFeature.referencePoint] of the [feature][naksha.model.objects.NakshaFeature] is located. It is possible to search directly the grid, but another options is to use the specialise [SpRefInHereTile] query. While this is more flexible, the specialised query will have a much better cache rate, and may run much faster.
         *
         * This value is part of the [naksha.model.XyzNs.hereTile].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val HERE_TILE = "hereTile"

        /**
         * Returns a new meta-column for [HERE_TILE].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun hereTile(): MetaColumn = MetaColumn(HERE_TILE)

        /**
         * The property reference to the [author][naksha.model.Metadata.author].
         *
         * This value is exposed as [naksha.model.XyzNs.author].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val AUTHOR = "author"

        /**
         * Returns a new meta-column for [AUTHOR].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun author(): MetaColumn = MetaColumn(AUTHOR)

        /**
         * The property reference to the [origin][naksha.model.Metadata.origin].
         *
         * This value is exposed as [naksha.model.XyzNs.origin].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val ORIGIN = "origin"

        /**
         * Returns a new meta-column for [ORIGIN].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun origin(): MetaColumn = MetaColumn(ORIGIN)

        /**
         * The property reference to the [target][naksha.model.Metadata.target].
         *
         * This value is exposed as [naksha.model.XyzNs.target].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val TARGET = "target"

        /**
         * Returns a new meta-column for [TARGET].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun target(): MetaColumn = MetaColumn(TARGET)

        /**
         * The property reference to the [author change timestamp][naksha.model.Metadata.authorTs].
         *
         * This value is exposed as [naksha.model.XyzNs.authorTs].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val AUTHOR_TS = "author_ts"

        /**
         * Returns a new meta-column for [AUTHOR_TS].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun authorTs(): MetaColumn = MetaColumn(AUTHOR_TS)

        /**
         * The property reference to the [author change timestamp][naksha.model.Metadata.appId].
         *
         * This value is exposed as [naksha.model.XyzNs.appId].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val APP_ID = "app_id"

        /**
         * Returns a new meta-column for [APP_ID].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun appId(): MetaColumn = MetaColumn(APP_ID)

        /**
         * The reference to the [feature][naksha.model.Tuple.feature].
         *
         * This can only be queried using a special [property query][IPropertyQuery].
         */
        const val FEATURE = "feature"

        /**
         * Returns a new meta-column for [FEATURE].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun feature(): MetaColumn = MetaColumn(FEATURE)

        /**
         * The reference to the [geometry][naksha.model.Tuple.geo].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val GEOMETRY = "geo"

        /**
         * Returns a new meta-column for [GEOMETRY].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun geometry(): MetaColumn = MetaColumn(GEOMETRY)

        /**
         * The reference to the [reference point][naksha.model.Tuple.referencePoint].
         *
         * This can only be queried using a special [spatial query][ISpatialQuery].
         */
        const val REF_POINT = "referencePoint"

        /**
         * Returns a new meta-column for [REF_POINT].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun referencePoint(): MetaColumn = MetaColumn(REF_POINT)

        /**
         * The reference to the [tags][naksha.model.Tuple.tags].
         *
         * This can only be queried using a special [tag query][ITagQuery].
         */
        const val TAGS = "tags"

        /**
         * Returns a new meta-column for [TAGS].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun tags(): MetaColumn = MetaColumn(TAGS)

        /**
         * The reference to the [attachment][naksha.model.Tuple.attachment].
         *
         * Attachments can't be queried!
         */
        const val ATTACHMENT = "attachment"

        /**
         * Returns a new meta-column for [ATTACHMENT].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun attachment(): MetaColumn = MetaColumn(ATTACHMENT)

        private val STRING = NotNullProperty<MetaColumn, String>(String::class) { _, _ -> "" }
    }

    /**
     * The name of the field.
     */
    var name by STRING
}