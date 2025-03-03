@file:Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")

package naksha.model.request.query

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * The meta columns reference for a [tuple][naksha.model.Tuple]; can be used in [queries][naksha.model.request.RequestQuery] via [MetaQuery][naksha.model.request.query.MetaQuery].
 * @since 3.0
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
         * The name of the virtual columns that stores the [feature-id][naksha.model.Tuple.id].
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
         * The name of the virtual columns that stores the [creation timestamp][naksha.model.Metadata.createdAt].
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
         * The name of the virtual columns that stores the feature-number.
         *
         * This value is exposed through [naksha.model.XyzNs.uuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val FN = "fn"

        /**
         * Returns a new meta-column for [FN].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun fn(): MetaColumn = MetaColumn(FN)

        /**
         * The name of the virtual columns that stores the [update timestamp][naksha.model.Metadata.updatedAt].
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
         * The name of the virtual columns that stores the [encoding flags and actions][naksha.model.Metadata.flags].
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
         * The amount of changes that have been applied to a feature, a value between `1` and `2,147,483,647`.
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
         * The name of the virtual columns that stores the binary [operation][naksha.model.Operation], which actually is a subset of bits from the [flags][naksha.model.Metadata.flags].
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
         * The name of the virtual columns that stores the binary [action][naksha.model.Action], which actually is a subset of bits from the [flags][naksha.model.Metadata.flags].
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
         * The name of the virtual columns that stores the [hash][naksha.model.Metadata.calculateHash].
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
         * The name of the virtual columns that stores the [next tuple-number][naksha.model.Metadata.nextTupleNumber].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val NEXT_TN = "txn_next"

        /**
         * Returns a new meta-column for [NEXT_TN].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun nextVersion(): MetaColumn = MetaColumn(NEXT_TN)

        /**
         * The name of the virtual columns that stores the [version][naksha.model.Metadata.version] (_transaction number_).
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
         * The name of the virtual columns that stores the [uid][naksha.model.Metadata.uid].
         *
         * This value is exposed through [naksha.model.XyzNs.uid].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val UID = "uid"

        /**
         * Returns a new meta-column for [UID].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun uid(): MetaColumn = MetaColumn(UID)

        /**
         * The name of the virtual column that stores the [tuple-number][naksha.model.Metadata.tupleNumber], encoded as [160-bit variant][naksha.model.TupleNumberVariant.B160], as generated by [TupleNumber.toByteArray][naksha.model.TupleNumber.toByteArray].
         *
         * This value is part of the [naksha.model.XyzNs.uuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_ANY_OF]
         * @see [naksha.model.TupleNumberVariant.B160]
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
         * The name of the virtual column that stores the [previous tuple-number][naksha.model.Metadata.prevTupleNumber], encoded as [96-bit variant][[naksha.model.TupleNumberVariant.B96]], as generated by [TupleNumber.toByteArray][naksha.model.TupleNumber.toByteArray].
         *
         * This value is part of the [naksha.model.XyzNs.puuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         * @see [naksha.model.TupleNumberVariant.B96]
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
         * The name of the virtual column that stores the [previous tuple-number][naksha.model.Metadata.baseTupleNumber], encoded as [96-bit variant][[naksha.model.TupleNumberVariant.B96]], as generated by [TupleNumber.toByteArray][naksha.model.TupleNumber.toByteArray].
         *
         * This value is part of the [naksha.model.XyzNs.muuid].
         *
         * Supported [query operations][AnyOp] are:
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         * @see [naksha.model.TupleNumberVariant.B96]
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
         * The name of the virtual column that stores the [binary HERE tile number][naksha.geo.HereTile], indexing the [metadata HERE tile number][naksha.model.Metadata.hereTile].
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
         * The name of the virtual column that stores the [author][naksha.model.Metadata.author].
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
         * The name of the virtual column that stores the [origin][naksha.model.Metadata.origin] as string.
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
         * The name of the virtual column that stores the [target][naksha.model.Metadata.target] as string.
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
         * The name of the virtual columns that stores the [author change timestamp][naksha.model.Metadata.authorTs].
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
         * The name of the virtual columns that stores the [author change timestamp][naksha.model.Metadata.appId].
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
         * The name of the virtual columns that stores the [type][naksha.model.Metadata.ft].
         *
         * This value is exposed as [naksha.model.XyzNs.featureType].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val FEATURE_TYPE = "type"

        /**
         * Returns a new meta-column for [FEATURE_TYPE].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun featureType(): MetaColumn = MetaColumn(FEATURE_TYPE)

        /**
         * The name of the virtual columns that stores the [cv][naksha.model.Metadata.cv0] (_custom value_).
         *
         * This value is exposed through [naksha.model.XyzNs.cv0].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CV0 = "cv0"

        /**
         * Returns a new meta-column for [CV0].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cv0(): MetaColumn = MetaColumn(CV0)

        /**
         * The name of the virtual columns that stores the [cv][naksha.model.Metadata.cv1] (_custom value_).
         *
         * This value is exposed through [naksha.model.XyzNs.cv1].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CV1 = "cv1"

        /**
         * Returns a new meta-column for [CV1].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cv1(): MetaColumn = MetaColumn(CV1)

        /**
         * The name of the virtual columns that stores the [cv][naksha.model.Metadata.cv2] (_custom value_).
         *
         * This value is exposed through [naksha.model.XyzNs.cv2].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CV2 = "cv2"

        /**
         * Returns a new meta-column for [CV2].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cv2(): MetaColumn = MetaColumn(CV2)

        /**
         * The name of the virtual columns that stores the [cv][naksha.model.Metadata.cv3] (_custom value_).
         *
         * This value is exposed through [naksha.model.XyzNs.cv3].
         *
         * Supported [query operations][AnyOp] are:
         * - [DoubleOp]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CV3 = "cv3"

        /**
         * Returns a new meta-column for [CV3].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cv3(): MetaColumn = MetaColumn(CV3)

        /**
         * The name of the virtual columns that stores the [first custom value][naksha.model.Metadata.cs0].
         *
         * This value is exposed as [naksha.model.XyzNs.cs0].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CS0 = "cs0"

        /**
         * Returns a new meta-column for [CS0].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cs0(): MetaColumn = MetaColumn(CS0)

        /**
         * The name of the virtual columns that stores the [first custom value][naksha.model.Metadata.cs1].
         *
         * This value is exposed as [naksha.model.XyzNs.cs1].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CS1 = "cs1"

        /**
         * Returns a new meta-column for [CS1].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cs1(): MetaColumn = MetaColumn(CS1)

        /**
         * The name of the virtual columns that stores the [first custom value][naksha.model.Metadata.cs2].
         *
         * This value is exposed as [naksha.model.XyzNs.cs2].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CS2 = "cs2"

        /**
         * Returns a new meta-column for [CS2].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cs2(): MetaColumn = MetaColumn(CS2)

        /**
         * The name of the virtual columns that stores the [first custom value][naksha.model.Metadata.cs3].
         *
         * This value is exposed as [naksha.model.XyzNs.cs3].
         *
         * Supported [query operations][AnyOp] are:
         * - [StringOp]
         * - [AnyOp.IS_NULL]
         * - [AnyOp.IS_NOT_NULL]
         * - [AnyOp.IS_ANY_OF]
         */
        const val CS3 = "cs3"

        /**
         * Returns a new meta-column for [CS3].
         * @return a new meta-column.
         */
        @JvmStatic
        @JsStatic
        fun cs3(): MetaColumn = MetaColumn(CS3)

        /**
         * The name of the virtual columns that stores the [feature][naksha.model.Tuple.feature].
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
         * The name of the virtual columns that stores the [geometry][naksha.model.Tuple.geo].
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
         * The name of the virtual columns that stores the [reference point][naksha.model.Tuple.referencePoint].
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
         * The name of the virtual columns that stores the [tags][naksha.model.Tuple.tags].
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
         * The name of the virtual columns that stores the [attachment][naksha.model.Tuple.attachment].
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

        private val NAME = NotNullProperty<MetaColumn, String>(String::class) { _, _ -> "" }
    }

    /**
     * The name of the field.
     */
    var name by NAME
}