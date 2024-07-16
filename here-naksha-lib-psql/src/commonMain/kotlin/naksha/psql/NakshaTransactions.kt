@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * The internal transactions table.
 */
@JsExport
class NakshaTransactions internal constructor(schema: PgSchema) : PgInternalCollection(schema, ID) {
    companion object NakshaTransactionsCompanion {
        const val ID = "naksha~transactions"
    }
}