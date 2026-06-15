@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.StringList
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A request to read [map features][naksha.model.objects.NakshaCatalog] from a storage.
 * @since 3.0
 */
@JsExport
open class ReadMaps() : ReadRequest() {
    /**
     * Create a new read-features request for the given collections.
     * @param mapIds the map identifiers.
     * @since 3.0
     */
    @JsName("of")
    constructor(vararg mapIds: String) : this() {
        this.mapIds.addAll(mapIds)
    }

    companion object ReadMaps_C {
        private val STRING_LIST = NotNullProperty<ReadMaps, StringList>(StringList::class)
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
     * Actually, reading maps is not different from reading features, because the storages will have a collection called `naksha~catalogs` in the admin-map in which the [map features][naksha.model.objects.NakshaCatalog] are stored, or at least the storage will simulate this virtual collection.
     *
     * This is necessary, if you want a more fine-grained query, like filter maps or request past states of the map feature.
     * @return this request as [ReadFeatures] request.
     */
    fun toReadFeatures(): ReadFeatures {
        val req = ReadFeatures()
        req.mapId = Naksha.ADMIN_CATALOG_ID
        req.collectionIds.add(Naksha.CATALOGS_COL_ID)
        req.featureIds.addAll(mapIds)
        return req
    }
}