@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.StringList
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A request to read [map features][naksha.model.objects.NakshaMap] from a storage.
 * @since 3.0
 */
@JsExport
open class ReadMaps() : ReadRequest() {
    /**
     * Create a new read-features request for the given collections.
     * @param mapIds the map identifiers.
     * @since 3.0
     */
    @JsName("ReadMapsOf")
    constructor(vararg mapIds: String) : this() {
        this.mapIds.addAll(mapIds)
    }

    companion object ReadMaps_C {
        /**
         * The [PlatformType] of [ReadMaps].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ReadMaps::class).withPackageName(PACKAGE_NAME)

        private val STRING_LIST = NotNullProperty<ReadMaps, StringList>(StringList.TYPE)
    }

    /**
     * Ids of maps to read.
     * @since 3.0
     */
    var mapIds by STRING_LIST

    /**
     * Adds the given map-id into [mapIds], if it is not already in it.
     * @param mapId the map-id to add.
     * @return this.
     * @since 3.0
     */
    open fun addMapsId(mapId: String?): ReadMaps {
        if (!mapIds.contains(mapId)) mapIds.add(mapId)
        return this
    }

    /**
     * Adds the given map-ids into [mapIds], if it is not already in it.
     * @param mapIds the map-ids to add.
     * @return this.
     * @since 3.0
     */
    open fun addMapsIds(vararg mapIds: String): ReadMaps {
        val ids = this.mapIds
        @Suppress("SENSELESS_COMPARISON")
        if (mapIds != null && mapIds.isNotEmpty()) {
            for (id in mapIds) if (!ids.contains(id)) ids.add(id)
        }
        return this
    }

    /**
     * Convert this request into a [ReadFeatures] request.
     *
     * Actually, reading maps is not different from reading features, because the storages will have a collection called `naksha~maps` in the admin-map in which the [map features][naksha.model.objects.NakshaMap] are stored, or at least the storage will simulate this virtual collection.
     *
     * This is necessary, if you want a more fine-grained query, like filter maps or request past states of the map feature.
     * @return this request as [ReadFeatures] request.
     */
    fun toReadFeatures(): ReadFeatures {
        val req = ReadFeatures()
        req.mapId = Naksha.ADMIN_MAP
        req.collectionIds.add(Naksha.MAPS_COL)
        req.featureIds.addAll(mapIds)
        return req
    }
}