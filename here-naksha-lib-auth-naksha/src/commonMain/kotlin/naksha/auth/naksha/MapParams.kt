@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.model.TagMap
import naksha.model.objects.NakshaMap
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The parameters for the operations when creating, reading, updating, or deleting a map _(CRUD)_.
 * @since 3.0
 * @see MapParamsList
 * @see NakshaOps.createMaps
 * @see NakshaOps.readMaps
 * @see NakshaOps.updateMaps
 * @see NakshaOps.deleteMaps
 */
@JsExport
class MapParams() : NakshaParams() {

    /**
     * Initialize parameters from the given map and storage.
     * @param map The map.
     * @param storage The storage in which the map is located.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(map: NakshaMap, storage: NakshaStorage) : this() {
        fromFeature(map)
        storageId = storage.id
        storageTags = storage.properties.xyz.tags.toTagMap()
    }

    companion object MapResourceCompanion {
        /**
         * The [PlatformType] of [MapParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<MapParams> = forKClass(MapParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<MapParams, String>(String_TYPE, autoRemove = true)
        private val TAG_MAP_MEMBER = NotNullProperty<NakshaParams, TagMap>(TagMap.TYPE) { _,_ -> TagMap() }
    }

    override fun withId(id: String?): MapParams = super.withId(id) as MapParams
    override fun withAppId(appId: String?): MapParams = super.withAppId(appId) as MapParams
    override fun withAuthor(author: String?): MapParams = super.withAuthor(author) as MapParams
    override fun withTags(tags: TagMap?): MapParams = super.withTags(tags) as MapParams
    override fun withSpaceIds(spaceIds: StringList?): MapParams = super.withSpaceIds(spaceIds) as MapParams

    /**
     * The `id` of the storage in which the map is located.
     * @since 3.0
     */
    var storageId: String? by STRING_MEMBER

    fun withStorageId(storageId: String?): MapParams {
        this.storageId = storageId
        return this
    }

    /**
     * The `tags` of the storage as [TagMap].
     * @since 3.0
     */
    var storageTags: TagMap by TAG_MAP_MEMBER

    fun withStorageTags(storageTags: TagMap?): MapParams {
        if (storageTags != null) this.storageTags = storageTags else this.storageTags.clear()
        return this
    }
}