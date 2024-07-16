@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package naksha.psql

import kotlin.js.JsExport

/**
 * Information about a single database table.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgTableInfo(
    val collectionId: String,
    val schema: String,
    val schemaOid: Int,
    val table: String,
    val tableOid: Int,
    val kind: PgKind
) {
    private var quotedSchema: String? = null

    /**
     * The schema identifier, optionally quoted in double quotes.
     */
    val schemaNameQuoted: String
        get() {
            var s = quotedSchema
            if (s == null) {
                s = PgUtil.quoteIdent(schema)
                quotedSchema = s
            }
            return s
        }

    private var quotedTable: String? = null

    /**
     * The table identifier, optionally quoted in double quotes.
     */
    val tableNameQuoted: String
        get() {
            var s = quotedTable
            if (s == null) {
                s = PgUtil.quoteIdent(table)
                quotedTable = s
            }
            return s
        }

    /**
     * Tests if this is any HEAD table.
     * @return _true_ if this is any HEAD table.
     */
    fun isHead(): Boolean = isHeadRoot() || isHeadPartition()

    /**
     * Tests if this is the root HEAD table.
     * @return _true_ if this is the root HEAD table.
     */
    fun isHeadRoot(): Boolean = table.indexOf('$') < 0

    /**
     * Tests if this is a partition of the HEAD table.
     * @return _true_ if this is a partition of the HEAD table.
     */
    fun isHeadPartition(): Boolean = table.indexOf("\$p") > 0

    /**
     * Tests if this is any DELETED table.
     * @return _true_ if this is any DELETED table.
     */
    fun isDeleted(): Boolean = isDeletedRoot() || isDeletedPartition()

    /**
     * Tests if this is the root DELETED table.
     * @return _true_ if this is the root DELETED table.
     */
    fun isDeletedRoot(): Boolean = table.endsWith("\$del")

    /**
     * Tests if this is a partition of the DELETED table.
     * @return _true_ if this is a partition of the DELETED table.
     */
    fun isDeletedPartition(): Boolean = table.indexOf("\$del_p") > 0

    /**
     * Tests if this is the META table.
     * @return _true_ if this is the META table.
     */
    fun isMeta(): Boolean = table.endsWith("\$meta")

    /**
     * Tests if this is any HISTORY table.
     * @return _true_ if this is any HISTORY table.
     */
    fun isHistory(): Boolean = table.indexOf("\$hst") > 0

    /**
     * Tests if this is the root HISTORY table.
     * @return _true_ if this is the root HISTORY table.
     */
    fun isHistoryRoot(): Boolean = table.endsWith("\$hst")

    /**
     * Tests if this is a monthly partition of HISTORY.
     * @return _true_ if this is a monthly partition of HISTORY.
     */
    fun isHistoryMonthly(): Boolean {
        val hstStart = table.indexOf("\$hst_")
        if (hstStart < 0) return false
        // should be: tableName$hst_yyyy_mm
        return table.indexOf("\$_p", hstStart) < 0
    }

    /**
     * Tests if this is a sub-partition of a monthly HISTORY partition.
     * @return _true_ if this is a sub-partition of a monthly HISTORY partition.
     */
    fun isHistoryPartition(): Boolean {
        val hstStart = table.indexOf("\$hst_")
        if (hstStart < 0) return false
        // should be: tableName$hst_yyyy_mm_p000
        return table.lastIndexOf("\$_p") > hstStart
    }

    /**
     * An indicator if this is an internal Naksha collection. Very special rules apply to these tables.
     */
    val internal: Boolean
        get() = collectionId.startsWith("naksha~")

    private var indices: List<PgIndex>? = null

    /**
     * Returns the indices of this table.
     * @param conn the connection that can be used to query the database.
     * @param noCache if _true_, then the cache is bypassed and the data is always queried live from the database.
     * @return the indices, only from cache if [noCache] is _false_.
     */
    fun getIndices(conn: PgConnection, noCache: Boolean = false): List<PgIndex> {
        if (!noCache && indices != null) indices
        TODO("Implement me!")
    }
}