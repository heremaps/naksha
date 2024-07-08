package naksha.psql

import naksha.model.NakshaVersion
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private const val MAX_POSTGRES_TOAST_TUPLE_TARGET = 32736
private const val MIN_POSTGRES_TOAST_TUPLE_TARGET = 128

/**
 * Information about the database and connection, that need only to be queried ones per session.
 * @constructor Creates and initializes a new database information object.
 * @param conn the PostgresQL connection to be used to gather the database information. The connection will not be closed.
 * @param schema the schema to query information about.
 */
@Suppress("MemberVisibilityCanBePrivate")
@OptIn(ExperimentalJsExport::class)
@JsExport
class PgInfo(conn: PgConnection, schema: String) { // TODO: Rename sql into conn
    /**
     * The page-size of the database (`current_setting('block_size')`).
     */
    val pageSize: Int

    /**
     * The maximum size of a tuple (row).
     */
    val maxTupleSize: Int

    /**
     * The tablespace to use for storage-class "brittle"; if any.
     */
    val brittleTableSpace: String?

    /**
     * The tablespace to use for temporary tables and their indices; if any.
     */
    val tempTableSpace: String?

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip`
     * as standalone SQL function by the database.
     */
    val gzipSupported: Boolean

    /**
     * The PostgresQL version parsed into a [NakshaVersion].
     */
    val postgresVersion: NakshaVersion

    /**
     * The [OID](https://www.postgresql.org/docs/current/datatype-oid.html) (Object Identifier) of the schema.
     */
    val schemaOid: Int

    init {
        val cursor = conn.execute(
            """
            SELECT 
                current_setting('block_size')::int4 as bs, 
                (select oid FROM pg_tablespace WHERE spcname = '$TEMPORARY_TABLESPACE') as temp_oid,
                (select oid FROM pg_extension WHERE extname = 'gzip') as gzip_oid,
                (select oid FROM pg_namespace WHERE nspname = $1) as schema_oid,
                version() as version
            """, arrayOf(schema)
        ).fetch()
        pageSize = cursor["bs"]
        val tupleSize = pageSize - 32
        maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
            MAX_POSTGRES_TOAST_TUPLE_TARGET
        } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
            MIN_POSTGRES_TOAST_TUPLE_TARGET
        } else {
            tupleSize
        }
        brittleTableSpace = if (cursor.column("temp_oid") is Int) TEMPORARY_TABLESPACE else null
        schemaOid = cursor["schema_oid"]
        tempTableSpace = brittleTableSpace
        gzipSupported = cursor.column("gzip_oid") is Int
        // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
        val v: String = cursor["version"]
        val start = v.indexOf(' ')
        val end = v.indexOf(' ', start + 1)
        postgresVersion = NakshaVersion.of(v.substring(start + 1, end))
    }
}