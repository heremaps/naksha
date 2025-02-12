@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.model.Naksha
import kotlin.js.JsExport

/**
 * Perform a read from the transaction log to query for [transaction features][naksha.model.objects.NakshaTransaction].
 * @since 3.0
 */
@JsExport
open class ReadTransactions : ReadFeatures() {
    init {
        mapId = Naksha.ADMIN_MAP
        collectionIds.add(Naksha.TRANSACTIONS_COL)
    }
}
