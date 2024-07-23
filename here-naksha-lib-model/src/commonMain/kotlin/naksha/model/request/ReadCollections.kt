@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Read collections request. Designed to return information from `naksha~collections`.
 */
@JsExport
class ReadCollections : ReadRequest<ReadCollections>() {
    /**
     * Ids of collections to read.
     */
    @JvmField
    var ids: MutableList<String> = mutableListOf()

    /**
     * Add the collection ID.
     * @param id the collection-id to add.
     * @return this.
     */
    fun addId(id: String): ReadCollections {
        this.ids.add(id)
        return this
    }

    /**
     * true - includes deleted features in search.
     * Default: false
     */
    @JvmField
    var queryDeleted: Boolean = false

    /**
     * Include deleted features.
     * @return this.
     */
    fun withQueryDeleted(): ReadCollections {
        this.queryDeleted = true
        return this
    }
}