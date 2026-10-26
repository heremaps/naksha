package naksha.model.objects

import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.Id.IdCompanion.TRANSACTIONS_COL_ID
import naksha.base.TupleNumber
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAppId
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthor
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzCreatedAt
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * The autoconfigured representation of `naksha~transactions` collection in the `naksha~admin` catalog.
 * @since 3.0
 * @see NakshaAdmin
 */
@JsExport
class NakshaAdminTransactions @Deprecated("This constructor is for reflection only") constructor(): NakshaCollection() {
    @JsName("newAdminTransactions")
    constructor(databaseId: Id): this() {
        this.databaseId = databaseId.text
        catalogId = ADMIN_CATALOG_ID.text
        id = TRANSACTIONS_COL_ID.text
        tupleNumber = TupleNumber(databaseId, ADMIN_CATALOG_ID, COLLECTIONS_COL_ID, TRANSACTIONS_COL_ID, 1L)

        storeDeleted = StoreMode.ON
        storeHistory = StoreMode.ON
        partitions = 8
        members = MemberList.of(
        StandardMembers.PublishNumber,
            StandardMembers.PublishTime,
            StandardMembers.GlobalVersion,
            StandardMembers.Geometry,
            StandardMembers.Tags,
            XyzCreatedAt,
            XyzAppId,
            XyzAuthor,
        )
        indices = IndexList.of(
            StandardIndices.PublishNumber,
            StandardIndices.PublishTime,
            StandardIndices.GlobalVersion,
            StandardIndices.Geometry,
            StandardIndices.Tags,
            Index("created_at", XyzCreatedAt),
            Index("app_id", XyzAppId, XyzCreatedAt),
            Index("author", XyzAuthor, XyzCreatedAt),
        )
    }
}