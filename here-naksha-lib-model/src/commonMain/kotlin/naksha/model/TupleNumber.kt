@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.Platform
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int32
import naksha.base.PlatformDataViewApi.PlatformDataViewApiCompanion.dataview_set_int64
import naksha.base.toInt64
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The in-memory representation of the unique [Tuple] identifier, being a 224-bit value, persisting out of the storage-number, map-number, collection-number, [transaction-number][Version], partition-number, and the local unique identifier.
 *
 * The tuple-number is stringified into:
 * ```
 * {storage-id}:{map-id}:{collection-id}:{partition-id}:{year}:{month}:{day}:{seq}:{uid}
 * ```
 *
 * - There are no two [tuples][Tuple] with the same [tuple-number][TupleNumber]; world-wide.
 * @since 3.0.0
 */
@JsExport
data class TupleNumber(
    /**
     * The storage-number, uniquely identifies the storage where the tuple is stored.
     * @since 3.0.0
     */
    @JvmField val storageNumber: Int64,

    /**
     * The map-number of the map in which the tuple is stored within the storage.
     * @since 3.0.0
     */
    @JvmField val mapNumber: Int,

    /**
     * The collection-number of the collection in which the tuple is stored within the storage.
     * @since 3.0.0
     */
    @JvmField val collectionNumber: Int,

    /**
     * The partition-number, a value between 0 and 255.
     * @since 3.0.0
     */
    @JvmField val partitionNumber: Int,

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
) : Comparable<TupleNumber> {

    /**
     * The transaction-number.
     * @since 3.0.0
     */
    val txn: Int64
        get() = version.txn

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
     * Return the row identifier as string.
     * @return `{storage}:{map}:{collection}:{partition}:{year}:{month}:{day}:{seq}:{uid}`
     * @since 3.0.0
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) {
            _string = "${storageNumber}:${mapNumber}:${collectionNumber}:${partitionNumber}:$version:$uid"
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
     * Encode this [tuple-number][TupleNumber] into its 96-bit (_12-byte_) binary encoding, which does only store [version], [partitionNumber], and [uid]. This variant is used in storage systems like for example Postgres, specifically in [MetaColumn.TUPLE_NUMBER][naksha.model.request.query.MetaColumn.TUPLE_NUMBER].
     * @return the 96-bit binary encoded [tuple-number][TupleNumber].
     * @since 3.0.0
     * @see [naksha.model.request.query.MetaColumn.TUPLE_NUMBER]
     * @see [naksha.model.request.query.MetaColumn.PREV_TUPLE_NUMBER]
     * @see [naksha.model.request.query.MetaColumn.BASE_TUPLE_NUMBER]
     */
    fun toByteArray(): ByteArray {
        val byteArray = ByteArray(12)
        val view = Platform.newDataView(byteArray)
        dataview_set_int64(view, 0, (version.txn shl 8) or partitionNumber.toInt64())
        dataview_set_int32(view, 8, uid)
        return byteArray
    }

    companion object TupleNumber_C {
        /**
         * The _HEAD_ [TupleNumber], to be used when a [tuple-number][TupleNumber] is not yet available. This happens for various reasons, for example when a [Tuple] is created in the client at runtime, and not yet persisted in any storage, therefore does not yet have a tuple-number.
         * @since 3.0.0
         * @see [IWriteSession.newTupleNumber]
         */
        val HEAD = TupleNumber(Int64(0), 0, 0, 0, Version.HEAD, 0)
    }
}