package naksha.model.objects

import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.TupleNumber
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The autoconfigured representation of `naksha~collections` collection in the `naksha~admin` catalog.
 * @since 3.0
 * @see NakshaAdmin
 */
@JsExport
class NakshaAdminCollections @Deprecated("This constructor is for reflection only") constructor(): NakshaCollection() {
    @JsName("newAdminCollections")
    constructor(databaseId: Id): this() {
        this.databaseId = databaseId.text
        catalogId = ADMIN_CATALOG_ID.text
        id = COLLECTIONS_COL_ID.text
        tupleNumber = TupleNumber(databaseId, ADMIN_CATALOG_ID, COLLECTIONS_COL_ID, COLLECTIONS_COL_ID, 1L)

        storeDeleted = StoreMode.ON
        storeHistory = StoreMode.ON
        partitions = 1
        members = MemberList.of(StandardMembers.Tags)
        indices = IndexList.of(StandardIndices.Tags)
    }
}