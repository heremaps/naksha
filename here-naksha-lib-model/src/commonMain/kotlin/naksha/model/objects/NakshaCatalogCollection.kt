package naksha.model.objects

import naksha.base.Id.IdCompanion.ADMIN_CATALOG_ID
import naksha.base.Id.IdCompanion.COLLECTIONS_COL_ID
import naksha.base.TupleNumber
import kotlin.js.JsName

/**
 * The autoconfigured representation of `naksha~collections` collection in a [catalog][NakshaCatalog].
 * @since 3.0
 * @see NakshaCatalog
 */
class NakshaCatalogCollection @Deprecated("This constructor is for reflection only") constructor(): NakshaCollection() {
    /**
     * Creates the autoconfigured representation if `naksha~collections` collection in the given catalog.
     * @param catalog the catalog for which to autoconfigure a collections.
     * @since 3.0
     */
    @JsName("newNakshaCollections")
    constructor(catalog: NakshaCatalog): this() {
        databaseId = catalog.databaseId
        catalogId = catalog.id
        id = COLLECTIONS_COL_ID.text
        tupleNumber = TupleNumber(catalog.databaseNumber, ADMIN_CATALOG_ID.intValue, COLLECTIONS_COL_ID.intValue, COLLECTIONS_COL_ID.number, 1L)

        storeDeleted = StoreMode.ON
        storeHistory = StoreMode.ON
        partitions = 1
        members = MemberList.of(StandardMembers.Tags)
        indices = IndexList.of(StandardIndices.Tags)
    }
}