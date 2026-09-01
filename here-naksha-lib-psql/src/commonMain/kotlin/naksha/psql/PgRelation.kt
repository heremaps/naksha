@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.psql.PgUtil.PgUtilCompanion.quoteLiteral
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Information extracted from the [pg_class](https://www.postgresql.org/docs/current/catalog-pg-class.html) table, about relations of a collection.
 * @property oid the OID of the relation (`oid`).
 * @property rel_name the name of the relation (`relname`).
 * @property schema_oid the OID of the schema in which the relation is located (`relnamespace`).
 * @property schema_name the name of the schema in which the relation is located, subselected from [pg_namespace](https://www.postgresql.org/docs/current/catalog-pg-namespace.html) using [schema_oid].
 * @property kind the kind of relation (`relkind`).
 * @property storageClass the storage class of the relation.
 * @property tablespace_oid the OID of the tablespace in which the relation is stored (`reltablespace`).
 */
@JsExport
data class PgRelation(
    @JvmField val oid: Int,
    @JvmField val rel_name: String,
    @JvmField val schema_oid: Int,
    @JvmField val schema_name: String,
    @JvmField val kind: PgKind,
    @JvmField val storageClass: PgStorageClass,
    @JvmField val tablespace_oid: Int
) {
    /**
     * Create an information row from the given cursor, that need to be a cursor as returned by [select].
     * @param cursor the cursor as returned by [select].
     */
    @JsName("fromCursor")
    constructor(cursor: PgCursor) : this(
        oid = cursor["oid"],
        rel_name = cursor["relname"],
        schema_oid = cursor["schema_oid"],
        schema_name = cursor["schema_name"],
        kind = PgKind.of(cursor.column("kind") as String),
        storageClass = PgStorageClass.of(cursor["sc"]),
        tablespace_oid = cursor["ts_oid"]
    )

    /**
     * Test if this is an index.
     * @return _true_ if this is an index.
     */
    fun isIndex() = kind === PgKind.Index

    /**
     * Test if this is a table.
     * @return _true_ if this is a table.
     */
    fun isTable() = kind === PgKind.OrdinaryTable

    /**
     * Test if this is a partition.
     * @return _true_ if this is a partition.
     */
    fun isPartition() = kind === PgKind.PartitionedTable

    /** The parts of the name. */
    private val parts = rel_name.split('$')

    /**
     * Returns the distribution partition of this relation.
     * @return the distribution partition of this relation or -1, when this is not distribution partitioned.
     */
    fun distributionPartition(): Int {
        // The distribution partition is the trailing `$p<NNN>` segment.
        val last = parts.last()
        if (parts.size >= 2 && last.length > 1 && last[0] == 'p') return last.substring(1).toIntOrNull() ?: -1
        return -1
    }

    //         0         1          =  2
    // HEAD: {name}${distribution}
    @JvmField
    val isHeadTable = parts.size == 1
    @JvmField
    val isHeadDistributionPartition = parts.size == 2 && parts[1] != "hst"
    @JvmField
    val isHead = isHeadTable || isHeadDistributionPartition

    //             0    1      2          3          = 4
    // HISTORY: {name}$hst${shifted}${distribution}

    val isHistoryTable: Boolean = parts.size == 2 && parts[1] == "hst"
    val isHistoryPartition: Boolean = parts.size == 3 && parts[1] == "hst"
    val isHistoryDistributionPartition: Boolean = parts.size == 4 && parts[1] == "hst"
    val isHistory = isHistoryTable || isHistoryPartition || isHistoryDistributionPartition

    companion object PgRelationCompanion {
        /**
         * Execute a query in [pg_class](https://www.postgresql.org/docs/current/catalog-pg-class.html) to receive all information rows about the given collection.
         * @param conn the connection to use for the query.
         * @param schemaName the name of the schema to query.
         * @param collectionId the ID of the collection to search for.
         * @return the cursor with the results of the query.
         */
        @JsStatic
        @JvmStatic
        fun select(conn: PgConnection, schemaName: String, collectionId: String): PgCursor {
            val SQL = """
WITH i AS (SELECT oid, nspname FROM pg_namespace WHERE nspname=${quoteLiteral(schemaName)})
SELECT c.oid AS oid,
       c.relname AS relname,
       i.oid AS schema_oid,
       i.nspname AS schema_name,
       c.relkind as kind,
       c.relpersistence sc,
       c.reltablespace as ts_oid
FROM pg_class c, i
WHERE c.relnamespace = i.oid AND (c.relname=${quoteLiteral(collectionId)} OR c.relname LIKE ${quoteLiteral(collectionId, "${PG_S}%")})
ORDER BY relname;"""
            return conn.execute(SQL)
        }
    }
}
