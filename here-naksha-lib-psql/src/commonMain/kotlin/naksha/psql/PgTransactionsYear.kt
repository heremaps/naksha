@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

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
) {
    companion object PgTransactionsYear_C {
        /**
         * The [PlatformType] of [PgTransactionsYear].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(PgTransactionsYear::class).withPackageName(PACKAGE_NAME)
    }
}