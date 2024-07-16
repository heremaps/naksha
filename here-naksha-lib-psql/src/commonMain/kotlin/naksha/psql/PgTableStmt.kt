@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import kotlin.js.JsExport

/**
 * A table statement to create a collection table.
 * @param storage the storage for which to create the table statement, needed to create the [WITH] and the [CREATE_TABLE] parts.
 * @property storageClass the storage class to create.
 * @property partitions the number of partitions or _1_, if this is no partitioned table.
 * @property volatile _true_ if the table information is for a volatile table (often changing data).
 */
@JsExport
class PgTableStmt(val storage: PgStorage, val storageClass: PgStorageClass, val partitions: Int, val volatile: Boolean) {

    /**
     * The CREATE TABLE statement.
     */
    val CREATE_TABLE: String

    /**
     * The table body being the columns definitions.
     */
    val TABLE_BODY: String

    /**
     * The storage parameters for the table creations (WITH).
     */
    val WITH: String

    /**
     * Either empty String "", or tablespace query part "TABLESPACE tablespace_name" (always last value).
     */
    val TABLESPACE: String

    init {
        require(partitions in 1 .. 256) { "Partitions must be between 1 and 256, was $partitions" }
        when (storageClass) {
            PgStorageClass.Brittle -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE "
                TABLESPACE = if (storage.brittleTableSpace != null) " TABLESPACE ${storage.brittleTableSpace}" else ""
            }

            PgStorageClass.Temporary -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE "
                TABLESPACE = if (storage.tempTableSpace != null) " TABLESPACE ${storage.tempTableSpace}" else ""
            }

            else -> {
                CREATE_TABLE = "CREATE TABLE "
                TABLESPACE = ""
            }
        }
        TABLE_BODY = """(
    created_at   int8,
    updated_at   int8 NOT NULL,
    author_ts    int8,
    txn_next     int8,
    txn          int8 NOT NULL,
    ptxn         int8,
    uid          int4,
    puid         int4,
    hash         int4,
    change_count int4,
    geo_grid     int4,
    flags        int4,
    id           text STORAGE PLAIN NOT NULL COLLATE "C.UTF8",
    origin       text STORAGE PLAIN COLLATE "C.UTF8",
    app_id       text STORAGE PLAIN NOT NULL COLLATE "C.UTF8",
    author       text STORAGE PLAIN COLLATE "C.UTF8",
    type         text STORAGE PLAIN COLLATE "C.UTF8",
    tags         bytea STORAGE PLAIN,
    geo_ref      bytea STORAGE PLAIN,
    geo          bytea STORAGE EXTERNAL,
    feature      bytea STORAGE EXTERNAL
)"""

        WITH = " WITH (fillfactor=${if (volatile) "65" else "100"},toast_tuple_target=${storage.maxTupleSize},parallel_workers=${partitions}) "
    }
}