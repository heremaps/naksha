@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.TagMap
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The parameters for the operations when creating, reading, updating, or deleting a collection _(CRUD)_.
 * @since 3.0
 * @see CollectionParamsList
 * @see NakshaOps.createCollections
 * @see NakshaOps.readCollections
 * @see NakshaOps.updateCollections
 * @see NakshaOps.deleteCollections
 */
@JsExport
class CollectionParams() : NakshaParams() {

    /**
     * Auto generate parameters for operation done to a collection.
     * @param collection The collection to perform the operation upon.
     * @param map The map in which the collection is located.
     * @param storage The storage in which the collection is located.
     * @return this.
     * @since 3.0
     * @see NakshaOps.createCollections
     * @see NakshaOps.readCollections
     * @see NakshaOps.updateCollections
     * @see NakshaOps.deleteCollections
     */
    @JsName("of")
    constructor(collection: NakshaCollection, map: NakshaMap, storage: NakshaStorage) : this() {
        fromFeature(collection)
        mapId = map.id
        mapTags = map.properties.xyz.tags.toTagMap()
        storageId = storage.id
        storageTags = storage.properties.xyz.tags.toTagMap()
    }

    companion object CollectionParams_C {
        /**
         * The [PlatformType] of [CollectionParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<CollectionParams> = forKClass(CollectionParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<CollectionParams, String>(String_TYPE, autoRemove = true)
        private val TAG_MAP_MEMBER = NotNullProperty<CollectionParams, TagMap>(TagMap.TYPE) { _,_ -> TagMap() }
    }

    override fun withId(id: String?): CollectionParams = super.withId(id) as CollectionParams
    override fun withAppId(appId: String?): CollectionParams = super.withAppId(appId) as CollectionParams
    override fun withAuthor(author: String?): CollectionParams = super.withAuthor(author) as CollectionParams
    override fun withTags(tags: TagMap?): CollectionParams = super.withTags(tags) as CollectionParams
    override fun withSpaceIds(spaceIds: StringList?): CollectionParams = super.withSpaceIds(spaceIds) as CollectionParams

    /**
     * The `id` of the storage in which the collection is located.
     * @since 3.0
     */
    var storageId: String? by STRING_MEMBER

    fun withStorageId(storageId: String?): CollectionParams {
        this.storageId = storageId
        return this
    }

    /**
     * The `tags` of the storage as [TagMap].
     * @since 3.0
     */
    var storageTags: TagMap by TAG_MAP_MEMBER

    fun withStorageTags(storageTags: TagMap?): CollectionParams {
        if (storageTags != null) this.storageTags = storageTags else this.storageTags.clear()
        return this
    }

    /**
     * The `id` of the map in which the collection is located.
     * @since 3.0
     */
    var mapId: String? by STRING_MEMBER

    fun withMapId(mapId: String?): CollectionParams {
        this.mapId = mapId
        return this
    }

    /**
     * The `tags` of the map as [TagMap].
     * @since 3.0
     */
    var mapTags: TagMap by TAG_MAP_MEMBER

    fun withMapTags(mapTags: TagMap?): CollectionParams {
        if (mapTags != null) this.mapTags = mapTags else this.mapTags.clear()
        return this
    }


}