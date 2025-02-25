@file:Suppress("OPT_IN_USAGE", "unused")

package naksha.model.request

import naksha.base.*
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.ADMIN_MAP
import naksha.model.Naksha.NakshaCompanion.COLLECTIONS_COL
import naksha.model.Naksha.NakshaCompanion.MAPS_COL
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.hashId
import naksha.model.Naksha.NakshaCompanion.isInternalId
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaDictionary
import naksha.model.objects.NakshaMap
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A write instruction for the storage.
 *
 * Modifications of features require to know the identifier of the feature, the collection-id of the collection in which the feature is stored, and the map-id of the map in which the collection is located.
 * @since 3.0
 */
@JsExport
open class Write : AnyObject() {

    companion object Write_C {
        private fun compareMapIds(a: String, b: String): Int {
            if (a == b) return 0
            // We order all modifications done in admin-map first.
            if (ADMIN_MAP == a) return -1
            if (ADMIN_MAP == b) return 1
            return a.compareTo(b)
        }

        private fun compareCollectionIds(a: String, b: String): Int {
            if (a == b) return 0
            // We order all modifications done in map's collection first (create maps first).
            if (MAPS_COL == a) return -1
            if (MAPS_COL == b) return 1
            // We order all modifications done in internal collection's-collection second.
            if (COLLECTIONS_COL == a) return -1
            if (COLLECTIONS_COL == b) return 1
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
         * **If writes are not ordered like this, this will lead to row-level locking in wrong order, causing deadlocks in the database!**
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
            val map_diff = compareMapIds(a.mapId, b.mapId)
            return if (map_diff == 0) {
                val col_diff = compareCollectionIds(a.collectionId, b.collectionId)
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
        private val MAP_ID = NotNullProperty<Write, String>(String::class) { _, _ -> NakshaContext.mapId() }
        private val COLLECTION_ID = NotNullProperty<Write, String>(String::class)
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
     * The identifier of the map to access, defaults to the [map of the current-context][NakshaContext.mapId].
     *
     * - If a [map][NakshaMap] should be modified, then use [Naksha.ADMIN_MAP].
     * - If a [dictionary][NakshaDictionary] should be modified, then use [Naksha.ADMIN_MAP].
     * @since 3.0
     */
    var mapId by MAP_ID

    /**
     * @see [mapId]
     */
    fun withMapId(value: String): Write {
        mapId = value
        return this
    }

    /**
     * The identifier of the collection to modify.
     *
     * - If a [map][NakshaMap] should be modified, then [Naksha.MAPS_COL] should be used, within [Naksha.ADMIN_MAP].
     * - If a [dictionary][NakshaDictionary] should be modified, the [Naksha.DICTIONARIES_COL] should be used, within [Naksha.ADMIN_MAP].
     * - If a [collection][NakshaCollection] should be modified, then [Naksha.COLLECTIONS_COL] should be used, must not be used together with [Naksha.ADMIN_MAP], because the admin-map does not allow collection modification, it is internally managed.
     * - If a [feature][NakshaFeature] should be created, then the [NakshaCollection] in which the feature should be stored is required.
     * - Throws [ILLEGAL_STATE], if the collection-id is read, before being set.
     * @since 3.0
     */
    var collectionId by COLLECTION_ID

    /**
     * @see [collectionId]
     */
    fun withCollectionId(value: String): Write {
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
            return feature?.properties?.xyz?.guid?.tupleNumber?.version
        }
        set(value) {
            if (value == null) removeRaw("version") else {
                versionValue = value
                versionRaw = value.txn
                setRaw("version", versionRaw)
            }
        }

    /**
     * @see [version]
     */
    fun withVersion(value: Version): Write {
        version = value
        return this
    }

    /**
     * If this write should be performed atomic.
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
     *
     * If the `id` differs from `feature.properties.xyz.guid.id`, then the feature is [forked][Operation.FORKED], this requires that the storage sets the [origin][Metadata.origin]. This is only valid for [CREATE][WriteOp.CREATE].
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
            val fn = feature?.properties?.xyz?.guid?.tupleNumber?.featureNumber
            if (fn != null) return fn
            val id = this.id
            // Calculating the feature-number is expensive, we need a MD5 hash, so cache the result.
            val cachedId = featureNumberId
            val cachedNumber = featureNumberValue
            if (id === cachedId && cachedNumber != null) return cachedNumber
            val number = featureNumber(hashId(id))
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
     * This method allows to reset the [featureNumber] to `null`, which basically means auto-generated, and is not supported by the default setter.
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
     * Arbitrary attachment to be stored.
     *
     * If not explicitly set _(so being undefined)_, returns `feature.attachment`.
     *
     * Setting the value explicitly to any value, even `null`, will force the attachment to be updated to that value. To keep the attachment in whatever state it is, the value should be `undefined`, which can be realized by calling [keepAttachment].
     *
     * The default state is `undefined`, so the attachment that exists in the storage is kept as it is _(unmodified)_.
     * @since 3.0
     */
    var attachment: ByteArray?
        get() {
            if (containsKey("attachment")) {
                val raw = getRaw("attachment")
                return if(raw is ByteArray) raw else null
            } else return feature?.attachment
        }
        set(value) {
            setRaw("attachment", value)
        }

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
        removeRaw("attachment")
        return this
    }

    /**
     * Tests if the attachment should stay in its current state.
     * @return `true` if the attachment is unchanged; `false` if the attachment should be modified.
     */
    fun shouldKeepAttachment(): Boolean = !containsKey("attachment")

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
     * Create a new dictionary.
     * @param dict the dictionary to create.
     * @return this.
     * @since 3.0
     */
    fun createDictionary(dict: NakshaDictionary): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = Naksha.DICTIONARIES_COL
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
        this.mapId = ADMIN_MAP
        this.collectionId = Naksha.DICTIONARIES_COL
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
        this.mapId = ADMIN_MAP
        this.collectionId = Naksha.DICTIONARIES_COL
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
        this.mapId = ADMIN_MAP
        this.collectionId = Naksha.DICTIONARIES_COL
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
        this.mapId = ADMIN_MAP
        this.collectionId = Naksha.DICTIONARIES_COL
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
    fun createMap(map: NakshaMap): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = MAPS_COL
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
    fun updateMap(map: NakshaMap, atomic: Boolean): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = MAPS_COL
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
    fun upsertMap(map: NakshaMap, atomic: Boolean): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = MAPS_COL
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
    fun deleteMap(map: NakshaMap, atomic: Boolean): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = MAPS_COL
        this.op = WriteOp.DELETE
        this.feature = map
        this.atomic = atomic
        return this
    }

    /**
     * Delete a map.
     * @param mapId the map to delete.
     * @param version the version to delete, if the deletion should be done atomic.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteMapById(mapId: String, version: Version? = null): Write {
        this.mapId = ADMIN_MAP
        this.collectionId = MAPS_COL
        this.op = WriteOp.DELETE
        this.id = mapId
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
        this.mapId = collection.mapId
        this.collectionId = COLLECTIONS_COL
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
        this.mapId = collection.mapId
        this.collectionId = COLLECTIONS_COL
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
        this.mapId = collection.mapId
        this.collectionId = COLLECTIONS_COL
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
        this.mapId = collection.mapId
        this.collectionId = COLLECTIONS_COL
        this.op = WriteOp.DELETE
        this.feature = collection
        this.atomic = atomic
        return this
    }

    /**
     * Delete a collection.
     * @param mapId the map-id of the map from which to delete the collection; see [NakshaContext.mapId].
     * @param collectionId the collection-id of the collection to delete.
     * @param version if the operation should be performed atomic, the version that is expected.
     * @since 3.0
     */
    @JvmOverloads
    fun deleteCollectionById(mapId: String = NakshaContext.mapId(), collectionId: String, version: Version? = null): Write {
        this.mapId = mapId
        this.collectionId = COLLECTIONS_COL
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
        this.mapId = collection.mapId
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
    fun createFeature(mapId: String = NakshaContext.mapId(), collectionId: String, feature: NakshaFeature): Write {
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
        this.mapId = collection.mapId
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
    fun updateFeature(mapId: String = NakshaContext.mapId(), collectionId: String, feature: NakshaFeature, atomic: Boolean): Write {
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
        this.mapId = collection.mapId
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
    fun upsertFeature(mapId: String = NakshaContext.mapId(), collectionId: String, feature: NakshaFeature): Write {
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
        this.mapId = collection.mapId
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
        this.mapId = collection.mapId
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
    fun deleteFeatureById(mapId: String = NakshaContext.mapId(), collectionId: String, id: String, version: Version? = null): Write {
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
        this.mapId = collection.mapId
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
        this.mapId = collection.mapId
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
    fun purgeFeatureById(mapId: String = NakshaContext.mapId(), collectionId: String, id: String, version: Version? = null): Write {
        this.mapId = mapId
        this.collectionId = collectionId
        this.op = WriteOp.PURGE
        this.id = id
        this.version = version
        this.atomic = version != null
        return this
    }

    /**
     * Tests if this write modifies a map.
     *
     * @return `true` if this write modifies a map; `false` otherwise.
     * @since 3.0
     */
    fun isMapModification(): Boolean = mapId == ADMIN_MAP && collectionId == MAPS_COL

    /**
     * Tests if this write modifies a collection.
     *
     * @return `true` if this write modifies a collection; `false` otherwise.
     * @since 3.0
     */
    fun isCollectionModification(): Boolean = collectionId == COLLECTIONS_COL

    /**
     * Tests if this write modifies a feature within a collection.
     *
     * @return `true` if this write modifies a feature within a collection; `false` otherwise.
     * @since 3.0
     */
    fun isFeatureModification(): Boolean = !isMapModification() && !isCollectionModification()

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
        if (mapId == ADMIN_MAP || collectionId == COLLECTIONS_COL) {
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