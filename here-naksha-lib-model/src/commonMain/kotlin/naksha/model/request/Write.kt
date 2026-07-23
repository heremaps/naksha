@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.*
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.ADMIN_CATALOG_ID
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_ID
import naksha.model.Naksha.NakshaCompanion.BOOKS_COL_ID
import naksha.model.Naksha.NakshaCompanion.CATALOGS_COL_ID
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_ID
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
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

        private fun compareMapIds(a: String, b: String): Int {
            if (a == b) return 0
            // We order all modifications done in admin-map first.
            if (ADMIN_CATALOG_ID == a) return -1
            if (ADMIN_CATALOG_ID == b) return 1
            return a.compareTo(b)
        }

        private fun compareCollectionIds(a: String, b: String): Int {
            if (a == b) return 0
            // We order all modifications done in map's collection first (create maps first).
            if (CATALOGS_COL_ID == a) return -1
            if (CATALOGS_COL_ID == b) return 1
            // We order all modifications done in internal collection's-collection second.
            if (COLLECTIONS_COL_ID == a) return -1
            if (COLLECTIONS_COL_ID == b) return 1
            // Rest by id
            return a.compareTo(b)
        }

        /**
         * The method to order writes via [MutableList.sortedWith] by:
         * - `catalog-id`
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

            // Sorts by map-id, collection-id, feature-id.
            // Sorts admin-map, map's collection, and collection's collection first.
            val a_mapId = a.catalogId ?: throw illegalState("Write for feature '${a.id}' does not have 'mapId'")
            val b_mapId = b.catalogId ?: throw illegalState("Write for feature '${b.id}' does not have 'mapId'")
            val a_colId = a.collectionId ?: throw illegalState("Write for feature '${a.id}' does not have 'collectionId'")
            val b_colId = b.collectionId ?: throw illegalState("Write for feature '${b.id}' does not have 'collectionId'")
            val map_diff = compareMapIds(a_mapId, b_mapId)
            return if (map_diff == 0) {
                val col_diff = compareCollectionIds(a_colId, b_colId)
                if (col_diff == 0) {
                    val a_partition = partitionNumber(a.featureNumber)
                    val b_partition = partitionNumber(b.featureNumber)
                    val part_diff = a_partition.compareTo(b_partition)
                    if (part_diff == 0) {
                        val id_diff = a.id.compareTo(b.id)
                        if (id_diff == 0) a.op.compareTo(b.op) else id_diff
                    } else part_diff
                } else col_diff
            } else map_diff
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

    /**
     * The identifier of the catalog to access.
     *
     * - If a [catalog][NakshaCatalog] should be modified, then use [Naksha.ADMIN_CATALOG_ID].
     * @since 3.0
     */
    var catalogId by MAP_ID

    /**
     * @see [catalogId]
     */
    fun withCatalogId(value: String?): Write {
        catalogId = value
        return this
    }

    /**
     * The identifier of the collection to modify; must not be `null` then the map-id is read from the [NakshaContext].
     *
     * - If a [catalog][NakshaCatalog] should be modified, then [Naksha.CATALOGS_COL_ID] should be used, within [Naksha.ADMIN_CATALOG_ID].
     * - If a [collection][NakshaCollection] should be modified, then [Naksha.COLLECTIONS_COL_ID] should be used.
     * - If a [feature][NakshaFeature] should be modified, then the [NakshaCollection] of the feature should be used.
     * @since 3.0
     * @throws NakshaException with error [NakshaError.ILLEGAL_STATE], if the collection-id is read, before being set.
     */
    var collectionId by COLLECTION_ID

    /**
     * @see [collectionId]
     */
    fun withCollectionId(value: String?): Write {
        collectionId = value
        return this
    }

    private var versionValue: Int64? = null
    private var versionInstance: Version? = null

    /**
     * The expected version that should be modified.
     *
     * Should only be set explicitly for [PURGE][WriteOp.PURGE] and [DELETE][WriteOp.DELETE] by `id`. In all other cases the given feature contains the version the client modified, and when `atomic` is requested, the embedded version is read by the storage. Try to avoid explicitly using [version], whenever you have the object.
     * @since 3.0
     * @see [atomic]
     */
    var version: Version?
        get() {
            var value = getRaw("version")
            if (value is Number) {
                value = Int64(value.toLong())
                setRaw("version", value)
            }
            if (value is Int64) {
                if (value === versionValue) return versionInstance
                versionValue = value
                versionInstance = Version(value)
                return versionInstance
            }
            return null
        }
        set(value) {
            if (value == null) {
                removeRaw("version")
                versionValue = null
                versionInstance = null
            } else {
                setRaw("version", value)
                versionValue = value.number
                versionInstance = value
            }
        }

    /**
     * @see [version]
     */
    fun withVersion(value: Version?): Write {
        version = value
        return this
    }

    private var tupleNumberRaw: String? = null
    private var tupleNumberValue: TupleNumber? = null

    /**
     * The expected version that should be modified.
     *
     * If not `null` and [atomic] is `true`, then the operation is atomic and expects that the existing _HEAD_ state is in the given [version][Version].
     *
     * If not explicitly set, defaults to `feature.properties.xyz.guid.tupleNumber`.
     * @since 3.0
     * @see [atomic]
     */
    var tupleNumber: TupleNumber?
        get() {
            val raw = getRaw("tupleNumber")
            if (raw is String) {
                @Suppress("StringReferentialEquality") // Intentional reference compare !
                if (raw === tupleNumberRaw) return tupleNumberValue
                tupleNumberValue = TupleNumber.fromUrn(raw)
                tupleNumberRaw = raw
                return tupleNumberValue
            }
            return feature?.properties?.xyz?.guid?.tupleNumber
        }
        set(value) {
            if (value == null) removeRaw("tupleNumber") else {
                tupleNumberValue = value
                tupleNumberRaw = value.toUrn()
                setRaw("tupleNumber", tupleNumberRaw)
            }
        }

    /**
     * @see [version]
     */
    fun withTupleNumber(value: TupleNumber?): Write {
        tupleNumber = value
        return this
    }

    /**
     * If this write should be performed atomic.
     *
     * Atomic actions, except for [CREATE][WriteOp.CREATE], does require either [version], [tupleNumber], or a [TupleNumber] in the [feature].
     *
     * @return `true` if this write should be performed atomic; `false` otherwise _(default)_.
     * @since 3.0
     * @see [validate]
     * @see [version]
     */
    var atomic: Boolean
        get() {
            val raw = getRaw("atomic")
            return raw as? Boolean ?: false
        }
        set(value) {
            if (value) setRaw("atomic", true) else removeRaw("atomic")
        }

    /**
     * @see [atomic]
     */
    fun withAtomic(value: Boolean): Write {
        this.atomic = value
        return this
    }

    /**
     * The identifier of the feature to modify.
     *
     * If not explicitly set, defaults to `feature.id`.
     * @since 3.0
     * @throws NakshaException with error [NakshaError.ILLEGAL_STATE] if not available.
     */
    @Suppress("SENSELESS_COMPARISON")
    var id: String
        get() {
            val raw = getRaw("id")
            if (raw is String) return raw
            return feature?.id ?: throw illegalState("Missing feature identifier")
        }
        set(value) {
            if (value == null) removeRaw("id") else setRaw("id", value)
            featureNumberId = null
            featureNumberValue = null
        }

    /**
     * @see [id]
     */
    fun withId(value: String?): Write {
        if (value == null) removeRaw("id") else setRaw("id", value)
        return this
    }

    /**
     * The new feature state to persist; if any _(not valid for deletes)_.
     * @since 3.0
     */
    var feature by FEATURE_NULL

    /**
     * @see [feature]
     */
    fun withFeature(value: NakshaFeature?): Write {
        feature = value
        return this
    }

    private var featureNumberId: String? = null
    private var featureNumberValue: Int64? = null

    /**
     * The **feature-number**.
     *
     * If set to a positive number, sets [id] to the same value as string.
     *
     * @since 3.0
     * @throws NakshaException with error [NakshaError.ILLEGAL_STATE] if [id] is not available.
     */
    @Suppress("SENSELESS_COMPARISON")
    var featureNumber: Int64
        get() {
            val id = this.id
            var number = featureNumberValue
            @Suppress("StringReferentialEquality") // We compare by intention by reference!
            if (featureNumberId === id && number != null) return number
            number = featureNumber(id)
            featureNumberId = id
            featureNumberValue = number
            return number
        }
        set(value) {
            // Note: Technically, from Java/JavaScript we can have a setter being called with `null`.
            if (value == null) return
            if (value < 0) throw illegalArg("Negative feature-number can only be calculated from 'id'")
            id = value.toString()
            featureNumberId = id
            featureNumberValue = value
        }

    /**
     * Set the [featureNumber] and return `this`.
     *
     * This method allows to reset the [featureNumber] to `null`, which means "go back to default behavior".
     * @see [featureNumber]
     */
    fun withFeatureNumber(value: Int64?): Write {
        if (value == null) removeRaw("featureNumber") else setRaw("featureNumber", value)
        return this
    }

    /**
     * Set the [featureNumber] and return `this`, the 32-bit number is converted into an unsigned 32-bit value and stored in the lower 32-bit of the [featureNumber].
     *
     * This method allows to reset the [featureNumber] to `null`, which basically means auto-generated, and is not supported by the default setter.
     * @see [featureNumber]
     */
    @JsName("withFeatureNumber32")
    fun withFeatureNumber(value: Int?): Write {
        val featureNumber = if (value == null) null else value.toInt64() and Naksha.INT64_CLEAR_HIGH32
        withFeatureNumber(featureNumber)
        return this
    }

    /**
     * Create a new catalog.
     * @param catalog the catalog to create.
     * @return this.
     * @since 3.0
     */
    fun createCatalog(catalog: NakshaCatalog): Write {
        this.op = WriteOp.CREATE
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.feature = catalog
        return this
    }

    /**
     * Update a catalog.
     * @param catalog the catalog to update.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun updateCatalog(catalog: NakshaCatalog, atomic: Boolean = true): Write {
        this.op = WriteOp.UPDATE
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.feature = catalog
        this.atomic = atomic
        return this
    }

    /**
     * Update or create a catalog.
     * @param catalog the catalog to update or create.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @return this.
     * @since 3.0
     */
    fun upsertCatalog(catalog: NakshaCatalog, atomic: Boolean): Write {
        this.op = WriteOp.UPSERT
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.feature = catalog
        this.atomic = atomic
        return this
    }

    /**
     * Delete a catalog.
     * @param catalog the catalog to delete.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @return this.
     * @since 3.0
     */
    fun deleteCatalog(catalog: NakshaCatalog, atomic: Boolean = true): Write {
        this.op = WriteOp.DELETE
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.feature = catalog
        this.atomic = atomic
        return this
    }

    /**
     * Delete a catalog.
     * @param id the `id` of the catalog to delete.
     * @param version the version to delete, if the deletion should be done atomic.
     * @return this.
     * @since 3.0
     */
    @JsName("deleteCatalogById")
    @JvmOverloads
    fun deleteCatalog(id: String, version: Version? = null): Write {
        this.op = WriteOp.DELETE
        this.catalogId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Create a Naksha collection in the given map.
     * @param collection the collection to create.
     * @since 3.0
     */
    fun createCollection(collection: NakshaCollection): Write {
        this.op = WriteOp.CREATE
        this.catalogId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.feature = collection
        return this
    }

    /**
     * Update a Naksha collection.
     * @param collection the new state of the collection.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    fun updateCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.op = WriteOp.UPDATE
        this.catalogId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.feature = collection
        this.atomic = atomic
        return this
    }

    /**
     * Update or create a Naksha collection.
     * @param collection the new state of the collection.
     * @since 3.0
     */
    fun upsertCollection(collection: NakshaCollection): Write {
        this.op = WriteOp.UPSERT
        this.catalogId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.feature = collection
        return this
    }

    /**
     * Delete a Naksha collection.
     * @param collection the collection to delete.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    fun deleteCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.op = WriteOp.DELETE
        this.catalogId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.feature = collection
        this.atomic = atomic
        return this
    }

    /**
     * Delete a collection.
     * @param catalogId the `id` of the catalog from which to delete the collection
     * @param id the `id` of the collection to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     * @since 3.0
     */
    @JsName("deleteCollectionById")
    @JvmOverloads
    fun deleteCollection(catalogId: String, id: String, version: Version? = null): Write {
        this.op = WriteOp.DELETE
        this.catalogId = catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.id = id
        this.version = version
        this.atomic = version != null
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
        this.op = WriteOp.CREATE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
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
    @JsName("createFeatureUsingIds")
    fun createFeature(catalogId: String, collectionId: String, feature: NakshaFeature): Write {
        this.op = WriteOp.CREATE
        this.catalogId = catalogId
        this.collectionId = collectionId
        this.feature = feature
        return this
    }

    /**
     * Update a Naksha feature.
     * @param collection the collection in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    fun updateFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.op = WriteOp.UPDATE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.feature = feature
        this.atomic = atomic
        return this
    }

    /**
     * Update a Naksha feature.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    @JsName("updateFeatureById")
    fun updateFeature(catalogId: String, collectionId: String, feature: NakshaFeature, atomic: Boolean): Write {
        this.op = WriteOp.UPDATE
        this.catalogId = catalogId
        this.collectionId = collectionId
        this.feature = feature
        this.atomic = atomic
        return this
    }

    /**
     * Update or create a Naksha feature.
     * @param collection the collection in which to upsert the feature.
     * @param feature the new state of the feature.
     * @since 3.0
     */
    fun upsertFeature(collection: NakshaCollection, feature: NakshaFeature): Write {
        this.op = WriteOp.UPSERT
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.feature = feature
        return this
    }

    /**
     * Update or create a Naksha feature in current map.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection in which to upsert the feature.
     * @param feature the new state of the feature.
     * @since 3.0
     */
    @JsName("upsertFeatureById")
    fun upsertFeature(catalogId: String, collectionId: String, feature: NakshaFeature): Write {
        this.op = WriteOp.UPSERT
        this.catalogId = catalogId
        this.collectionId = collectionId
        this.feature = feature
        return this
    }

    /**
     * Delete a Naksha feature.
     * @param collection the collection from which to delete the feature.
     * @param feature the feature to delete.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    fun deleteFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.op = WriteOp.DELETE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.feature = feature
        this.atomic = atomic
        return this
    }

    /**
     * Delete a feature by id.
     * @param collection the collection from which to delete the feature.
     * @param id the identifier of the feature to delete.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("deleteFeatureById")
    @JvmOverloads
    fun deleteFeature(collection: NakshaCollection, id: String, version: Version? = null): Write {
        this.op = WriteOp.DELETE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Delete a feature by id.
     * @param catalogId the `id` of the catalog in which the collection is located.
     * @param collectionId the `id` of the collection from which to delete the feature.
     * @param id the identifier of the feature to delete.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("deleteFeatureByIds")
    @JvmOverloads
    fun deleteFeature(catalogId: String, collectionId: String, id: String, version: Version? = null): Write {
        this.op = WriteOp.DELETE
        this.catalogId = catalogId
        this.collectionId = collectionId
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Purge a Naksha feature.
     * @param collection the collection from which to purge the feature.
     * @param feature the feature to purge.
     * @param atomic if _true_, requires that the collection exists in a specific [version].
     * @since 3.0
     */
    fun purgeFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.op = WriteOp.PURGE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.feature = feature
        this.atomic = atomic
        return this
    }

    /**
     * Purge a feature by id.
     * @param collection the collection from which to purge the feature.
     * @param id the identifier of the feature to purge.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("purgeFeatureById")
    @JvmOverloads
    fun purgeFeature(collection: NakshaCollection, id: String, version: Version? = null): Write {
        this.op = WriteOp.PURGE
        this.catalogId = collection.catalogId
        this.collectionId = collection.id
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Purge a feature by id.
     * @param catalogId the `id` of the catalog in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the `id` of the collection from which to purge the feature.
     * @param id the identifier of the feature to purge.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("purgeFeatureByIds")
    @JvmOverloads
    fun purgeFeature(catalogId: String, collectionId: String, id: String, version: Version? = null): Write {
        this.op = WriteOp.PURGE
        this.catalogId = catalogId
        this.collectionId = collectionId
        this.id = id
        this.version = version
        this.atomic = version != null
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
     * Tests if this write modifies a feature within a collection.
     *
     * @return `true` if this write modifies a feature within a collection; `false` otherwise.
     * @since 3.0
     */
    fun isFeatureModification(): Boolean = !isCatalogModification() && !isCollectionModification()

    /**
     * Validate that this write is valid, invoked by [session's][naksha.model.ISession] before executing the writes.
     *
     * If [atomic] is _true_, and [version] is not _null_, then the _HEAD_ state of the modified feature must be in this version. This is invalid for [CREATE][WriteOp.CREATE].
     *
     * If the [op] is [UPSERT][WriteOp.UPSERT], the values of [atomic] and [version] are ignored.
     *
     * - Throws [NakshaException] with error [NakshaError.ILLEGAL_STATE], if the write is invalid.
     * @since 3.0
     * @see [atomic]
     * @see [WriteOp]
     */
    fun validate(): Write {
        // Writing into the admin-catalog means that we either want to mutate administrative objects.
        if (catalogId == ADMIN_CATALOG_ID) {
            if (collectionId == CATALOGS_COL_ID) { // Mutation of catalog.
                NakshaIdType.CATALOG.verify(id)
            } else if (collectionId == BOOKS_COL_ID) { // Mutation of book.
                NakshaIdType.BOOK.verify(id)
            } else if (collectionId == COLLECTIONS_COL_ID) { // Mutation of collection.
                NakshaIdType.COLLECTION.verify(id)
            } else if (collectionId == TRANSACTIONS_COL_ID) { // Mutation of transaction.
                NakshaIdType.TRANSACTION.verify(id)
            } else {
                throw illegalArg("Write operation for invalid collection in admin catalog; id: '$id' ")
            }
            return this
        }
        if (collectionId == COLLECTIONS_COL_ID) { // Mutation of collection in custom catalog.
            NakshaIdType.COLLECTION.verify(id)
        } // otherwise, arbitrary feature is modified, we allow any identifier.
        return this
    }
}