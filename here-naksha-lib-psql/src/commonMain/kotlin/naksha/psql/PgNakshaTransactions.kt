@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaException
import naksha.model.NakshaUtil
import kotlin.js.JsExport

/**
 * The internal transactions table.
 *
 */
@JsExport
class PgNakshaTransactions internal constructor(schema: PgSchema) : PgCollection(schema, NakshaUtil.VIRT_TRANSACTIONS), PgInternalCollection {

    /**
     * The transactions table.
     */
    var transactions: PgTransactions? = null
        get() {
            check(exists()) { throw NakshaException(COLLECTION_NOT_FOUND, "Collection '$id' does not exist", id = id) }
            return field
        }
        private set

    override fun refresh(connection: PgConnection?, noCache: Boolean): PgCollection {
        super.refresh(connection, noCache)
        // TODO: txnPartitions
        return this
    }
}