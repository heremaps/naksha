@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A request to read [collection features][naksha.model.objects.NakshaCollection] from a map of the storage.
 *
 * **Hint**: To query multiple maps, it is recommended to simply open multiple sessions in parallel, query for the collections, then join the results.
 * @since 3.0.0
 */
@JsExport
open class ReadCollections : ReadRequest() {
    companion object ReadCollections_C {
        private val STRING_OR_NULL = NullableProperty<ReadCollections, String>(String::class)
        private val STRING_LIST = NotNullProperty<ReadCollections, StringList>(StringList::class)
    }

    /**
     * The id of the map from which to read.
     *
     * @since 3.0
     */
    var mapId by STRING_OR_NULL

    /**
     * @see [mapId]
     */
    open fun withMapId(value: String?): ReadCollections {
        mapId = value
        return this
    }

    /**
     * Ids of collections to read.
     * @since 3.0
     */
    var collectionIds by STRING_LIST

    /**
     * Adds the given collection-id into [collectionIds], if it is not already in it.
     * @param collectionId the collection-id to add.
     * @return this.
     * @since 3.0
     */
    open fun addCollectionId(collectionId: String?): ReadCollections {
        if (!collectionIds.contains(collectionId)) collectionIds.add(collectionId)
        return this
    }

    /**
     * Adds the given collection-ids into [collectionIds], if it is not already in it.
     * @param collectionIds the collection-ids to add.
     * @return this.
     * @since 3.0
     */
    open fun addCollectionIds(vararg collectionIds: String): ReadCollections {
        val ids = this.collectionIds
        @Suppress("SENSELESS_COMPARISON")
        if (collectionIds != null && collectionIds.isNotEmpty()) {
            for (id in collectionIds) if (!ids.contains(id)) ids.add(id)
        }
        return this
    }

    /**
     * Convert this request into a [ReadFeatures] request.
     *
     * Actually, reading collections is not different from reading features, because the storages will have a collection called `naksha~collections`, in which the [collection features][naksha.model.objects.NakshaCollection] are stored, or at least the storage will simulate this virtual collection.
     *
     * This is necessary, if you want a more fine-grained query, like filter collections or request past states of the collection feature.
     *
     * @return this request as [ReadFeatures] request.
     */
    fun toReadFeatures(): ReadFeatures {
        val req = ReadFeatures()
        req.mapId = mapId
        req.collectionIds.add(Naksha.COLLECTIONS_COL)
        req.featureIds.addAll(collectionIds)
        return req
    }
}