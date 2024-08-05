@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaException
import naksha.model.Version
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmField

/**
 * The TRANSACTIONS table, which is a special table that only exists in the [PgNakshaTransactions] collection. This table is partitioned by `txn` and replaces the HEAD table for the [PgNakshaTransactions] collection.
 * @param c the collection for which to create the HEAD table.
 */
@JsExport
class PgTransactions(c: PgCollection) : PgHead(c, "${c.id}${PG_HEAD}", PgStorageClass.Consistent, false, partitionBy = PgColumn.txn) {
    init {
        check(c is PgNakshaTransactions) { throw NakshaException(ILLEGAL_ARGUMENT, "Expected NakshaTransactions") }
    }

    /**
     * All partitions, with key being the year (`txn >> 41`).
     */
    @JvmField
    val years: MutableMap<Int, PgTransactionsYear> = mutableMapOf()
    @JsName("getYear") operator fun get(txn: Version): PgTransactionsYear? = years[txn.year()]
    @JsName("setYear") operator fun set(txn: Version, partition: PgTransactionsYear) {
        years[txn.year()] = partition
    }

    override fun create(conn: PgConnection) {
        super.create(conn)
        for (entry in years) entry.value.create(conn)
    }

    fun createYear(conn: PgConnection, year: Int) {
        if (year !in years) {
            val yearTable = PgTransactionsYear(this, year)
            yearTable.create(conn)
            years[year] = yearTable
            for (index in indices) yearTable.createIndex(conn, index)
        }
    }

    override fun createIndex(conn: PgConnection, index: PgIndex) {
        for (entry in years) entry.value.createIndex(conn, index)
    }

    override fun addIndex(index: PgIndex) {
        for (entry in years) entry.value.addIndex(index)
    }

    override fun removeIndex(index: PgIndex) {
        for (entry in years) entry.value.removeIndex(index)
    }

    override fun dropIndex(conn: PgConnection, index: PgIndex) {
        for (entry in years) entry.value.dropIndex(conn, index)
    }
}