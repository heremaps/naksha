@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport

/**
 * The internal collection in the admin-map, that keeps track of the transactions of the storage.
 */
@JsExport
class PgNakshaTransactions internal constructor(adminMap: PgAdminMap) : PgCollection(adminMap, NakshaCollection()
    .withMapId(Naksha.ADMIN_MAP)
    .withId(Naksha.TRANSACTIONS_COL)
    .withNumber(Naksha.TRANSACTIONS_COL_NUMBER)
    .withDisableShadow(true)
    .withDisableHistory(true)
    .withDisableMeta(true)
), PgInternalCollection {

    /**
     * The transactions table, which actually is just [head].
     */
    val transactions: PgTransactions
        get() = head as PgTransactions
}
