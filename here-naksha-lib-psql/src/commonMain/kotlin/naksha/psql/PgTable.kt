package naksha.psql

import kotlin.js.JsExport

/**
 * Information about a single database table.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
data class PgTable(
    val collectionId: String,
    val schemaName: String,
    val schemaOid: Int,
    val tableName: String,
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
                s = PgUtil.quoteIdent(schemaName)
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
                s = PgUtil.quoteIdent(tableName)
                quotedTable = s
            }
            return s
        }

    fun isHead(): Boolean = tableName.indexOf('$') < 0
    fun isHeadPartition(): Boolean = tableName.indexOf("\$p") >= 0
    fun isMeta(): Boolean = tableName.indexOf("\$meta") >= 0
    fun isHistory(): Boolean = tableName.indexOf("\$hst") >= 0
    fun isHistoryMonthly(): Boolean {
        val hstStart = tableName.indexOf("\$hst_")
        if (hstStart < 0) return false
        // should be: tableName$hst_yyyy_mm
        return tableName.indexOf("\$_p", hstStart) < 0
    }

    fun isHistoryPartition(): Boolean {
        val hstStart = tableName.indexOf("\$hst_")
        if (hstStart < 0) return false
        // should be: tableName$hst_yyyy_mm_p000
        return tableName.lastIndexOf("\$_p") > hstStart
    }

    val internal: Boolean
        get() = collectionId.startsWith("naksha~")

    private var indices: Array<PgIndex>? = null
    fun getIndices(conn: PgConnection, noCache: Boolean = false): Array<PgIndex> {
        TODO("Implement me!")
    }
}