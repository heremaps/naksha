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
    head.collection, "${head.collection.id}${PG_HST}", head.storageClass, false,
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

    fun createYear(conn: PgConnection, year: Int) {
        if (year !in years) {
            val yearTable = PgHistoryYear(this, year)
            yearTable.create(conn)
            years[year] = yearTable
            for (index in indices) yearTable.createIndex(conn, index)
        }
    }

    override fun addIndex(index: PgIndex) {
        for (entry in years) entry.value.addIndex(index)
    }

    override fun removeIndex(index: PgIndex) {
        for (entry in years) entry.value.removeIndex(index)
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        for (entry in years) entry.value.createIndex(conn, index)
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        for (entry in years) entry.value.dropIndex(conn, index)
    }
}