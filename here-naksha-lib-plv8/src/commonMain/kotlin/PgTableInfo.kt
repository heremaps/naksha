@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Configuration of a table.
 * @property sql The SQL API of the session.
 * @property storageClass The storage class to create.
 */
@JsExport
class PgTableInfo(val sql: IPlv8Sql, val storageClass: String?) {
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
                TABLESPACE = if (sql.info().brittleTableSpace != null) " TABLESPACE ${sql.info().brittleTableSpace}" else ""
            }

            Static.SC_TEMPORARY -> {
                CREATE_TABLE = "CREATE UNLOGGED TABLE "
                TABLESPACE = if (sql.info().tempTableSpace != null) " TABLESPACE ${sql.info().tempTableSpace}" else ""
            }

            else -> {
                CREATE_TABLE = "CREATE TABLE "
                TABLESPACE = ""
            }
        }
        val builder = StringBuilder()
        builder.append(" (")
        if (Static.SC_TRANSACTIONS == storageClass) {
            builder.append("""
                created_at int8 NOT NULL,
                updated_at int8,""")
        } else {
            builder.append("""
                created_at int8,
                updated_at int8 NOT NULL,""")
        }
        builder.append("""
                author_ts   int8,
                txn_next    int8,
                txn         int8 NOT NULL,
                ptxn        int8,
                uid         int4,
                puid        int4,
                version     int4,
                geo_grid    int4,
                geo_type    int2,
                action      int2,
                app_id      text STORAGE PLAIN NOT NULL,
                author      text STORAGE PLAIN,
                type        text STORAGE PLAIN,
                id          text STORAGE PLAIN NOT NULL,
                feature     bytea STORAGE MAIN COMPRESSION lz4,
                tags        bytea STORAGE MAIN COMPRESSION lz4,
                geo         bytea STORAGE MAIN COMPRESSION lz4,
                geo_ref     bytea STORAGE MAIN COMPRESSION lz4
            ) """)
        CREATE_TABLE_BODY = builder.toString()

        builder.setLength(0)
        builder.append(" WITH (")
                .append("fillfactor=100")
                .append(",toast_tuple_target=").append(sql.info().pageSize)
                .append(",parallel_workers=").append(Static.PARTITION_COUNT)
                .append(") ")
        STORAGE_PARAMS = builder.toString()
    }
}