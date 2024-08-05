@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.*
import naksha.model.NakshaContext
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.NakshaException
import naksha.model.objects.NakshaFeature
import naksha.model.Tuple
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * A write instruction for the storage.
 */
@JsExport
open class Write : AnyObject() {

    companion object Write_C {
        private val OP = NotNullEnum<Write, WriteOp>(WriteOp::class) { _, _ -> WriteOp.NULL }
        private val MAP_ID = NotNullProperty<Write, String>(String::class) { _, _ -> NakshaContext.currentContext().mapId }
        private val STRING = NotNullProperty<Write, String>(String::class) { _, _ -> "" }
        private val STRING_NULL = NullableProperty<Write, String>(String::class)
        private val FEATURE_NULL = NullableProperty<Write, NakshaFeature>(NakshaFeature::class)
        private val Tuple_NULL = NullableProperty<Write, Tuple>(Tuple::class)
        private val INT64_NULL = NullableProperty<Write, Int64>(Int64::class)
    }

    /**
     * The operation to perform.
     */
    var op by OP

    fun withOp(op: WriteOp): Write {
        this.op = op
        return this
    }

    /**
     * The map in which the collection is stored, if being an empty string, the default map is used.
     */
    var mapId by MAP_ID

    /**
     * The identifier of the collection into which to write.
     */
    var collectionId by STRING

    /**
     * The identifier of the target to modify.
     */
    var id by STRING_NULL

    /**
     * The version that should be updated.
     *
     * If not _null_, the operation is atomic, and expects that the existing HEAD row is in the given version.
     */
    var version by INT64_NULL

    /**
     * Tests if this write should be performed atomic.
     * @return _true_ if this write should be performed atomic.
     */
    fun isAtomic(): Boolean = version != null

    /**
     * The new feature to persist; if any.
     */
    var feature by FEATURE_NULL

    /**
     * The new row to persists; if any.
     */
    var row by Tuple_NULL

    /**
     * Returns `feature.id`, `row.meta.id`, or `id` in that order.
     * @return the of the feature or row.
     */
    fun featureId(): String? {
        val f = feature
        if (f != null) return f.id
        val r = row
        if (r != null) return r.meta.id
        return id
    }

    /**
     * Create a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the feature to create.
     */
    fun createFeature(map: String?, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.CREATE
        this.id = feature.id
        this.version = null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Update a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the new state of the feature.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     */
    fun updateFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.UPDATE
        this.id = feature.id
        this.version = if (atomic) feature.properties.xyz.version else null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Update or create a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the new state of the feature.
     */
    fun upsertFeature(map: String?, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.UPSERT
        this.id = feature.id
        this.version = null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Delete a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the feature to delete.
     * @param atomic if the operation should be performed atomic.
     */
    fun deleteFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = feature.id
        this.version = if (atomic) feature.properties.xyz.version else null
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Delete a feature by id.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param id the identifier of the object to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     */
    fun deleteFeatureById(map: String?, collectionId: String, id: String, version: Int64? = null): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = id
        this.version = version
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Create a Naksha collection.
     * @param map the map.
     * @param collection the collection to create.
     */
    fun createCollection(map: String?, collection: NakshaCollection): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.CREATE
        this.id = collection.id
        this.version = null
        this.feature = collection
        this.row = null
        return this
    }

    /**
     * Update a Naksha collection.
     * @param map the map.
     * @param collection the new state of the collection.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     */
    fun updateCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.UPDATE
        this.id = collection.id
        this.version = if (atomic) collection.properties.xyz.version else null
        this.feature = collection
        this.row = null
        return this
    }

    /**
     * Update or create a Naksha collection.
     * @param map the map.
     * @param collection the new state of the collection.
     */
    fun upsertCollection(map: String?, collection: NakshaCollection): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.UPSERT
        this.id = collection.id
        this.version = null
        this.feature = collection
        this.row = null
        return this
    }

    /**
     * Delete a Naksha collection.
     * @param map the map.
     * @param collection the collection to delete.
     * @param atomic if the operation should be performed atomic.
     */
    fun deleteCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.op = WriteOp.DELETE
        this.id = collection.id
        this.version = if (atomic) collection.properties.xyz.version else null
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Delete a collection.
     * @param map the map.
     * @param collectionId the identifier of the collection to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     */
    fun deleteCollectionById(map: String?, collectionId: String, version: Int64? = null): Write {
        this.mapId = map ?: NakshaContext.mapId()
        this.collectionId = VIRT_COLLECTIONS
        this.op = WriteOp.DELETE
        this.id = collectionId
        this.version = version
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Create a new row.
     * @param tuple the new row state.
     */
    fun createRow(tuple: Tuple): Write {
        this.mapId = tuple.mapId ?: throw NakshaException(MAP_NOT_FOUND, "Map of row '$tuple' not found")
        this.collectionId = tuple.collectionId ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection of row '$tuple' not found")
        this.op = WriteOp.CREATE
        this.id = tuple.meta.id
        this.version = null
        this.feature = null
        this.row = tuple
        return this
    }

    /**
     * Update a row.
     * @param tuple the new row state.
     * @param atomic if the operation should be performed atomic.
     */
    fun updateRow(tuple: Tuple, atomic: Boolean = false): Write {
        this.mapId = tuple.mapId ?: throw NakshaException(MAP_NOT_FOUND, "Map of row '$tuple' not found")
        this.collectionId = tuple.collectionId ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection of row '$tuple' not found")
        this.op = WriteOp.UPDATE
        this.id = tuple.meta.id
        this.version = if (atomic) tuple.meta.version else null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Update or insert a row.
     * @param tuple the new row state.
     */
    fun upsertRow(tuple: Tuple): Write {
        this.mapId = tuple.mapId ?: throw NakshaException(MAP_NOT_FOUND, "Map of row '$tuple' not found")
        this.collectionId = tuple.collectionId ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection of row '$tuple' not found")
        this.op = WriteOp.UPSERT
        this.id = tuple.meta.id
        this.version = null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Delete a row.
     * @param tuple the row state to delete.
     * @param atomic if the operation should be performed atomic.
     */
    fun deleteRow(tuple: Tuple, atomic: Boolean = false): Write {
        this.mapId = tuple.mapId ?: throw NakshaException(MAP_NOT_FOUND, "Map of row '$tuple' not found")
        this.collectionId = tuple.collectionId ?: throw NakshaException(COLLECTION_NOT_FOUND, "Collection of row '$tuple' not found")
        this.op = WriteOp.DELETE
        this.id = tuple.meta.id
        this.version = if (atomic) tuple.meta.version else null
        this.feature = null
        this.row = null
        return this
    }

}