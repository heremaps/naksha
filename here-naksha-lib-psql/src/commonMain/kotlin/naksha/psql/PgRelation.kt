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
 * @property oid the OID of the relation.
 * @property name the name of the relation.
 * @property schemaOid the OID of the schema in which the relation is located.
 * @property schema_name the name of the schema in which the relation is located.
 * @property kind the kind of relation.
 * @property storageClass the storage class of the relation.
 * @property tablespaceOid the OID of the tablespace in which the relation is stored.
 */
@JsExport
data class PgRelation(
    @JvmField val oid: Int,
    @JvmField val name: String,
    @JvmField val schemaOid: Int,
    @JvmField val schema_name: String,
    @JvmField val kind: PgKind,
    @JvmField val storageClass: PgStorageClass,
    @JvmField val tablespaceOid: Int
) {
    /**
     * Create an information row from the given cursor, that need to be a cursor as returned by [select].
     * @param cursor the cursor as returned by [select].
     */
    @JsName("fromCursor")
    constructor(cursor: PgCursor) : this(
        oid = cursor["oid"],
        name = cursor["relname"],
        schemaOid = cursor["schema_oid"],
        schema_name = cursor["schema_name"],
        kind = PgKind.of(cursor.column("kind") as String),
        storageClass = PgStorageClass.of(cursor["sc"]),
        tablespaceOid = cursor["ts_oid"]
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

    private var _partNumber: Int? = null

    /**
     * Returns the partition number.
     * @return the partition number or -1, when this is no partition.
     */
    fun partitionNumber(): Int {
        var n = _partNumber
        if (n != null) return n
        n = -1
        var i = name.indexOf(PG_PART)
        if (i > 0) {
            i += PG_PART.length
            if (i + 3 <= name.length) try {
                n = name.substring(i, 3).toInt(10)
            } catch (_: Exception) {}
        }
        this._partNumber = n
        // TODO: KotlinCompilerBug - It should know that "n" is never null
        //                           Either name.substring().toInt() returns or not, either way, n is a number!
        return n!!
    }

    private var _year: Int? = null

    /**
     * Returns the year, when this is a year-partition.
     * @return the year or -1, when this is no year-partition.
     */
    fun year(): Int {
        var n = _year
        if (n != null) return n
        n = -1
        var i = name.indexOf(PG_YEAR)
        if (i > 0) {
            i += PG_YEAR.length
            if (i + 4 <= name.length) try {
                n = name.substring(i, 4).toInt(10)
            } catch (_: Exception) {}
        }
        this._year = n
        // TODO: KotlinCompilerBug - It should know that "n" is never null
        //                           Either name.substring().toInt() returns or not, either way, n is a number!
        return n!!
    }

    fun isAnyHeadRelation() = name.indexOf(PG_DEL) < 0 && name.indexOf(PG_HST) < 0 && name.indexOf(PG_META) < 0
    fun isHeadRootRelation() = isAnyHeadRelation() && (isTable() || isPartition()) && name.indexOf(PG_PART) < 0
    fun isTxnYearRelation() = isAnyHeadRelation() && isTable() && name.indexOf(PG_YEAR) > 0

    // ---

    fun isAnyDeleteRelation() = name.indexOf(PG_DEL) > 0
    fun isDeleteRootRelation() = isAnyDeleteRelation() && (isTable() || isPartition()) && name.indexOf(PG_PART) < 0

    // ---

    fun isAnyHistoryRelation() = name.indexOf(PG_HST) > 0
    fun isHistoryRootRelation() = isAnyHistoryRelation() && (isTable() || isPartition())
            && name.indexOf(PG_YEAR) < 0
    fun isHistoryYearRelation() = isAnyHistoryRelation() && (isTable() || isPartition())
            && name.indexOf(PG_YEAR) > 0 && name.indexOf(PG_PART) < 0
    fun isHistoryPartition() = isAnyHistoryRelation() && isTable()
            && name.indexOf(PG_YEAR) > 0 && name.indexOf(PG_PART) > 0

    // ---

    /**
     * Test if this is a HEAD relation (any table, partition or index that belongs to HEAD).
     * @return _true_ if this is a HEAD relation.
     */
    fun isAnyMetaRelation() = name.indexOf(PG_META) > 0

    /**
     * Tests if this the HEAD table, which means, that HEAD is not partitioned.
     * @return _true_ if this is the HEAD table.
     */
    fun isMetaRootRelation() = isAnyMetaRelation() && isTable()

    // ---

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
ORDER BY relname;
"""
            return conn.execute(SQL)
        }
    }
}
