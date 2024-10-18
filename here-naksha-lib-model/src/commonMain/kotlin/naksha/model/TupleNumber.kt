@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A unique [Tuple] identifier, being a 256-bit value, persisting out of the storage-number (a unique identifier of the storage in which the tuple is located), the [store-number][StoreNumber] (where the [tuple][Tuple] is physically located within the storage), the [version][Version] (the transaction in which it was created), a 32-bit [version][Version] local unique identifier, and eventually the [flags][Flags] of the feature, which encode mainly the [action][Action].
 *
 * The tuple-number is stringified into:
 * ```
 * {storage}:{map}:{collection}:{partition}:{year}:{month}:{day}:{seq}:{uid}:{flags}
 * ```
 *
 * - There are no two [tuples][Tuple] with the same [tuple-number][TupleNumber]; world-wide.
 * @since 3.0.0
 */
@JsExport
data class TupleNumber(
    /**
     * The storage-number uniquely identifies the storage of where the tuple is stored.
     * @since 3.0.0
     */
    @JvmField val storageNumber: Int64,

    /**
     * The store-number of where the tuple is stored within a certain storage (combination of map-, collection- and partition-number).
     * @since 3.0.0
     */
    @JvmField val storeNumber: StoreNumber,

    /**
     * The version (transaction-number) in which the row is located.
     * @since 3.0.0
     */
    @JvmField val version: Version,

    /**
     * The unique identifier within the version (transaction).
     * @since 3.0.0
     */
    @JvmField val uid: Int,

    /**
     * The flags of the feature; not part of equality checks.
     * @since 3.0.0
     */
    @JvmField val flags: Flags
) : Comparable<TupleNumber> {
    /**
     * Returns the map-number of the map in which the tuple is stored.
     * @return the map-number.
     * @since 3.0.0
     */
    fun mapNumber(): Int = storeNumber.mapNumber()

    /**
     * Returns the collection-number of the collection in which the tuple is stored.
     * @return the collection-number.
     * @since 3.0.0
     */
    fun collectionNumber(): Int = storeNumber.collectionNumber()

    /**
     * Returns the partition-number of the partition in which the tuple is stored.
     * @return the partition-number.
     * @since 3.0.0
     */
    fun partitionNumber(): Int = storeNumber.partitionNumber()

    override fun hashCode(): Int = version.hashCode() xor uid

    override fun compareTo(other: TupleNumber): Int {
        var i64_diff = storageNumber - other.storageNumber
        if (i64_diff < 0) return -1
        if (i64_diff > 1) return 1
        i64_diff = storeNumber.compact() - other.storeNumber.compact()
        if (i64_diff < 0) return -1
        if (i64_diff > 1) return 1
        var i32_diff = version.compareTo(other.version)
        if (i32_diff < 0) return -1
        if (i32_diff > 1) return 1
        i32_diff = uid - other.uid
        return if (i32_diff == 0) 0 else if (i32_diff < 0) -1 else 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TupleNumber
            && storageNumber == other.storageNumber
            && storeNumber == other.storeNumber
            && version.txn == other.version.txn
            && uid == other.uid
    }

    private lateinit var _string: String

    /**
     * Return the row identifier as string.
     * @return `{storage-number}:{map-number}:{collection-number}:{partition-number}:{year}:{month}:{day}:{seq}:{uid}:{flags}`
     * @since 3.0.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            val sn = storeNumber
            _string = "${storageNumber}:${sn.mapNumber()}:${sn.collectionNumber()}:${sn.partitionNumber()}:$version:$uid:$flags"
        }
        return _string
    }

    /**
     * Create a [Guid] for a specific feature state.
     * @param featureId the feature-identifier.
     * @return the [Guid] that describes this state world-wide uniquely.
     * @since 3.0.0
     */
    fun toGuid(featureId: String): Guid = Guid(featureId, this)

    /**
     * Encode this [tuple-number][TupleNumber] into its 256-bit binary encoding; this binary encoding is compatible with the [TupleNumberByteArray].
     * @return the 256-bit binary encoded [tuple-number][TupleNumber].
     * @since 3.0.0
     */
    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(32)
        val view = Platform.newDataView(byteArray)
        dataview_set_int64(view, 0, storageNumber) // lead-in
        dataview_set_int64(view, 8, storeNumber)
        dataview_set_int64(view, 16, version.txn)
        dataview_set_int32(view, 24, uid)
        dataview_set_int32(view, 28, flags)
        return byteArray
    }

    /**
     * Convert this tuple into its 160-bit binary encoding; the only use-case for this method is to use the tuple-number to query the database.
     *
     * @return the 160-bit binary encoded tuple-number.
     * @since 3.0.0
     */
    fun toQuery(): ByteArray {
        val byteArray = ByteArray(20)
        val view = Platform.newDataView(byteArray)
        dataview_set_int64(view, 0, storeNumber)
        dataview_set_int64(view, 8, version.txn)
        dataview_set_int32(view, 16, uid)
        return byteArray
    }

    companion object TupleNumber_C {
        /**
         * The undefined [TupleNumber], to be used when a [tuple-number][TupleNumber] is invalid. This happens for various reasons, for example when a [Tuple] is created in the client at runtime, and not yet located in any storage, it does not yet have a tuple-number.
         */
        val UNDEFINED = TupleNumber(Int64(0), StoreNumber(), Version(0L), 0, 0)

        /**
         * Decodes a binary encoded [tuple-number][TupleNumber] from the given byte-array.
         * @param byteArray the byte-array of encoded tuple-numbers.
         * @param index the index, not the offset, only useful when a [tuple-number byte-array][TupleNumberByteArray] is given.
         * @return the decoded [TupleNumber] or [TupleNumber.UNDEFINED], if no valid one can be read.
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun fromByteArray(byteArray: ByteArray, index: Int = 0): TupleNumber {
            if (byteArray.size < 32) return UNDEFINED
            return TupleNumberByteArray(byteArray)[index] ?: UNDEFINED
        }
    }
}