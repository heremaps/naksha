@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.*
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.BOOKS_COL_ID
import naksha.base.Id.IdCompanion.CATALOGS_COL_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_ID
import naksha.base.Platform.PlatformCompanion.FAL
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaCatalog
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A write instruction for the storage.
 *
 * Its recommended strongly to stick to the helper methods:
 * - [createMap]
 * - [upsertMap]
 * - [deleteMap]
 * - [deleteMapById]
 * - [createCollection]
 * - [upsertCollection]
 * - [deleteCollection]
 * - [deleteCollectionById]
 * - [createFeature]
 * - [upsertFeature]
 * - [deleteFeature]
 * - [deleteFeatureById]
 * - [deleteFeature]
 * - [deleteFeatureById]
 *
 * Modifications of features require to know the identifier of the feature, the collection-id of the collection in which the feature is stored, and the map-id of the map in which the collection is located.
 * @since 3.0
 */
@JsExport
open class Write : AnyObject() {

    companion object Write_C {
        /**
         * A special byte-array instance that represents `undefined`.
         * @since 3.0
         */
        private val UNDEFINED_BYTES = "undefined".encodeToByteArray()

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
         * **If writes are not ordered like this, this may lead to concurrent write conflicts and deadlocks in the storage!**
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun sortCompare(a: Write?, b: Write?): Int {
            if (a === b) return 0
            if (b == null) return -1
            if (a == null) return 1

            // Sorts by catalog, collection, and finally feature.
            val a_catalogId = a.catalogId ?: throw illegalState("Write for feature '${a.id}' does not have 'catalogId'")
            val b_catalogId = b.catalogId ?: throw illegalState("Write for feature '${b.id}' does not have 'catalogId'")
            val a_colId = a.collectionId ?: throw illegalState("Write for feature '${a.id}' does not have 'collectionId'")
            val b_colId = b.collectionId ?: throw illegalState("Write for feature '${b.id}' does not have 'collectionId'")
            val catalog_diff = a_catalogId.compareTo(b_catalogId)
            return if (catalog_diff == 0) {
                val col_diff = a_colId.compareTo(b_colId)
                if (col_diff != 0) col_diff else a.id.compareTo(b.id)
            } else catalog_diff
        }

        private val OP = NotNullEnum<Write, WriteOp>(WriteOp::class) { _, _ -> WriteOp.NULL }
        private val MAP_ID = NullableProperty<Write, String>(String::class)
        private val COLLECTION_ID = NullableProperty<Write, String>(String::class)
        private val FEATURE_NULL = NullableProperty<Write, NakshaFeature>(NakshaFeature::class)
        private val BOOLEAN_FALSE = NotNullProperty<Write, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * The operation to perform.
     * @since 3.0
     */
    var op by OP

    /**
     * @see [op]
     */
    fun withOp(value: WriteOp): Write {
        op = value
        return this
    }

    private var _catalogId: Id? = null

    /**
     * The identifier of the catalog to access.
     *
     * If a [catalog][NakshaCatalog] should be modified, then use [Id.ADMIN_CATALOG_ID].
     * @since 3.0
     */
    var catalogId: Id?
        get() {
            val raw = getRaw("catalogId") as? String? ?: return null
            var id = _catalogId
            if (id != null && id.text == raw) return id
            id = Id(raw)
            _catalogId = id
            return id
        }
        set(value) {
            if (value == null) {
                _catalogId = null
                removeRaw("catalogId")
            } else {
                _catalogId = value
                setRaw("catalogId", value.text)
            }
        }

    /** Fluent setter for [catalogId]. */
    @Deprecated("Renamed", ReplaceWith("withCatalogId(catalogId)"))
    fun withMapId(value: Id?): Write {
        catalogId = value
        return this
    }

    /** Fluent setter for [catalogId]. */
    @Deprecated("Renamed", ReplaceWith("withCatalogId(catalogId)"))
    @JsName("withMapName")
    fun withMapId(value: String?): Write {
        catalogId = if (value != null) Id(value) else null
        return this
    }

    /** Fluent setter for [catalogId]. */
    fun withCatalogId(catalog: Id?): Write {
        catalogId = catalog
        return this
    }

    /** Fluent setter for [catalogId]. */
    fun withCatalogName(value: String?): Write {
        catalogId = if (value != null) Id(value) else null
        return this
    }

    private var _collectionId: Id? = null

    /**
     * The identifier of the collection to modify.
     *
     * - If a [catalog][NakshaCatalog] should be modified, then [Id.CATALOGS_COL_ID] should be used.
     * - If a [collection][NakshaCollection] should be modified, then [Id.COLLECTIONS_COL_ID] should be used.
     * - If a [feature][NakshaFeature] should be modified, then the [collection][NakshaCollection] in which the feature should be stored should be used.
     * @since 3.0
     */
    var collectionId: Id?
        get() {
            val raw = getRaw("collectionId") as? String? ?: return null
            var id = _collectionId
            if (id != null && id.text == raw) return id
            id = Id(raw)
            _collectionId = id
            return id
        }
        set(value) {
            if (value == null) {
                _collectionId = null
                removeRaw("collectionId")
            } else {
                _collectionId = value
                setRaw("collectionId", value.text)
            }
        }

    /** Fluent setter for [collectionId]. */
    fun withCollectionId(value: Id?): Write {
        catalogId = value
        return this
    }

    /** Fluent setter for [collectionId]. */
    @JsName("withCollectionName")
    fun withCollectionId(value: String?): Write {
        catalogId = if (value != null) Id(value) else null
        return this
    }

   private var _id: Id? = null

    /**
     * The identifier of the feature to modify.
     *
     * If not explicitly set, defaults to `feature.id`. If no feature identifier is available, throws an [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] exception.
     * @since 3.0
     */
    var id: Id
        get() {
            val idText = getRaw("id") as? String ?: feature?.id ?: throw illegalState("Missing feature identifier")
            var id = this._id
            if (id != null && id.text == idText) return id
            id = Id(idText)
            this._id = id
            return id
        }
        set(value) {
            setRaw("id", value)
        }

    /** Fluent setter for [id]. */
    fun withId(value: Id?): Write {
        if (value == null) removeRaw("id") else {
            _id = value
            setRaw("id", value.text)
        }
        return this
    }

    /** Fluent setter for [id]. */
    @JsName("withName")
    fun withId(value: String?): Write {
        if (value == null) removeRaw("id") else setRaw("id", value)
        return this
    }

    /**
     * The version of the current _HEAD_ state that should be modified.
     *
     * The meaning is dependent on the [operation][op]:
     * - [CREATE][naksha.model.request.WriteOp.CREATE]: The version is ignored.
     * - [UPDATE][naksha.model.request.WriteOp.UPDATE]: Requires that the current _HEAD_ state is `CREATE` or `UPDATE` **and** exists in this version; otherwise causes a [CONFLICT][NakshaError.CONFLICT] error. If being `null`, only requires that _HEAD_ exists, and is not in state `DELETED`.
     * - [UPSERT][naksha.model.request.WriteOp.UPSERT]: Tries to perform `CREATE`; if that fails, executes an `UPDATE`, in that case the meaning of version is the same as if a normal `UPDATE` would have been requested directl. This operation is more like _"try create, if exists, do update"_.
     * - [DELETE][naksha.model.request.WriteOp.DELETE]: If `null`, always succeed. If a specific version is given, succeeds only if the feature does not exist _(including existence in `DELETE` state)_ or exists in the given version. If the feature exists in `CREATE` or `UPDATE` state, but does not match the given version, fails with [CONFLICT][NakshaError.CONFLICT] error.
     * - [PURGE][naksha.model.request.WriteOp.PURGE]: Always succeed if the feature does not exist in _HEAD_. Succeeds as well if the feature is in `DELETE` state and version is `null`. If given, fails with with [CONFLICT][NakshaError.CONFLICT] error when the _HEAD_ state is `DELETE`, but not in the given version. If the feature exists in `CREATE` or `UPDATE` state, always fails with [FEATURE_EXISTS][NakshaError.FEATURE_EXISTS].
     *
     * ### Note
     * This property must be set explicitly, it is not read automatically from the [feature]. If not being set explicitly, then the operation is treated as "not-atomic" _(see above)_.
     *
     * ### Purging
     * Purge is a very special operation, it is used to move tombstones from _HEAD_ into _HISTORY_. This is only needed when working with views aka branches. For normal operations, purging is not needed and clients should not care about this special operation and its sometimes odd behavior.
     * @since 3.0
     */
    var version: Long?
        get() {
            var raw = getRaw("version")
            if (raw is Double) {
                raw = raw.toLong()
                setRaw("version", raw)
            }
            return if (raw is Long) raw else null
        }
        set(value) {
            if (value == null) removeRaw("version") else setRaw("version", value)
        }

    /** Fluent setter for [version]. */
    @JsName("withVersionInt64")
    fun withVersion(value: Long?): Write {
        version = value
        return this
    }

    /** Fluent setter for [version]. */
    fun withVersion(value: Version?): Write {
        version = value?.number
        return this
    }

    /**
     * The new feature state to persist; if any.
     *
     * Ignored for [PURGE][naksha.model.request.WriteOp.PURGE] operations.
     * @since 3.0
     */
    var feature by FEATURE_NULL

    /** Fluent setter for [feature]. */
    fun withFeature(value: NakshaFeature?): Write {
        feature = value
        return this
    }

    /**
     * If `true`, destructive collection-schema changes (currently: dropping a [naksha.model.objects.Member]) are allowed during UPSERT/UPDATE.
     *
     * Without `force`, removing a [Member][naksha.model.objects.Member] from [NakshaCollection.members][naksha.model.objects.NakshaCollection.members] throws [NakshaError.ILLEGAL_ARGUMENT].
     *
     * Default `false`.
     * @since 3.0
     */
    var force by BOOLEAN_FALSE

    /**
     * @see [force]
     */
    fun withForce(value: Boolean): Write {
        force = value
        return this
    }

    @Deprecated("Renamed", ReplaceWith("createCatalog(catalog)"))
    fun createMap(catalog: NakshaCatalog): Write = createCatalog(catalog)

    /**
     * Create a new catalog.
     * @param catalog the catalog to create.
     * @return this.
     * @since 3.0
     */
    fun createCatalog(catalog: NakshaCatalog): Write {
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.CREATE
        this.feature = catalog
        return this
    }

    @Deprecated("Renamed", ReplaceWith("updateCatalog(catalog, atomic)"))
    fun updateMap(catalog: NakshaCatalog, atomic: Boolean): Write = updateCatalog(catalog, atomic)

    /**
     * Update a catalog.
     * @param catalog the catalog to update.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @return this.
     * @since 3.0
     */
    fun updateCatalog(catalog: NakshaCatalog, atomic: Boolean): Write {
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.UPDATE
        this.feature = catalog
        this.version = if (atomic) (catalog.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    @Deprecated("Renamed", ReplaceWith("upsertCatalog(catalog, atomic)"))
    fun upsertMap(catalog: NakshaCatalog, atomic: Boolean): Write = upsertCatalog(catalog, atomic)

    /**
     * Update or create a catalog.
     * @param catalog the catalog to update or create.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @return this.
     * @since 3.0
     */
    fun upsertCatalog(catalog: NakshaCatalog, atomic: Boolean): Write {
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.UPSERT
        this.feature = catalog
        this.version = if (atomic) (catalog.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    @Deprecated("Renamed", ReplaceWith("deleteCatalog(catalog, atomic)"))
    fun deleteMap(catalog: NakshaCatalog, atomic: Boolean): Write = deleteCatalog(catalog, atomic)

    /**
     * Delete a catalog.
     *
     * **Beware, delete operations always succeed when the entity to be deleted does not exist, this is by definition.**
     * @param catalog the catalog to delete.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @return this.
     * @since 3.0
     */
    fun deleteCatalog(catalog: NakshaCatalog, atomic: Boolean): Write {
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.DELETE
        this.feature = catalog
        this.version = if (atomic) (catalog.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    @Deprecated("Renamed", ReplaceWith("deleteCatalogById(id, requireVersion)"))
    @JvmOverloads
    fun deleteMapById(id: String, requireVersion: Version? = null): Write = deleteCatalogById(id, requireVersion)

    /**
     * Delete a catalog.
     *
     * **Beware, delete operations always succeed when the entity to be deleted does not exist, this is by definition.**
     * @param id the identifier of the catalog to delete.
     * @param requireVersion the [version] to delete, `null` if delete is not conditional.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteCatalogById(id: String, requireVersion: Version? = null): Write {
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.DELETE
        withId(id)
        this.version = if (requireVersion != null) requireVersion.number else null
        return this
    }

    /**
     * Create a Naksha collection in the given map.
     * @param collection the collection to create.
     * @since 3.0
     */
    fun createCollection(collection: NakshaCollection): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.CREATE
        this.feature = collection
        return this
    }

    /**
     * Update a Naksha collection.
     * @param collection the new state of the collection.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun updateCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.UPDATE
        this.feature = collection
        this.version = if (atomic) (collection.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Update or create a Naksha collection.
     * @param collection the new state of the collection.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun upsertCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.UPSERT
        this.feature = collection
        this.version = if (atomic) (collection.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Delete a Naksha collection.
     * @param collection the collection to delete.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun deleteCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.DELETE
        this.feature = collection
        this.version = if (atomic) (collection.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Delete a collection.
     *
     * **Beware, delete operations always succeed when the entity to be deleted does not exist, this is by definition.**
     * @param catalogId the `id` of the catalog from which to delete the collection.
     * @param collectionId the `id` of the collection to delete.
     * @param requireVersion the required [version]; if any.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteCollectionById(catalogId: String? = null, collectionId: String, requireVersion: Version? = null): Write {
        if (catalogId != null) this.catalogId = Id(catalogId) else {
            logger.warn("${FAL}Calling deleteCollectionById with catalogId being null is deprecated!")
        }
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.DELETE
        this.id = Id(collectionId)
        this.version = requireVersion?.number
        return this
    }

    /**
     * Create a Naksha feature.
     * @param collection the collection in which to create the feature.
     * @param feature the feature to create.
     * @return this.
     * @since 3.0
     */
    fun createFeature(collection: NakshaCollection, feature: NakshaFeature): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.CREATE
        this.feature = feature
        return this
    }

    /**
     * Create a Naksha feature in current map.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection in which to create the feature.
     * @param feature the feature to create.
     * @return this.
     * @since 3.0
     */
    @Deprecated(message = "Use createFeature with NakshaCollection instead")
    @JsName("createFeatureUsingIds")
    @JvmOverloads
    fun createFeature(catalogId: String? = null, collectionId: String, feature: NakshaFeature): Write {
        if (catalogId != null) this.catalogId = Id(catalogId) else {
            logger.warn("${FAL}Calling createFeature with catalogId being null is deprecated!")
        }
        this.collectionId = Id(collectionId)
        this.op = WriteOp.CREATE
        this.feature = feature
        return this
    }

    /**
     * Update a Naksha feature.
     * @param collection the collection in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun updateFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.UPDATE
        this.feature = feature
        this.version = if (atomic) (feature.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Update a Naksha feature.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    @Deprecated(message = "Use updateFeature with NakshaCollection instead")
    @JsName("updateFeatureUsingIds")
    @JvmOverloads
    fun updateFeature(catalogId: String? = null, collectionId: String, feature: NakshaFeature, atomic: Boolean): Write {
        if (catalogId != null) this.catalogId = Id(catalogId) else {
            logger.warn("${FAL}Calling createFeature with catalogId being null is deprecated!")
        }
        this.collectionId = Id(collectionId)
        this.op = WriteOp.UPDATE
        this.feature = feature
        this.version = if (atomic) (feature.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Update or create a Naksha feature.
     * @param collection the collection in which to upsert the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun upsertFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.UPSERT
        this.feature = feature
        this.version = if (atomic) (feature.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Update or create a Naksha feature in current map.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection in which to upsert the feature.
     * @param feature the new state of the feature.
     * @param requireVersion the required [version]; if any.
     * @since 3.0
     */
    @Deprecated(message = "Use upsertFeature with NakshaCollection instead")
    @JsName("upsertFeatureUsingIds")
    @JvmOverloads
    fun upsertFeature(catalogId: String? = null, collectionId: String, feature: NakshaFeature, requireVersion: Version? = null): Write {
        if (catalogId != null) this.catalogId = Id(catalogId) else {
            logger.warn("${FAL}Calling upsertFeature with catalogId being null is deprecated!")
        }
        this.collectionId = Id(collectionId)
        this.op = WriteOp.UPSERT
        this.feature = feature
        return this
    }

    /**
     * Delete a Naksha feature.
     * @param collection the collection from which to delete the feature.
     * @param feature the feature to delete.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun deleteFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.DELETE
        this.feature = feature
        this.version = if (atomic) (feature.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Delete a feature by id.
     * @param collection the collection from which to delete the feature.
     * @param id the identifier of the feature to delete.
     * @param requireVersion the required [requireVersion]; if any.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteFeatureById(collection: NakshaCollection, id: String, requireVersion: Version? = null): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.DELETE
        this.id = Id(id)
        this.version = requireVersion?.number
        return this
    }

    /**
     * Delete a feature by id.
     * @param catalogId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier from which to delete the feature.
     * @param id the identifier of the feature to delete.
     * @param requireVersion the required [version]; if any.
     * @return this.
     * @since 3.0
     */
    @Deprecated(message = "Use deleteFeatureById with NakshaCollection instead")
    @JsName("deleteFeatureByIds")
    @JvmOverloads
    fun deleteFeatureById(catalogId: String? = null, collectionId: String, id: String, requireVersion: Version? = null): Write {
        if (catalogId != null) this.catalogId = Id(catalogId) else {
            logger.warn("${FAL}Calling upsertFeature with catalogId being null is deprecated!")
        }
        this.collectionId = Id(collectionId)
        this.op = WriteOp.DELETE
        this.id = Id(id)
        this.version = requireVersion?.number
        return this
    }

    /**
     * Purge a Naksha feature.
     * @param collection the collection from which to purge the feature.
     * @param feature the feature to purge.
     * @param atomic if _true_, copies the version found in the [uuid][XyzNs.uuid] of the feature into [version].
     * @since 3.0
     */
    fun purgeFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.PURGE
        this.feature = feature
        this.version = if (atomic) (feature.tupleNumber?.version ?: throw illegalState("Missing version")) else null
        return this
    }

    /**
     * Purge a feature by id.
     * @param collection the collection from which to purge the feature.
     * @param id the identifier of the feature to purge.
     * @param requireVersion the required [requireVersion]; if any.
     * @return this.
     * @since 3.0
     */
    @JsName("purgeFeatureById")
    @JvmOverloads
    fun purgeFeatureById(collection: NakshaCollection, id: String, requireVersion: Version? = null): Write {
        this.catalogId = Id(collection.catalogId ?: throw illegalState("Missing catalogId in collection"))
        this.collectionId = Id(collection.id)
        this.op = WriteOp.PURGE
        this.id = Id(id)
        this.version = requireVersion?.number
        return this
    }

    /**
     * Tests if this write modifies a catalog.
     *
     * @return `true` if this write modifies a catalog; `false` otherwise.
     * @since 3.0
     */
    fun isCatalogModification(): Boolean = catalogId == ADMIN_CATALOG_ID && collectionId == CATALOGS_COL_ID

    /**
     * Tests if this write modifies a collection.
     *
     * @return `true` if this write modifies a collection; `false` otherwise.
     * @since 3.0
     */
    fun isCollectionModification(): Boolean = collectionId == COLLECTIONS_COL_ID

    /**
     * Tests if this write modifies a dictionary.
     *
     * @return `true` if this write modifies a dictionary; `false` otherwise.
     * @since 3.0
     */
    fun isBookModification(): Boolean = catalogId == ADMIN_CATALOG_ID && collectionId == BOOKS_COL_ID

    /**
     * Tests if this write modifies a transaction.
     *
     * @return `true` if this write modifies a transaction; `false` otherwise.
     * @since 3.0
     */
    fun isTransactionModification(): Boolean = catalogId == ADMIN_CATALOG_ID && collectionId == TRANSACTIONS_COL_ID

    /**
     * Tests if this write modifies a feature within a collection.
     *
     * @return `true` if this write modifies a feature within a collection; `false` otherwise.
     * @since 3.0
     */
    fun isFeatureModification(): Boolean = catalogId != ADMIN_CATALOG_ID && collectionId != COLLECTIONS_COL_ID

    @Deprecated(message = "This method is now an no-op, it will not do anything")
    fun validate(): Write = this
}
