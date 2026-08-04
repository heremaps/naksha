@file:OptIn(ExperimentalJsExport::class)

package naksha.model.request

import naksha.base.Id
import naksha.base.illegalState
import naksha.model.objects.NakshaDatabase
import naksha.model.objects.StandardMembers.StandardMembers_C.IdMember
import naksha.model.request.ops.Equals
import naksha.model.request.ops.IsAnyOf
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A request to read [catalog features][naksha.model.objects.NakshaCatalog] from a storage.
 * @since 3.0
 */
@JsExport
open class ReadCatalogs() : ReadFeatures() {
    /**
     * Create a request to read catalogs from the database.
     *
     * When no limits are set, this will read all catalogs.
     * @param database the database to read from.
     * @since 3.0
     */
    @JsName("of")
    constructor(database: NakshaDatabase) : this() {
        databaseId = database.id
        catalogId = Id.ADMIN_CATALOG_ID
        collectionId = Id.CATALOGS_COL_ID
    }

    /**
     * Read the catalog with the given `id`.
     * @param catalogId the `id` of the catalog to read.
     * @return this.
     * @since 3.0
     */
    open fun readCatalog(catalogId: Id): ReadCatalogs {
        val q = memberQuery
        if (q == null) {
            memberQuery = IsAnyOf(IdMember.id, catalogId.text)
            return this
        }
        if (q is IsAnyOf && q.at == IdMember.id) {
            if (!q.items.contains(catalogId.text)) q.items.add(catalogId.text)
            return this
        }
        if (q is Equals && q.at == IdMember.id) {
            val existing = q.value
            if (existing != catalogId.text && existing != null) {
                memberQuery = IsAnyOf(IdMember.id, existing, catalogId.text)
                return this
            }
        }
        throw illegalState("Cannot find 'id' query")
    }

    /**
     * Read the catalogs with the given `id`'s.
     * @param catalogIds the `id`'s of the catalogs to read.
     * @return this.
     * @since 3.0
     */
    open fun readCatalogs(vararg catalogIds: Id): ReadCatalogs {
        for (i in 0 until catalogIds.size) {
            val catalogId = catalogIds[i]
            readCatalog(catalogId)
        }
        return this
    }
}