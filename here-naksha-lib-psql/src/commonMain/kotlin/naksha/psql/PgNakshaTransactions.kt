@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the transactions of the storage.
 */
@JsExport
class PgNakshaTransactions internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_MAP)
    .withId(Naksha.TRANSACTIONS_COL)
    .withNumber(Naksha.TRANSACTIONS_COL_NUMBER)
    .withStoreDeleted(StoreMode.OFF)
    .withStoreHistory(StoreMode.OFF)
    .withStoreMeta(StoreMode.OFF)
), PgInternalCollection {

    /**
     * The transactions table, which actually is just [head].
     */
    val transactions: PgTransactions
        get() = head as PgTransactions
}
