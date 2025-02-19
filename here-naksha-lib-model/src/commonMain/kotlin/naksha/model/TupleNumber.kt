@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The in-memory representation of the unique [Tuple] identifier.
 *
 * The full qualified [Tuple] identifier is a 288-bit value _(36 byte)_, persisting out of the storage-number, map-number, collection-number, feature-number, [transaction-number][Version], and the local unique identifier.
 *
 * The tuple-number is stringified into:
 * ```
 * {storage-number}:{map-number}:{collection-number}:{feature-number}:{year}:{month}:{day}:{seq}:{uid}
 * ```
 *
 * - There are no two [tuples][Tuple] with the same [tuple-number][TupleNumber]; world-wide.
 * @since 3.0
 */
@JsExport
data class TupleNumber(
    /**
     * The storage-number, uniquely identifies the storage where the tuple is stored.
     * @since 3.0
     */
    @JvmField val storageNumber: Int64,

    /**
     * The map-number of the map in which the tuple is stored within the storage.
     * @since 3.0.0
     */
    @JvmField val mapNumber: Int,

    /**
     * The collection-number of the collection in which the tuple is stored within the storage.
     * @since 3.0
     */
    @JvmField val collectionNumber: Int,

    /**
     * The feature-number.
     * @since 3.0
     * @see [Naksha.featureNumber]
     */
    @JvmField val featureNumber: Int64,

    /**
     * The version _(transaction)_ of which the [Tuple] is part of.
     * @since 3.0
     * @see [Version.HEAD]
     */
    @JvmField val version: Version,

    /**
     * The unique identifier within the version _(transaction)_.
     * @since 3.0
     */
    @JvmField val uid: Int,
) : Comparable<TupleNumber> {

    /**
     * The transaction-number.
     * @since 3.0
     */
    val txn: Int64
        get() = version.txn

    /**
     * The partition-number of the [Tuple], a value between `0` and `65536` _(exclusive)_.
     * @since 3.0
     */
    val partitionNumber: Int
        get() = featureNumber.toInt() and 0xffff

    /**
     * Calculates the partition-index where this [Tuple] will be located.
     *
     * If the given partitions are less than `2`, the method always returns `0`, if the number is bigger than `65536` the result will be mapped back into the range between `0` and `65536` _(exclusive)_.
     * @param partitions the number of partitions
     * @return the partition-index, a value between `0` and `partitions - 1`, maximal `65535`
     * @since 3.0
     */
    fun partitionIndex(partitions: Int): Int = if (partitions <= 1) 0 else (partitionNumber % partitions) and 0xffff

    override fun hashCode(): Int = version.hashCode() xor uid

    override fun compareTo(other: TupleNumber): Int {
        val i64_diff = storageNumber - other.storageNumber
        if (i64_diff < 0) return -1
        if (i64_diff > 1) return 1
        var i32_diff = mapNumber - other.mapNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = collectionNumber - other.collectionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = partitionNumber - other.partitionNumber
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = version.compareTo(other.version)
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = uid - other.uid
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TupleNumber
            && storageNumber == other.storageNumber
            && mapNumber == other.mapNumber
            && collectionNumber == other.collectionNumber
            && version.txn == other.version.txn
            && partitionNumber == other.partitionNumber
            && uid == other.uid
    }

    private lateinit var _string: String

    /**
     * Return the [TupleNumber] as string.
     * @return `{storage-number}:{map-number}:{collection-number}:{feature-number}:{year}:{month}:{day}:{seq}:{uid}`
     * @since 3.0.0
     * @see [fromString]
     * @see [toUrn]
     * @see [fromUrn]
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            _string = "$storageNumber:$mapNumber:$collectionNumber:$featureNumber:$version:$uid"
        }
        return _string
    }

    /**
     * Returns an alternative tuple-number, if this tuple-number causes a conflict in the auto-generated `feature-number`.
     *
     * This only happens when new features are created, and another feature, with a different **feature-id**, results in the same **feature-number**, because the lower 63-bit of their [MD5](https://en.wikipedia.org/wiki/MD5) hash collide. It deterministically calculates a new alternative **feature-number** and creates a new [TupleNumber] from it, that has just a new feature-number (no other changes). When this fails gain due to another conflict, the step can be repeated using this same function on the new, failing again, [TupleNumber] to generated yet another one, aso.
     *
     * ### Warning
     * The function returns deterministically always the same [TupleNumber], therefore, for more derivations it is necessary to call this function on the returned [TupleNumber] number, not again on the origin!
     *
     * - Throws [ILLEGAL_STATE] if the [featureNumber] is greater than `-1`, so not auto-generated.
     * @return an alternative[TupleNumber]
     * @since 3.0
     */
    fun resolveFeatureNumberConflict(): TupleNumber {
        val fn = this.featureNumber
        if (fn >= 0) throw NakshaException(ILLEGAL_STATE, "The feature-number is not auto-generated, failed to calculate alternative")
        val new_fn = Naksha.alternativeInt64(fn)
        return TupleNumber(storageNumber, mapNumber, collectionNumber, new_fn, version, uid)
    }

    private var _urn: String? = null

    /**
     * Convert this [TupleNumber] into a [URN](https://datatracker.ietf.org/doc/html/rfc8141).
     * @return the [URN](https://datatracker.ietf.org/doc/html/rfc8141) that describes this state world-wide uniquely.
     * @since 3.0
     */
    fun toUrn(): String {
        var urn = _urn
        if (urn == null) {
            urn = "urn:naksha:tn:${toString()}"
            _urn = urn
        }
        return urn
    }

    /**
     * Encode this [tuple-number][TupleNumber] into its binary representation.
     *
     * @param variant the [TupleNumberVariant] to use for the encoding.
     * @return the binary encoded [tuple-number][TupleNumber].
     * @since 3.0
     * @see [naksha.model.request.query.MetaColumn.TUPLE_NUMBER]
     * @see [naksha.model.request.query.MetaColumn.PREV_TUPLE_NUMBER]
     * @see [naksha.model.request.query.MetaColumn.BASE_TUPLE_NUMBER]
     */
    fun toByteArray(variant: TupleNumberVariant): ByteArray {
        val byteArray = ByteArray(variant.encodingBytes)
        val view = Platform.newDataView(byteArray)
        var offset = 0
        if (variant.encodeStorageNumber()) {
            dataview_set_int64(view, offset, storageNumber)
            offset += 8
        }
        if (variant.encodeMapNumber()) {
            dataview_set_int32(view, offset, mapNumber)
            offset += 4
        }
        if (variant.encodeCollectionNumber()) {
            dataview_set_int32(view, offset, collectionNumber)
            offset += 4
        }
        if (variant.encodeFeatureNumber()) {
            dataview_set_int64(view, offset, featureNumber)
            offset += 8
        }
        dataview_set_int64(view, offset, version.txn)
        dataview_set_int32(view, offset + 8, uid)
        return byteArray
    }

    companion object TupleNumber_C {
        internal const val STORAGE_NUMBER = 0
        internal const val MAP_NUMBER = 1
        internal const val COLLECTION_NUMBER = 2
        internal const val FEATURE_NUMBER = 3
        internal const val YEAR = 4
        internal const val MONTH = 5
        internal const val DAY = 6
        internal const val SEQ = 7
        internal const val UID = 8
        internal const val ALL_PARTS = 9

        internal const val URN = 0
        internal const val NAKSHA = 1
        internal const val TN = 2
        internal const val URN_STORAGE_NUMBER_OFFSET = 3
        internal const val URN_PARTS = ALL_PARTS + 3

        /**
         * The _HEAD_ [TupleNumber], to be used when a [tuple-number][TupleNumber] is not yet available.
         *
         * This happens for various reasons, for example when a [Tuple] is created in the client at runtime, and not yet persisted in any storage, therefore does not yet have a valid tuple-number.
         * @since 3.0
         * @see [IWriteSession.newTupleNumber]
         */
        val HEAD = TupleNumber(Int64(0), 0, 0, Int64(0), Version.HEAD, 0)

        /**
         * Restore a [TupleNumber] from the stringified version.
         * @param string the string generated via [toString]
         * @return the deserialized [TupleNumber].
         */
        @JsStatic
        @JvmStatic
        fun fromString(string: String): TupleNumber {
            val parts = string.split(':')
            if (parts.size != ALL_PARTS) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number string, require $ALL_PARTS parts: $string")
            }
            return fromParts(parts)
        }

        /**
         * Restore a [TupleNumber] from the given [URN](https://datatracker.ietf.org/doc/html/rfc8141), generated via [toUrn].
         * @param urn the [URN](https://datatracker.ietf.org/doc/html/rfc8141) from which to deserialize the [TupleNumber].
         * @return the deserialized [TupleNumber].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromUrn(urn: String): TupleNumber {
            val parts = urn.split(':')
            if (parts.size != URN_PARTS
                || parts[URN] != "urn"
                || parts[NAKSHA] != "naksha"
                || parts[TN] != "tn") {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number URN: $urn")
            }
            return fromParts(parts, URN_STORAGE_NUMBER_OFFSET)
        }

        /**
         * Deserialize a [TupleNumber] from the given parts array.
         *
         * The given array should contain, in order as **decimal string**:
         * - `storage-number` _(64-bit integer)_
         * - `map-number` _(32-bit integer)_
         * - `collection-number` _(32-bit integer)_
         * - `feature-number` _(64-bit integer)_
         * - `year` _(15-bit integer)_
         * - `month` _(4-bit integer)_
         * - `day` _(5-bit integer)_
         * - `sequence` _(32-bit integer)_
         * - `uid` _(32-bit integer)_
         * @param parts the string parts of the tuple-number.
         * @param offset the index in the given list where the `storage-number` is located, defaults to `0`.
         * @return the deserialized [TupleNumber].
         * @since 3.0
         */
        @JsStatic
        @JvmStatic
        fun fromParts(parts: List<String>, offset:Int = 0): TupleNumber {
            if (offset < 0 || (offset + ALL_PARTS) > parts.size) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Invalid tuple-number: $parts")
            }
            val storageNumber = Int64(parts[offset + STORAGE_NUMBER].toLong(10))
            val mapNumber = parts[offset + MAP_NUMBER].toInt(10)
            val colNumber = parts[offset + COLLECTION_NUMBER].toInt(10)
            val featureNumber = Int64(parts[offset + FEATURE_NUMBER].toLong(10))
            val year = parts[YEAR].toInt(10)
            val month = parts[MONTH].toInt(10)
            val day = parts[DAY].toInt(10)
            val seq = Int64(parts[SEQ].toLong())
            val version = Version.of(year, month, day, seq)
            val uid = parts[UID].toInt()
            return TupleNumber(storageNumber, mapNumber, colNumber, featureNumber, version, uid)
        }
    }
}