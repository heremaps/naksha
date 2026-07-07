package naksha.psql

import naksha.model.illegalArg
import naksha.model.objects.Index
import naksha.model.objects.MemberType
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * The special PostgreSQL internal class that represents an [indices][Index], generated from [custom indices][Index].
 *
 * The mandatory indices are intrinsic and not exposed as `PgIndex`. Therefore, indices are only those indices that users needs, not what is necessary.
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate")
@JsExport
data class PgIndex(
    /**
     * The collection unique name of the index.
     * @since 3.0
     */
    @JvmField
    val name: String,

    /**
     * The columns to index.
     * @since 3.0
     */
    @JvmField
    var on: Array<PgColumn>,

    /**
     * The columns to include into the index to improve queries.
     * @since 3.0
     */
    @JvmField
    var includes: Array<PgColumn> = emptyArray()
) {

    companion object PgIndex_C {
        private fun indexAndOpsOf(column: PgColumn, indexName: String): Pair<String, String> = when (column.memberType) {
            MemberType.INT8,
            MemberType.INT16,
            MemberType.INT32,
            MemberType.INT64,
            MemberType.FLOAT32,
            MemberType.FLOAT64,
            MemberType.BYTE_ARRAY -> Pair("btree", "")
            MemberType.STRING -> Pair("btree", " COLLATE \"C\" text_pattern_ops")
            MemberType.SPATIAL -> Pair("gist_btree", " gist_geometry_ops_2d")
            MemberType.TAG_MAP -> Pair("gin", " jsonb_ops")
            MemberType.TAG_MAP_FROM_ARRAY,
            MemberType.TAG_LIST -> Pair("gin", " array_ops")
            else -> throw illegalArg("The member type ${column.memberType} of column '$column' of index '$indexName' is not a valid index target")
        }
    }

    internal fun create(conn: PgConnection, table: PgTable) {
        if (on.isEmpty()) throw illegalArg("Index without target columns: $name")
        val primaryColumn = on.first()
        val (primaryIndex, _) = indexAndOpsOf(primaryColumn, name)
        val secondaries = mutableListOf<Pair<String,String>>()
        for (i in 0 ..< on.size) {
            val pgColumn = on[i]
            val secondary = indexAndOpsOf(pgColumn, name)
            if (secondary.first != "btree" || !primaryIndex.contains(secondary.first)) {
                throw illegalArg("The member #$i ($pgColumn) can not be used as secondary index element, only primitives are allows")
            }
            secondaries.add(secondary)
        }
        val includeClause = if (includes.isEmpty()) "" else " INCLUDE (${includes.joinToString(", ") { column -> 
            val (index, _) = indexAndOpsOf(column, "include")
            if (index != "btree") throw illegalArg("The include of column $column is not possible, because it is no primitive")
            column.ident
        }})"
        val indexIdent = quoteIdent(table.name, "\$i_", table.name)
        val fillFactor = if (PgTable.isAnyHead(table.name)) "(fillfactor=50)" else "(fillfactor=100)"
        val sql = """CREATE INDEX IF NOT EXISTS $indexIdent 
ON ${table.quotedName}
USING $primaryIndex (${on.zip(secondaries).joinToString(", ") { (col, sec) -> "${col.ident}${sec.second}" }})$includeClause
WITH $fillFactor""" //                        like "gin jsonb_ops"
        conn.execute(sql).close()
    }

    internal fun drop(conn: PgConnection, tableName: String) {
        val indexName = quoteIdent(tableName, "\$i_", tableName)
        conn.execute("DROP INDEX IF EXISTS $indexName CASCADE").close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgIndex
        if (name != other.name) return false
        if (!on.contentEquals(other.on)) return false
        if (!includes.contentEquals(other.on)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + on.contentHashCode()
        result = 31 * result + includes.contentHashCode()
        return result
    }

}
