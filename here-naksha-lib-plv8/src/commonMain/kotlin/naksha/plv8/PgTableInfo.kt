@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Configuration of a table.
 * @property conn The SQL API of the session.
 * @property storageClass The storage class to create.
 * @property partitionCount ?
 */
@JsExport
class PgTableInfo(val conn: PgSession, val storageClass: String?, val partitionCount: Int) { // TODO: Rename sql to conn
    /**
     * The CREATE TABLE statement.
     */
    val CREATE_TABLE: String

    /**
     * The table body being the columns definitions.
     */
    val CREATE_TABLE_BODY: String

    /**
     * The storage parameters for the table creations (WITH).
     */
    val STORAGE_PARAMS: String

    /**
     * Either empty String "", or tablespace query part "TABLESPACE tablespace_name" (always last value).
     */
    val TABLESPACE: String

    init {
        when (storageClass) {
            Static.SC_BRITTLE -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE "
                TABLESPACE = if (conn.info().brittleTableSpace != null) " TABLESPACE ${conn.info().brittleTableSpace}" else ""
            }

            Static.SC_TEMPORARY -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE "
                TABLESPACE = if (conn.info().tempTableSpace != null) " TABLESPACE ${conn.info().tempTableSpace}" else ""
            }

            else -> {
                CREATE_TABLE = "CREATE TABLE "
                TABLESPACE = ""
            }
        }

        val featureCompression = if (conn.info().gzipSupported) "EXTERNAL" else DEFAULT_FEATURE_STORAGE

        val builder = StringBuilder()
        builder.append(" (")
        builder.append("""
            created_at int8,
            updated_at int8 NOT NULL,""")
        builder.append("""
                author_ts   int8,
                txn_next    int8,
                txn         int8 NOT NULL,
                ptxn        int8,
                uid         int4,
                puid        int4,
                fnva1       int4,
                version     int4,
                geo_grid    int4,
                flags       int4,
                action      int2,
                app_id      text STORAGE PLAIN NOT NULL,
                author      text STORAGE PLAIN,
                type        text STORAGE PLAIN,
                id          text STORAGE PLAIN NOT NULL,
                feature     bytea STORAGE $featureCompression,
                tags        bytea STORAGE EXTERNAL,
                geo         bytea STORAGE EXTERNAL,
                geo_ref     bytea STORAGE EXTERNAL
            ) """)
        CREATE_TABLE_BODY = builder.toString()

        builder.setLength(0)
        builder.append(" WITH (")
                .append("fillfactor=100")
                .append(",toast_tuple_target=").append(conn.info().maxTupleSize)
                .append(",parallel_workers=").append(partitionCount)
                .append(") ")
        STORAGE_PARAMS = builder.toString()
    }

    companion object {
        const val DEFAULT_FEATURE_STORAGE = "MAIN COMPRESSION lz4"
    }
}