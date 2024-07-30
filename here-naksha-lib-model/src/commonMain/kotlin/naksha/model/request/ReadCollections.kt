@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.NotNullProperty
import naksha.base.StringList
import naksha.model.NakshaUtil
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
    @JsName("forCollections")
    constructor(vararg collectionIds: String) : this() {
       this.collectionIds.addAll(collectionIds)
    }

    companion object ReadCollections_C {
        private val STRING_LIST = NotNullProperty<ReadCollections, StringList>(StringList::class)
        private val BOOLEAN = NotNullProperty<ReadCollections, Boolean>(Boolean::class) { _, _ -> false }
    }

    /**
     * Ids of collections to read.
     * @since 3.0.0
     */
    var collectionIds by STRING_LIST

    /**
     * Extend the request to search through deleted features.
     */
    var queryDeleted by BOOLEAN

    /**
     * Extend the request to search through historic states of features.
     */
    var queryHistory by BOOLEAN

    /**
     * Convert this request into a [ReadFeatures] request.
     *
     * Actually, reading collections is not different from reading features, because the storages will have a collection called `naksha~collections`, in which the [collection features][naksha.model.objects.NakshaCollection] are stored, or at least the storage will simulate this virtual collection.
     *
     * @return this request as [ReadFeatures] request.
     */
    fun toReadFeatures(): ReadFeatures {
        val req = ReadFeatures(NakshaUtil.VIRT_COLLECTIONS)
        req.queryDeleted = queryDeleted
        req.queryHistory = queryHistory
        req.featureIds.addAll(collectionIds)
        return req
    }
}