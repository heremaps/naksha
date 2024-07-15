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
     * Ids of collections to search.
     */
    @JvmField
    var ids: MutableList<String> = mutableListOf()

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

    fun withQueryDeleted(): ReadCollections {
        this.queryDeleted = true
        return this
    }
}