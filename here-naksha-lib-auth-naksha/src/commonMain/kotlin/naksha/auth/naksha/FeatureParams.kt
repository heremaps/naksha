@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.model.TagMap
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The parameters for the operations when creating, reading, updating, or deleting a feature _(CRUD)_.
 * @since 3.0
 * @see FeatureParamsList
 * @see NakshaOps.createFeatures
 * @see NakshaOps.readFeatures
 * @see NakshaOps.updateFeatures
 * @see NakshaOps.deleteFeatures
 */
@JsExport
class FeatureParams() : NakshaParams() {

    /**
     * Initialize parameters from the given collection, map, and storage.
     * @param feature The feature.
     * @param collection The collection in which the feature is located.
     * @param map The map in which the feature is located.
     * @param storage The storage in which the feature is located.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(feature: NakshaFeature, collection: NakshaCollection, map: NakshaMap, storage: NakshaStorage) : this() {
        fromFeature(collection)
        collectionId = collection.id
        collectionTags = collection.properties.xyz.tags.toTagMap()
        mapId = map.id
        mapTags = map.properties.xyz.tags.toTagMap()
        storageId = storage.id
        storageTags = storage.properties.xyz.tags.toTagMap()
    }

    companion object FeatureParamsCompanion {
        /**
         * The [PlatformType] of [FeatureParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<FeatureParams> = forKClass(FeatureParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<FeatureParams, String>(String_TYPE, autoRemove = true)
        private val TAG_MAP_MEMBER = NotNullProperty<FeatureParams, TagMap>(TagMap.TYPE) { _,_ -> TagMap() }
    }

    override fun withId(id: String?): FeatureParams = super.withId(id) as FeatureParams
    override fun withAppId(appId: String?): FeatureParams = super.withAppId(appId) as FeatureParams
    override fun withAuthor(author: String?): FeatureParams = super.withAuthor(author) as FeatureParams
    override fun withTags(tags: TagMap?): FeatureParams = super.withTags(tags) as FeatureParams
    override fun withSpaceIds(spaceIds: StringList?): FeatureParams = super.withSpaceIds(spaceIds) as FeatureParams

    /**
     * The `id` of the storage in which the feature is located.
     * @since 3.0
     */
    var storageId: String? by STRING_MEMBER

    fun withStorageId(storageId: String?): FeatureParams {
        this.storageId = storageId
        return this
    }

    /**
     * The `tags` of the storage as [TagMap].
     * @since 3.0
     */
    var storageTags: TagMap by TAG_MAP_MEMBER

    fun withStorageTags(storageTags: TagMap?): FeatureParams {
        if (storageTags != null) this.storageTags = storageTags else this.storageTags.clear()
        return this
    }

    /**
     * The `id` of the map in which the feature is located.
     * @since 3.0
     */
    var mapId: String? by STRING_MEMBER

    fun withMapId(mapId: String?): FeatureParams {
        this.mapId = mapId
        return this
    }

    /**
     * The `tags` of the map as [TagMap].
     * @since 3.0
     */
    var mapTags: TagMap by TAG_MAP_MEMBER

    fun withMapTags(mapTags: TagMap?): FeatureParams {
        if (mapTags != null) this.mapTags= mapTags else this.mapTags.clear()
        return this
    }

    /**
     * The `id` of the collection in which the feature is located.
     * @since 3.0
     */
    var collectionId: String? by STRING_MEMBER

    fun withCollectionId(collectionId: String?): FeatureParams {
        this.collectionId = collectionId
        return this
    }

    /**
     * The `tags` of the collection as [TagMap].
     * @since 3.0
     */
    var collectionTags: TagMap by TAG_MAP_MEMBER

    fun withCollectionTags(collectionTags: TagMap?): FeatureParams {
        if (collectionTags != null) this.collectionTags = collectionTags else this.collectionTags.clear()
        return this
    }
}