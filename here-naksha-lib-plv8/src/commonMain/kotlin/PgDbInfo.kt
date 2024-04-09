package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.get
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
private const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 128

/**
 * Information about the database, that need only to be queried ones per session.
 * @property sql The SQL API.
 * @property pageSize The page-size of the database (`current_setting('block_size')`).
 * @property brittleTableSpace The tablespace to use for storage-class "brittle"; if any.
 * @property tempTableSpace The tablespace to use for temporary tables and their indices; if any.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class PgDbInfo(val sql: IPlv8Sql) {
    val pageSize : Int by lazy {
        val settings = asMap(sql.rows(sql.execute("SELECT current_setting('block_size') as bs"))!![0])
        val blockSizeStr: String? = settings["bs"]
        val blockSize = blockSizeStr?.toInt() ?: 8192
        if (blockSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
            MAX_POSTGRES_TOAST_TUPLE_TARGET
        } else if (blockSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
            MIN_POSTGRES_TOAST_TUPLE_TARGET
        } else {
            blockSize
        }
    }
    val brittleTableSpace: String? by lazy {
        val rows = sql.rows(sql.execute("select oid from pg_tablespace where spcname = '$TEMPORARY_TABLESPACE';"))
        if (rows.isNullOrEmpty()) {
            null
        } else {
            TEMPORARY_TABLESPACE
        }
    }
    val tempTableSpace: String? by lazy { brittleTableSpace }
}