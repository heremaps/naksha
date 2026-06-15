@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.*
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.ADMIN_CATALOG_ID
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL_ID
import naksha.model.Naksha.NakshaCompanion.BOOKS_COL_ID
import naksha.model.Naksha.NakshaCompanion.CATALOGS_COL_ID
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.isInternalId
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaDictionary
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A write instruction for the storage.
 *
 * Its recommended strongly to stick to the helper methods:
 * - [createDictionary]
 * - [upsertDictionary]
 * - [deleteDictionary]
 * - [deleteDictionaryById]
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
        val UNDEFINED = "undefined".encodeToByteArray()

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

            // Sorts by map-id, collection-id, feature-id.
            // Sorts admin-map, map's collection, and collection's collection first.
            val a_mapId = a.mapId ?: throw illegalState("Write for feature '${a.id}' does not have 'mapId'")
            val b_mapId = b.mapId ?: throw illegalState("Write for feature '${b.id}' does not have 'mapId'")
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
                        return if (id_diff == 0) a.op.compareTo(b.op) else id_diff
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
     * The identifier of the map to access; if `null` then the map-id is read from the [NakshaContext].
     *
     * If a [map][NakshaCatalog] or [dictionary][NakshaDictionary] should be modified, then use [Naksha.ADMIN_CATALOG_ID].
     * @since 3.0
     */
    var mapId by MAP_ID

    /**
     * @see [mapId]
     */
    fun withMapId(value: String?): Write {
        mapId = value
        return this
    }

    /**
     * The identifier of the collection to modify; must not be `null` then the map-id is read from the [NakshaContext].
     *
     * - If a [map][NakshaCatalog] should be modified, then [Naksha.CATALOGS_COL_ID] should be used, within [Naksha.ADMIN_CATALOG_ID].
     * - If a [dictionary][NakshaDictionary] should be modified, the [Naksha.BOOKS_COL_ID] should be used, within [Naksha.ADMIN_CATALOG_ID].
     * - If a [collection][NakshaCollection] should be modified, then [Naksha.COLLECTIONS_COL_ID] should be used, must not be used together with [Naksha.ADMIN_CATALOG_ID], because the admin-map does not allow collection modification, it is internally managed.
     * - If a [feature][NakshaFeature] should be created, then the [NakshaCollection] in which the feature should be stored is required.
     * - Throws [ILLEGAL_STATE], if the collection-id is read, before being set.
     * @since 3.0
     */
    var collectionId by COLLECTION_ID

    /**
     * @see [collectionId]
     */
    fun withCollectionId(value: String?): Write {
        collectionId = value
        return this
    }

    private var versionRaw: Int64? = null
    private var versionValue: Version? = null

    /**
     * The expected version that should be modified.
     *
     * If not `null` and [atomic] is `true`, then the operation is atomic and expects that the existing _HEAD_ state is in the given [version][naksha.model.Version].
     *
     * If not explicitly set, defaults to `feature.properties.xyz.guid.tupleNumber.version`.
     * @since 3.0
     * @see [atomic]
     */
    var version: Version?
        get() {
            var raw = getRaw("version")
            if (raw is Double) {
                raw = raw.toInt64()
                setRaw("version", raw)
            }
            if (raw is Int64) {
                if (raw === versionRaw) return versionValue
                versionRaw = raw
                versionValue = Version(raw)
                return versionValue
            }
            val versionNumber = feature?.properties?.xyz?.guid?.tupleNumber?.version
            return if (versionNumber != null) Version(versionNumber) else null
        }
        set(value) {
            if (value == null) removeRaw("version") else {
                versionValue = value
                versionRaw = value.number
                setRaw("version", versionRaw)
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
     * If not `null` and [atomic] is `true`, then the operation is atomic and expects that the existing _HEAD_ state is in the given [version][naksha.model.Version].
     *
     * If not explicitly set, defaults to `feature.properties.xyz.guid.tupleNumber`.
     * @since 3.0
     * @see [atomic]
     */
    var tupleNumber: TupleNumber?
        get() {
            val raw = getRaw("tupleNumber")
            if (raw is String) {
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
     * Atomic actions, except for [CREATE][WriteOp.CREATE], do require either a [version] or a [tupleNumber].
     *
     * @return `true` if this write should be performed atomic; `false` otherwise _(default)_.
     * @since 3.0
     * @see [validate]
     * @see [version]
     */
    var atomic: Boolean
        get() {
            val raw = getRaw("atomic")
            return if (raw is Boolean) raw else false
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
     * If not explicitly set, defaults to `feature.id`. If no feature identifier is available, throws an [ILLEGAL_STATE] exception.
     * @since 3.0
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
     * The **feature-number** to operate upon.
     *
     * If not explicitly set, reads `feature.properties.xyz.guid.tupleNumber.featureNumber`, if that is `null`, then it calculates the feature-number from the `id`. If all of these fail, it will throw an [ILLEGAL_STATE] exception.
     *
     * The feature-number is informal only, when processing the write-operation it is totally ignored.
     * @since 3.0
     */
    @Suppress("SENSELESS_COMPARISON")
    var featureNumber: Int64
        get() {
            val raw = getRaw("featureNumber")
            if (raw is Int64) return raw

            // TODO: This is a hack, we need to change this.
            //       Without the collection, we normally do not know where the tuple-number is located within a feature.
            val feature = this.feature
            var fn: Int64? = null
            if (feature != null) {
                fn = XyzMembers.XyzTn.getTupleNumber(feature)?.featureNumber
                if (fn == null) fn = StandardMembers.Tn.getTupleNumber(feature)?.featureNumber
            }
            if (fn != null) return fn

            val id = this.id
            val cachedId = featureNumberId
            val cachedNumber = featureNumberValue
            if (id === cachedId && cachedNumber != null) return cachedNumber
            val number = featureNumber(id)
            featureNumberId = id
            featureNumberValue = number
            return number
        }
        set(value) {
            // Note: Technically, from Java/JavaScript we can have a setter being called with `null`.
            if (value == null) removeRaw("featureNumber") else setRaw("featureNumber", value)
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
     * Arbitrary attachment to be stored, if this is [CREATE][WriteOp.CREATE], [UPSERT][WriteOp.UPSERT], or [UPDATE][WriteOp.UPDATE].
     *
     * If being [UNDEFINED], then the attachment, in whatever state it is, is left unmodified, _(this is the default value)_. If the value is explicitly set to `null`, an existing attachments is removed, if set to a specific byte-array, then the attachment is updated.
     *
     * If this is a [CREATE][WriteOp.CREATE] operation, the value [UNDEFINED] has the same meaning as explicitly setting the value to `null`.
     * @since 3.0
     * @see [UNDEFINED]
     */
    var attachment: ByteArray? = UNDEFINED

    /**
     * @see [attachment]
     */
    fun withAttachment(value: ByteArray?): Write {
        attachment = value
        return this
    }

    /**
     * Ask the storage to keep the attachment in the state in which it currently is. This is the default behavior.
     * @return this.
     * @since 3.0
     */
    fun keepAttachment(): Write {
        attachment = UNDEFINED
        return this
    }

    /**
     * Tests if the attachment should be modified.
     * @return `true` if the attachment should be modified; `false` if the attachment should stay unchanged _(default behavior)_.
     */
    fun attachmentModified(): Boolean = attachment !== UNDEFINED

    /**
     * If enabled, a missing map is automatically created, when creating or modifying collections; defaults to `false`.
     *
     * To make the default map more transparent, this option can be enabled by clients like:
     *
     * ```kotlin
     * request
     *   .add(Write().withAutoCreateMap(true).createCollection(...))
     *   .add(Write().createFeature(...))
     *   .add(Write().createFeature(...))
     * ```
     * @since 3.0
     */
    var autoCreateMap by BOOLEAN_FALSE

    /**
     * @see [autoCreateMap]
     */
    fun withAutoCreateMap(value: Boolean): Write {
        autoCreateMap = value
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

    /**
     * Create a new dictionary.
     * @param dict the dictionary to create.
     * @return this.
     * @since 3.0
     */
    fun createDictionary(dict: NakshaDictionary): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = BOOKS_COL_ID
        this.op = WriteOp.CREATE
        this.feature = dict
        return this
    }

    /**
     * Update a dictionary.
     * @param dict the dictionary to update.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @return this.
     * @since 3.0
     */
    fun updateDictionary(dict: NakshaDictionary, atomic: Boolean): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = BOOKS_COL_ID
        this.op = WriteOp.UPDATE
        this.feature = dict
        this.atomic = atomic
        return this
    }

    /**
     * Update or create a dictionary.
     * @param dict the dictionary to update or create.
     * @return this.
     * @since 3.0
     */
    fun upsertDictionary(dict: NakshaDictionary): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = BOOKS_COL_ID
        this.op = WriteOp.UPSERT
        this.feature = dict
        return this
    }

    /**
     * Delete a dictionary.
     * @param dict the dictionary to delete.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @return this.
     * @since 3.0
     */
    fun deleteDictionary(dict: NakshaDictionary, atomic: Boolean): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = BOOKS_COL_ID
        this.op = WriteOp.DELETE
        this.feature = dict
        this.atomic = atomic
        return this
    }

    /**
     * Delete a dictionary.
     * @param dictId the dictionary to delete.
     * @param version the version to delete, if the deletion should be done atomic.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteDictionaryById(dictId: String, version: Version? = null): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = BOOKS_COL_ID
        this.op = WriteOp.DELETE
        this.id = dictId
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Create a new map.
     * @param map the map to create.
     * @return this.
     * @since 3.0
     */
    fun createMap(map: NakshaCatalog): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.CREATE
        this.feature = map
        return this
    }

    /**
     * Update a map.
     * @param map the map to update.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @return this.
     * @since 3.0
     */
    fun updateMap(map: NakshaCatalog, atomic: Boolean): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.UPDATE
        this.feature = map
        this.atomic = atomic
        return this
    }

    /**
     * Update or create a map.
     * @param map the map to update or create.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @return this.
     * @since 3.0
     */
    fun upsertMap(map: NakshaCatalog, atomic: Boolean): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.UPSERT
        this.feature = map
        this.atomic = atomic
        return this
    }

    /**
     * Delete a map.
     * @param map the map to delete.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @return this.
     * @since 3.0
     */
    fun deleteMap(map: NakshaCatalog, atomic: Boolean): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.DELETE
        this.feature = map
        this.atomic = atomic
        return this
    }

    /**
     * Delete a map.
     * @param id the identifier of the map to delete.
     * @param version the version to delete, if the deletion should be done atomic.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteMapById(id: String, version: Version? = null): Write {
        this.mapId = ADMIN_CATALOG_ID
        this.collectionId = CATALOGS_COL_ID
        this.op = WriteOp.DELETE
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
        this.mapId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.CREATE
        this.feature = collection
        return this
    }

    /**
     * Update a Naksha collection.
     * @param collection the new state of the collection.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0
     */
    fun updateCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.mapId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.UPDATE
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
        this.mapId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.UPSERT
        this.feature = collection
        return this
    }

    /**
     * Delete a Naksha collection.
     * @param collection the collection to delete.
     * @param atomic if the operation should be performed atomic.
     * @since 3.0
     */
    fun deleteCollection(collection: NakshaCollection, atomic: Boolean): Write {
        this.mapId = collection.catalogId
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.DELETE
        this.feature = collection
        this.atomic = atomic
        return this
    }

    /**
     * Delete a collection.
     * @param mapId the map-id of the map from which to delete the collection
     * @param collectionId the collection-id of the collection to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteCollectionById(mapId: String? = null, collectionId: String, version: Version? = null): Write {
        this.mapId = mapId
        this.collectionId = COLLECTIONS_COL_ID
        this.op = WriteOp.DELETE
        this.id = collectionId
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
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.CREATE
        this.feature = feature
        return this
    }

    /**
     * Create a Naksha feature in current map.
     * @param mapId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier in which to create the feature.
     * @param feature the feature to create.
     * @return this.
     * @since 3.0
     */
    @JsName("createFeatureUsingIds")
    @JvmOverloads
    fun createFeature(mapId: String? = null, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.CREATE
        this.feature = feature
        return this
    }

    /**
     * Update a Naksha feature.
     * @param collection the collection in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0
     */
    fun updateFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.UPDATE
        this.feature = feature
        this.atomic = atomic
        return this
    }

    /**
     * Update a Naksha feature.
     * @param mapId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier in which to update the feature.
     * @param feature the new state of the feature.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0
     */
    @JsName("updateFeatureUsingIds")
    @JvmOverloads
    fun updateFeature(mapId: String? = null, collectionId: String, feature: NakshaFeature, atomic: Boolean): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.UPDATE
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
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.UPSERT
        this.feature = feature
        return this
    }

    /**
     * Update or create a Naksha feature in current map.
     * @param mapId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier in which to upsert the feature.
     * @param feature the new state of the feature.
     * @since 3.0
     */
    @JsName("upsertFeatureUsingIds")
    @JvmOverloads
    fun upsertFeature(mapId: String? = null, collectionId: String, feature: NakshaFeature): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.UPSERT
        this.feature = feature
        return this
    }

    /**
     * Delete a Naksha feature.
     * @param collection the collection from which to delete the feature.
     * @param feature the feature to delete.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0
     */
    fun deleteFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.DELETE
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
    @JvmOverloads
    fun deleteFeatureById(collection: NakshaCollection, id: String, version: Version? = null): Write {
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.DELETE
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Delete a feature by id.
     * @param mapId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier from which to delete the feature.
     * @param id the identifier of the feature to delete.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("deleteFeatureByIds")
    @JvmOverloads
    fun deleteFeatureById(mapId: String? = null, collectionId: String, id: String, version: Version? = null): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.DELETE
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Purge a Naksha feature.
     * @param collection the collection from which to purge the feature.
     * @param feature the feature to purge.
     * @param atomic if _true_, the [version] is read from the [XZY namespace][naksha.model.XyzNs] of the feature, so that the operation fails, if the currently existing feature is not exactly in this state. It is assumed, that when a client sends a new feature, it will not change the metadata, so the [XZY namespace][naksha.model.XyzNs], of the feature, except maybe for the tags.
     * @since 3.0
     */
    fun purgeFeature(collection: NakshaCollection, feature: NakshaFeature, atomic: Boolean): Write {
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.PURGE
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
    fun purgeFeatureById(collection: NakshaCollection, id: String, version: Version? = null): Write {
        this.mapId = collection.catalogId
        this.collectionId = collection.id
        this.op = WriteOp.PURGE
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Purge a feature by id.
     * @param mapId the map-identifier in which the collection is located; defaults to `NakshaContext.mapId()`.
     * @param collectionId the collection-identifier from which to purge the feature.
     * @param id the identifier of the feature to purge.
     * @param version if the operation should be performed atomic, the version that is expected to be deleted.
     * @return this.
     * @since 3.0
     */
    @JsName("purgeFeatureByIds")
    @JvmOverloads
    fun purgeFeatureById(mapId: String? = null, collectionId: String, id: String, version: Version? = null): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.PURGE
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Tests if this write modifies a dictionary.
     *
     * @return `true` if this write modifies a dictionary; `false` otherwise.
     * @since 3.0
     */
    fun isDictionaryModification(): Boolean = mapId == ADMIN_CATALOG_ID && collectionId == BOOKS_COL_ID

    /**
     * Tests if this write modifies a map.
     *
     * @return `true` if this write modifies a map; `false` otherwise.
     * @since 3.0
     */
    fun isMapModification(): Boolean = mapId == ADMIN_CATALOG_ID && collectionId == CATALOGS_COL_ID

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
    fun isFeatureModification(): Boolean = !isMapModification() && !isCollectionModification() && !isDictionaryModification()

    /**
     * Validate that this write is valid, invoked by [session's][naksha.model.ISession] before executing the writes.
     *
     * If [atomic] is _true_, and [version] is not _null_, then the _HEAD_ state of the modified feature must be in this version. This is invalid for [CREATE][WriteOp.CREATE].
     *
     * If the [op] is [UPSERT][WriteOp.UPSERT], the values of [atomic] and [version] are ignored.
     *
     * - Throws [ILLEGAL_STATE], if the write is invalid.
     * @since 3.0
     * @see [atomic]
     * @see [WriteOp]
     */
    fun validate(): Write {
        if (mapId == ADMIN_CATALOG_ID || collectionId == COLLECTIONS_COL_ID) {
            if (isInternalId(id)) {
                throw NakshaException(ILLEGAL_STATE, "Modification of internal features forbidden: '$id'")
            }
        }
        if (!Naksha.isValidId(id)) {
            throw NakshaException(ILLEGAL_STATE, "Invalid feature-id: '$id'")
        }
        return this
    }
}