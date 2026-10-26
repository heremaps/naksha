package naksha.model.objects

import naksha.base.ADMIN_CATALOG_TEXT
import naksha.base.BOOKS_COL_TEXT
import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.BOOKS_COL_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.TupleNumber
import kotlin.js.JsName

/**
 * The autoconfigured representation of `naksha~books` collection in the `naksha~admin` catalog.
 * @since 3.0
 * @see NakshaAdmin
 */
class NakshaAdminBooks @Deprecated("This constructor is for reflection only") constructor(): NakshaCollection() {
    @JsName("newAdminBooks")
    constructor(databaseId: Id): this() {
        this.databaseId = databaseId.text
        catalogId = ADMIN_CATALOG_ID.text
        id = BOOKS_COL_ID.text
        tupleNumber = TupleNumber(databaseId, ADMIN_CATALOG_ID, COLLECTIONS_COL_ID, BOOKS_COL_ID, 1L)

        catalogId = ADMIN_CATALOG_TEXT
        id = BOOKS_COL_TEXT
        storeDeleted = StoreMode.ON
        storeHistory = StoreMode.ON
        partitions = 1
        members = MemberList.of(StandardMembers.Tags)
        indices = IndexList.of(StandardIndices.Tags)
    }
}
