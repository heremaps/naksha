@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.*
import naksha.model.NakshaContext
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic

/**
 * A write instruction for the storage.
 * @since 3.0.0
 */
@JsExport
open class Write : AnyObject() {

    companion object Write_C {
        /**
         * The method to order writes via [MutableList.sortedWith] by:
         * - `map-id`
         * - `collection-id`
         * - `partition-number`
         * - `op` (CREATE, UPSERT, UPDATE, DELETE, PURGE, UNKNOWN)
         * - `feature-id`
         *
         * Example:
         * ```kotlin
         * val writes = WriteList()
         * ... add writes
         * writes.sortedWith(Write::sortCompare)
         * ```
         * It is very important that all code that modifies features, use the same ordering.
         *
         * **If writes are not order like this, this will lead to row-level locking in wrong order, causing deadlocks in the database!**
         * @since 3.0.0
         */
        @JvmStatic
        @JsStatic
        fun sortCompare(a: Write?, b: Write?): Int {
            if (a === b) return 0
            if (b == null) return -1
            if (a == null) return 1

            // Sorts by map-id, collection-id, partition-number, operation, feature-id
            val a_mapId = a.mapId
            val b_mapId = b.mapId
            val map_diff = a_mapId.compareTo(b_mapId)
            return if (map_diff == 0) {
                val a_colId = if (a.collectionId == VIRT_COLLECTIONS) a.featureId ?: "" else a.collectionId
                val b_colId = if (b.collectionId == VIRT_COLLECTIONS) b.featureId ?: "" else b.collectionId
                val col_diff = a_colId.compareTo(b_colId)
                if (col_diff == 0) {
                    val a_featureId = a.featureId ?: ""
                    val b_featureId = b.featureId ?: ""
                    val a_part = partitionNumber(a_featureId)
                    val b_part = partitionNumber(b_featureId)
                    val part_diff = a_part.compareTo(b_part)
                    if (part_diff == 0) {
                        val id_diff = a_featureId.compareTo(b_featureId)
                        if (id_diff == 0) {
                            return a.op.compareTo(b.op)
                        } else id_diff
                    } else part_diff
                } else col_diff
            } else map_diff
        }

        private val OP = NotNullEnum<Write, WriteOp>(WriteOp::class) { _, _ -> WriteOp.NULL }
        private val MAP_ID = NotNullProperty<Write, String>(String::class) { _, _ -> NakshaContext.currentContext().mapId }
        private val STRING = NotNullProperty<Write, String>(String::class) { _, _ -> "" }
        private val STRING_NULL = NullableProperty<Write, String>(String::class)
        private val FEATURE_NULL = NullableProperty<Write, NakshaFeature>(NakshaFeature::class)
        private val INT64_NULL = NullableProperty<Write, Int64>(Int64::class)
        private val BYTE_ARRAY_NULL = NullableProperty<Write, ByteArray>(ByteArray::class)
    }

    /**
     * The operation to perform.
     * @since 3.0.0
     */
    var op by OP

    /**
     * The map in which the collection is stored, if being an empty string, the default map is used.
     * @since 3.0.0
     */
    var mapId by MAP_ID

    /**
     * The identifier of the collection into which to write.
     * @since 3.0.0
     */
    var collectionId by STRING

    /**
     * The identifier of the target to modify.
     * @since 3.0.0
     */
    var id by STRING_NULL

    /**
     * The version that should be updated.
     *
     * If not _null_, the operation is atomic, and expects that the existing HEAD row is in the given version.
     * @since 3.0.0
     */
    var version by INT64_NULL

    /**
     * Tests if this write should be performed atomic.
     * @return _true_ if this write should be performed atomic.
     * @since 3.0.0
     */
    fun isAtomic(): Boolean = version != null

    /**
     * The new feature to persist; if any.
     * @since 3.0.0
     */
    var feature by FEATURE_NULL

    /**
     * Returns `feature.id` or `id` in that order.
     * @since 3.0.0
     */
    val featureId: String?
        get() {
            val f = feature
            if (f != null) return f.id
            return id
        }

    /**
     * Arbitrary attachment to be added.
     * @since 3.0.0
     */
    var attachment by BYTE_ARRAY_NULL

    /**
     * Create a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the feature to create.
     * @since 3.0.0
     */
    fun createFeature(map: String?, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.CREATE
        this.id = feature.id
        this.version = null
        this.feature = feature
        return this
    }

    /**
     * Update a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the new state of the feature.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0.0
     */
    fun updateFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.UPDATE
        this.id = feature.id
        this.version = if (atomic) feature.properties.xyz.txn else null
        this.feature = feature
        return this
    }

    /**
     * Update or create a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the new state of the feature.
     * @since 3.0.0
     */
    fun upsertFeature(map: String?, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.UPSERT
        this.id = feature.id
        this.version = null
        this.feature = feature
        return this
    }

    /**
     * Delete a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the feature to delete.
     * @param atomic if the operation should be performed atomic.
     * @since 3.0.0
     */
    fun deleteFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = feature.id
        this.version = if (atomic) feature.properties.xyz.txn else null
        this.feature = null
        return this
    }

    /**
     * Delete a feature by id.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param id the identifier of the object to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     * @since 3.0.0
     */
    fun deleteFeatureById(map: String?, collectionId: String, id: String, version: Int64? = null): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = id
        this.version = version
        this.feature = null
        return this
    }

    /**
     * Create a Naksha collection.
     * @param map the map.
     * @param collection the collection to create.
     * @since 3.0.0
     */
    fun createCollection(map: String?, collection: NakshaCollection): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.CREATE
        this.id = collection.id
        this.version = null
        this.feature = collection
        return this
    }

    /**
     * Update a Naksha collection.
     * @param map the map.
     * @param collection the new state of the collection.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0.0
     */
    fun updateCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.UPDATE
        this.id = collection.id
        this.version = if (atomic) collection.properties.xyz.txn else null
        this.feature = collection
        return this
    }

    /**
     * Update or create a Naksha collection.
     * @param map the map.
     * @param collection the new state of the collection.
     * @since 3.0.0
     */
    fun upsertCollection(map: String?, collection: NakshaCollection): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.UPSERT
        this.id = collection.id
        this.version = null
        this.feature = collection
        return this
    }

    /**
     * Delete a Naksha collection.
     * @param map the map.
     * @param collection the collection to delete.
     * @param atomic if the operation should be performed atomic.
     * @since 3.0.0
     */
    fun deleteCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.op = WriteOp.DELETE
        this.id = collection.id
        this.version = if (atomic) collection.properties.xyz.txn else null
        this.feature = null
        return this
    }

    /**
     * Delete a collection.
     * @param map the map.
     * @param collectionId the identifier of the collection to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     * @since 3.0.0
     */
    fun deleteCollectionById(map: String?, collectionId: String, version: Int64? = null): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.DELETE
        this.id = collectionId
        this.version = version
        this.feature = null
        return this
    }

}