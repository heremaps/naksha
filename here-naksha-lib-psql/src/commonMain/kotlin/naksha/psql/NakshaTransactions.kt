@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaErrorCode.StorageErrorCompanion.COLLECTION_NOT_FOUND
import kotlin.js.JsExport

/**
 * The internal transactions table.
 *
 */
@JsExport
class NakshaTransactions internal constructor(schema: PgSchema) : PgCollection(schema, ID), PgInternalCollection {
    companion object NakshaTransactionsCompanion {
        const val ID = "naksha~transactions"
    }

    /**
     * The transactions table.
     */
    var transactions: PgTransactions? = null
        get() {
            check(exists()) { throwStorageException(COLLECTION_NOT_FOUND, id = id) }
            return field
        }
        private set

    override fun refresh(connection: PgConnection?, noCache: Boolean): PgCollection {
        super.refresh(connection, noCache)
        // TODO: txnPartitions
        return this
    }
}