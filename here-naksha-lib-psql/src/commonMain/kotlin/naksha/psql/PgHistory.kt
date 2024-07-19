@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.Txn
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * The HISTORY table, partitioned by `txn_next`.
 */
@JsExport
class PgHistory(val head: PgHead) : PgTable(
    head.collection, "${head.name}\$hst", head.storageClass, false,
    partitionByColumn = PgColumn.txn_next
) {
    /**
     * All partitions, with key being the year (`txn >> 41`).
     */
    @JvmField
    val years: MutableMap<Int, PgHistoryYear> = mutableMapOf()
    @JsName("getYear")
    operator fun get(txn_next: Txn): PgHistoryYear? = years[txn_next.year()]
    @JsName("setYear")
    operator fun set(txn_next: Txn, partition: PgHistoryYear) {
        years[txn_next.year()] = partition
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (entry in years) entry.value.create(conn)
    }
}