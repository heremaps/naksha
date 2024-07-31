@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * A row identifier, being a 128-bit value, persisting out of the [version][Version], a 32-bit integer unique identifies within the version, and the flags which contain the partition number.
 *
 * The `uid` allows to order rows within a version, and to process the changes being part of a transaction in a deterministic order. The row identifier is stringified into: `{year}:{month}:{day}:{seq}:{uid}:{flags}`.
 *
 * Within a given storage, there are no two rows with the same [RowId], not even in different maps or collections.
 *
 * @property version the version (transaction) in which the row is located.
 * @property uid the unique identifier within the transaction.
 * @property flags the flags of the row.
 */
@JsExport
data class RowId(@JvmField val version: Version, @JvmField val uid: Int, @JvmField val flags: Flags) : Comparable<RowId> {

    override fun hashCode(): Int = version.hashCode() xor uid

    override fun compareTo(other: RowId): Int {
        var diff = version.compareTo(other.version)
        if (diff < 0) return -1
        if (diff > 1) return 1
        diff = uid - other.uid
        return if (diff == 0) 0 else if (diff < 0) -1 else 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is RowId && version.txn == other.version.txn && uid == other.uid) return true
        if (other is RowRef && version.txn == Int64(other.txn) && uid == other.uid) return true
        return false
    }

    private lateinit var _string: String

    /**
     * Return the row identifier as string.
     * @return `{year}:{month}:{day}:{seq}:{uid}`
     */
    override fun toString(): String {
        if (!this::_string.isInitialized) _string = "$version:$uid:$flags"
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
        Guid(storageId, map, collectionId, featureId, this)
}