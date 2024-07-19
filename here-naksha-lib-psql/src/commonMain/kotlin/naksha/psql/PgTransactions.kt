@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaErrorCode
import naksha.model.Txn
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * The TRANSACTIONS table, which is a special table that only exists in the [NakshaTransactions] collection. This table is partitioned by `txn` and replaces the HEAD table for the [NakshaTransactions] collection.
 * @param c the collection for which to create the HEAD table.
 */
@JsExport
class PgTransactions(c: PgCollection) : PgHead(c, c.id, PgStorageClass.Consistent, false, partitionBy = PgColumn.txn) {
    init {
        check(c is NakshaTransactions) { throwStorageException(NakshaErrorCode.ILLEGAL_ARGUMENT, "Expected NakshaTransactions") }
    }

    /**
     * All partitions, with key being the year (`txn >> 41`).
     */
    @JvmField
    val years: MutableMap<Int, PgTransactionsYear> = mutableMapOf()
    @JsName("getYear") operator fun get(txn: Txn): PgTransactionsYear? = years[txn.year()]
    @JsName("setYear") operator fun set(txn: Txn, partition: PgTransactionsYear) {
        years[txn.year()] = partition
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (entry in years) entry.value.create(conn)
    }
}