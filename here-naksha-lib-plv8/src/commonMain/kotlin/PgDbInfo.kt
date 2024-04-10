package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.containsKey
import com.here.naksha.lib.jbon.getAny
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
private const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 128

/**
 * Information about the database, that need only to be queried ones per session.
 * @property sql The SQL API.
 * @property pageSize The page-size of the database (`current_setting('block_size')`).
 * @property maxTupleSize The maximum size of a tuple (row).
 * @property brittleTableSpace The tablespace to use for storage-class "brittle"; if any.
 * @property tempTableSpace The tablespace to use for temporary tables and their indices; if any.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class PgDbInfo(val sql: IPlv8Sql) {
    val pageSize: Int
    val maxTupleSize : Int
    val brittleTableSpace: String?
    val tempTableSpace: String?
    init {
        val row = asMap(sql.rows(sql.execute("SELECT current_setting('block_size')::int4 as bs, oid FROM pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE';"))!![0])
        pageSize = (row.getAny("bs") as Int)
        val tupleSize = pageSize - 32
        maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
            MAX_POSTGRES_TOAST_TUPLE_TARGET
        } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
            MIN_POSTGRES_TOAST_TUPLE_TARGET
        } else {
            tupleSize
        }
        brittleTableSpace = if (row.containsKey("oid")) TEMPORARY_TABLESPACE else null
        tempTableSpace = brittleTableSpace
    }
}