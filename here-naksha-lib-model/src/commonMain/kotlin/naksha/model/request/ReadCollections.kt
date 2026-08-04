@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.Id
import naksha.base.illegalState
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.StandardMembers.StandardMembers_C.IdMember
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A request to read [collection features][naksha.model.objects.NakshaCollection] from a [catalog][NakshaCatalog].
 * @since 3.0.0
 */
@JsExport
open class ReadCollections() : ReadFeatures() {

    /**
     * Create a request to read collections from the catalog.
     *
     * When no limits are set, this will read all collections of the catalog.
     * @param catalog the catalog to read from.
     * @since 3.0
     */
    @JsName("of")
    constructor(catalog: NakshaCatalog) : this() {
        databaseId = catalog.databaseId
        catalogId = catalog.id
        collectionId = Id.COLLECTIONS_COL_ID
    }


    /**
     * Read the collection with the given `id`.
     * @param collectionId the `id` of the collection to read.
     * @return this.
     * @since 3.0
     */
    open fun readCollection(collectionId: Id): ReadCollections {
        val q = memberQuery
        if (q == null) {
            memberQuery = IsAnyOf(IdMember.id, collectionId.text)
            return this
        }
        if (q is IsAnyOf && q.at == IdMember.id) {
            if (!q.items.contains(collectionId.text)) q.items.add(collectionId.text)
            return this
        }
        if (q is Equals && q.at == IdMember.id) {
            val existing = q.value
            if (existing != collectionId.text && existing != null) {
                memberQuery = IsAnyOf(IdMember.id, existing, collectionId.text)
                return this
            }
        }
        throw illegalState("Cannot find 'id' query")
    }

    /**
     * Read the collections with the given `id`'s.
     * @param collectionIds the `id`'s of the collections to read.
     * @return this.
     * @since 3.0
     */
    open fun readCollections(vararg collectionIds: Id): ReadCollections {
        for (i in 0 until collectionIds.size) {
            val collectionId = collectionIds[i]
            readCollection(collectionId)
        }
        return this
    }
}