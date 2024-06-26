package naksha.plv8

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
private const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 128

/**
 * Information about the database, that need only to be queried ones per session.
 * @property sql The SQL connection for which this information was returned.
 * @property pageSize The page-size of the database (`current_setting('block_size')`).
 * @property maxTupleSize The maximum size of a tuple (row).
 * @property brittleTableSpace The tablespace to use for storage-class "brittle"; if any.
 * @property tempTableSpace The tablespace to use for temporary tables and their indices; if any.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class PgDbInfo(val sql: IPgConnection) { // TODO: Rename sql into conn
    val pageSize: Int
    val maxTupleSize: Int
    val brittleTableSpace: String?
    val tempTableSpace: String?
    val gzipSupported: Boolean

    init {
        val row = (sql.rows(sql.execute("""
            SELECT 
                current_setting('block_size')::int4 as bs, 
                (select oid FROM pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE') as oid,
                (select oid FROM pg_extension WHERE extname = 'gzip') as gzip_oid
            """))!![0])
        pageSize = (row["bs"] as Int)
        val tupleSize = pageSize - 32
        maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
            MAX_POSTGRES_TOAST_TUPLE_TARGET
        } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
            MIN_POSTGRES_TOAST_TUPLE_TARGET
        } else {
            tupleSize
        }
        brittleTableSpace = if (row["oid"] != null) TEMPORARY_TABLESPACE else null
        tempTableSpace = brittleTableSpace
        gzipSupported = row["gzip_oid"] != null
    }
}