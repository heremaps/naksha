@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A helper class to create tables. To create the table, the statement basically must be:
 *
 * `"$CREATE_TABLE ${table.quotedName} $COLUMNS [partition by ...] $WITH $TABLESPACE"`
 *
 * Note that the partitioning is used to specify the number of parallel workers PostgresQL will use, when querying the table.
 * @param table the table for which to create the table statements.
 * @property storageClass the storage class to create.
 * @property partitions the number of partitions or _1_, if this is no partitioned table.
 * @property volatile _true_ if the table information is for a volatile table (often changing data).
 */
@JsExport
data class PgTableStmt(val table: PgTable, val storageClass: PgStorageClass, val partitions: Int, val volatile: Boolean) {
}