@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.StringList
import naksha.model.Naksha
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Read collections request. Designed to return information from `naksha~collections`.
 * @since 3.0.0
 */
@JsExport
open class ReadCollections() : ReadRequest() {
    /**
     * Create a new read-features request for the given collections.
     * @param collectionIds the collection identifiers.
     * @since 3.0.0
     */
    @JsName("of")
    constructor(vararg collectionIds: String) : this() {
       this.collectionIds.addAll(collectionIds)
    }

    companion object ReadCollections_C {
        private val STRING_LIST = NotNullProperty<ReadCollections, StringList>(StringList::class)
    }

    /**
     * Ids of collections to read.
     * @since 3.0.0
     */
    var collectionIds by STRING_LIST

    /**
     * Convert this request into a [ReadFeatures] request.
     *
     * Actually, reading collections is not different from reading features, because the storages will have a collection called `naksha~collections`, in which the [collection features][naksha.model.objects.NakshaCollection] are stored, or at least the storage will simulate this virtual collection.
     *
     * @return this request as [ReadFeatures] request.
     */
    fun toReadFeatures(): ReadFeatures {
        val req = ReadFeatures(Naksha.VIRT_COLLECTIONS)
        req.queryDeleted = false
        req.queryHistory = false
        req.featureIds.addAll(collectionIds)
        return req
    }
}