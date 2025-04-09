@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A yearly partition of the transaction table.
 * @param transactions the transactions table.
 * @param year the year of this transactions table.
 * @since 3.0
 * @see [PgTransactions]
 */
@JsExport
class PgTransactionsYear(val transactions: PgTransactions, year: Int) : PgTable(
    transactions.collection, "${transactions.name}${PG_YEAR}$year", transactions.collection.storageClass, false,
    partitionOfTable = transactions, partitionOfValue = year
)