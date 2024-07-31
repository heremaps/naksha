@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.*
import naksha.model.NakshaContext
import naksha.model.Naksha
import naksha.model.objects.NakshaFeature
import naksha.model.Row
import naksha.model.RowRef
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * A write instruction for the storage.
 */
@JsExport
open class Write : AnyObject() {

    companion object Write_C {
        private val OP = NotNullEnum<Write, WriteOp>(WriteOp::class) { _, _ -> WriteOp.NULL }
        private val MAP = NotNullProperty<Write, String>(String::class) { _, _ -> NakshaContext.currentContext().map }
        private val STRING = NotNullProperty<Write, String>(String::class) { _, _ -> "" }
        private val STRING_NULL = NullableProperty<Write, String>(String::class)
        private val ROW_REF_NULL = NullableProperty<Write, RowRef>(RowRef::class)
        private val FEATURE_NULL = NullableProperty<Write, NakshaFeature>(NakshaFeature::class)
        private val ROW_NULL = NullableProperty<Write, Row>(Row::class)
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
    var map by MAP

    /**
     * The identifier of the collection into which to write.
     */
    var collectionId by STRING

    /**
     * The identifier of the target to modify.
     */
    var id by STRING_NULL

    /**
     * The row reference of the target to modify.
     */
    var rowRef by ROW_REF_NULL

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
    var row by ROW_NULL

    /**
     * Create a Naksha feature.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param feature the feature to create.
     */
    fun createFeature(map: String?, collectionId: String, feature: NakshaFeature) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = collectionId
        this.op = WriteOp.CREATE
        this.id = feature.id
        this.rowRef = null
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
    fun updateFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = collectionId
        this.op = WriteOp.UPDATE
        this.id = feature.id
        this.rowRef = null
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
    fun upsertFeature(map: String?, collectionId: String, feature: NakshaFeature) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = collectionId
        this.op = WriteOp.UPSERT
        this.id = feature.id
        this.rowRef = null
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
    fun deleteFeature(map: String?, collectionId: String, feature: NakshaFeature, atomic: Boolean = false) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = feature.id
        this.rowRef = null
        this.version = if (atomic) feature.properties.xyz.version else null
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Create a Naksha collection.
     * @param map the map.
     * @param collection the collection to create.
     */
    fun createCollection(map: String?, collection: NakshaCollection) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = Naksha.VIRT_COLLECTIONS
        this.op = WriteOp.CREATE
        this.id = collection.id
        this.rowRef = null
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
    fun updateCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = Naksha.VIRT_COLLECTIONS
        this.op = WriteOp.UPDATE
        this.id = collection.id
        this.rowRef = null
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
    fun upsertCollection(map: String?, collection: NakshaCollection) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = Naksha.VIRT_COLLECTIONS
        this.op = WriteOp.UPSERT
        this.id = collection.id
        this.rowRef = null
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
    fun deleteCollection(map: String?, collection: NakshaCollection, atomic: Boolean = false) : Write {
        this.map = map ?: NakshaContext.map()
        this.op = WriteOp.DELETE
        this.id = collection.id
        this.rowRef = null
        this.version = if (atomic) collection.properties.xyz.version else null
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Create a new row.
     * @param row the new row state.
     */
    fun createRow(row: Row) : Write {
        this.map = row.map
        this.collectionId = row.collectionId
        this.op = WriteOp.CREATE
        this.id = row.meta.id
        this.rowRef = null
        this.version = null
        this.feature = null
        this.row = row
        return this
    }

    /**
     * Update a row.
     * @param row the new row state.
     * @param atomic if the operation should be performed atomic.
     */
    fun updateRow(row: Row, atomic: Boolean = false) : Write {
        this.map = row.map
        this.collectionId = row.collectionId
        this.op = WriteOp.UPDATE
        this.id = row.meta.id
        this.rowRef = null
        this.version = if (atomic) row.meta.version else null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Update or insert a row.
     * @param row the new row state.
     */
    fun upsertRow(row: Row) : Write {
        this.map = row.map
        this.collectionId = row.collectionId
        this.op = WriteOp.UPSERT
        this.id = row.meta.id
        this.rowRef = null
        this.version = null
        this.feature = feature
        this.row = null
        return this
    }

    /**
     * Delete a row.
     * @param row the row state to delete.
     * @param atomic if the operation should be performed atomic.
     */
    fun deleteRow(row: Row, atomic: Boolean = false) : Write {
        this.map = row.map
        this.collectionId = row.collectionId
        this.op = WriteOp.DELETE
        this.id = row.meta.id
        this.rowRef = null
        this.version = if (atomic) row.meta.version else null
        this.feature = null
        this.row = null
        return this
    }

    /**
     * Delete an object by id.
     * @param map the map.
     * @param collectionId the identifier of the collection to act upon.
     * @param id the identifier of the object to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     */
    fun delete(map: String?, collectionId: String, id: String, version: Int64? = null) : Write {
        this.map = map ?: NakshaContext.map()
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = id
        this.rowRef = null
        this.version = version
        this.feature = null
        this.row = null
        return this
    }
}