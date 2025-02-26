@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha.NakshaCompanion.ADMIN_MAP
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL
import naksha.model.Naksha.NakshaCompanion.TRANSACTIONS_COL_NUMBER
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the transactions of the storage.
 */
@JsExport
class PgNakshaTransactions internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(ADMIN_MAP)
    .withId(TRANSACTIONS_COL)
    .withNumber(TRANSACTIONS_COL_NUMBER)
    .withStoreDeleted(StoreMode.OFF)
    .withStoreHistory(StoreMode.OFF)
    .withStoreMeta(StoreMode.OFF)
), PgInternalCollection {

    /**
     * The transactions table, which actually is just [headTable].
     */
    val transactions: PgTransactions
        get() = headTable as PgTransactions
}
