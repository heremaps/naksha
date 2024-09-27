@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_get_int64
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A [tuple][Tuple] identifier, being a 160-bit value, persisting out of the [store-number][StoreNumber] (where the [tuple][Tuple] is physically located), the [version][Version] (the transaction in which it was created), and a 32-bit integer unique identifies within the [version][Version].
 *
 * The tuple-number is stringified into: `{map-number}:{collection-number}:{year}:{month}:{day}:{seq}:{uid}:{partition-number}`.
 *
 * Within a given storage, there are no two [tuples][Tuple] with the same [TupleNumber], not even in different maps or collections.
 */
@JsExport
data class TupleNumber(
    /**
     * The store-number (combination of map-, collection- and partition-number) of where the row is stored.
     */
    @JvmField val storeNumber: StoreNumber,

    /**
     * The version (transaction-number) in which the row is located.
     */
    @JvmField val version: Version,

    /**
     * The unique identifier within the version (transaction).
     */
    @JvmField val uid: Int
) : Comparable<TupleNumber> {

    /**
     * Returns the map-number of the map from where the row is.
     * @return the map-number of the map from where the row is.
     */
    fun mapNumber(): Int = storeNumber.mapNumber()

    /**
     * Returns the collection-number of the collection from where the row is.
     * @return the collection-number of the collection from where the row is.
     */
    fun collectionNumber(): Int64 = storeNumber.collectionNumber()

    override fun hashCode(): Int = version.hashCode() xor uid

    override fun compareTo(other: TupleNumber): Int {
        val i64_diff = storeNumber.compact() - other.storeNumber.compact()
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
            && storeNumber.compact() == other.storeNumber.compact()
            && version.txn == other.version.txn
            && uid == other.uid
    }

    private lateinit var _string: String

    /**
     * Return the row identifier as string.
     * @return `{map-number}:{collection-number}:{year}:{month}:{day}:{seq}:{uid}:{partition-number}`
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            _string = "${storeNumber.mapNumber()}:${storeNumber.collectionNumber()}:$version:$uid:${storeNumber.partitionNumber()}"
        }
        return _string
    }

    /**
     * Create a [Guid] for a specific feature state.
     * @param storageId the storage-identifier of the storage in which this version is stored.
     * @param map the map in which this version is stored.
     * @param collectionId the collection-identifier in which the feature is stored.
     * @param featureId the feature-identifier.
     * @return the [Guid] that describes this state world-wide uniquely.
     */
    fun toGuid(storageId: String, map: String, collectionId: String, featureId: String): Guid =
        Guid(storageId, map, collectionId, featureId, version, uid)

    /**
     * Convert this tuple into its binary form.
     * @return the binary encoded tuple-number.
     */
    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(20)
        val view = Platform.newDataView(byteArray)
        dataview_set_int64(view, 0, storeNumber)
        dataview_set_int64(view, 8, version.txn)
        dataview_set_int32(view, 16, uid)
        return byteArray
    }

    companion object TupleNumberCompanion {
        /**
         * Convert byteArray form of tuple into [TupleNumber]
         * @param byteArray tupleNumber in byteArray.
         * @param offset the offset to read from.
         * @return the decoded [TupleNumber].
         */
        fun fromByteArray(byteArray: ByteArray, offset: Int=0): TupleNumber {
            val view = Platform.newDataView(byteArray)
            return TupleNumber(
                dataview_get_int64(view, offset),
                Version(dataview_get_int64(view, offset+8)),
                dataview_get_int32(view, offset+16),
            )
        }
    }
}