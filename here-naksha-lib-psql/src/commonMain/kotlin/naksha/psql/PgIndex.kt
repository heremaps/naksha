package naksha.psql

import naksha.base.illegalArg
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
        // Returns (index-method, index-element) for a column. The element includes the column reference
        // plus any opclass / functional wrapper the method needs (e.g. a spatial column becomes
        // `naksha_2d(<col>)`), so the caller can just join the elements.
        private fun indexAndOpsOf(column: PgColumn, indexName: String): Pair<String, String> = when (column.memberType) {
            MemberType.INT8,
            MemberType.INT16,
            MemberType.INT32,
            MemberType.INT64,
            MemberType.FLOAT32,
            MemberType.FLOAT64,
            MemberType.BYTE_ARRAY -> Pair("btree", column.ident)
            MemberType.STRING -> Pair("btree", "${column.ident} COLLATE \"C\" text_pattern_ops")
            // A two-dimensional gist index over the TWKB geometry, via the naksha_2d() helper.
            MemberType.SPATIAL -> Pair("gist", "naksha_2d(${column.ident})")
            MemberType.TAG_MAP,
            MemberType.TAG_MAP_FROM_ARRAY -> Pair("gin", "${column.ident} jsonb_ops")
            MemberType.TAG_LIST -> Pair("gin", "${column.ident} array_ops")
            else -> throw illegalArg("The member type ${column.memberType} of column '$column' of index '$indexName' is not a valid index target")
        }
    }

    internal fun create(conn: PgConnection, table: PgTable) {
        if (on.isEmpty()) throw illegalArg("Index without target columns: $name")
        val (primaryIndex, _) = indexAndOpsOf(on.first(), name)
        val elements = ArrayList<String>(on.size)
        for (i in 0 ..< on.size) {
            val (method, element) = indexAndOpsOf(on[i], name)
            // The first column selects the index method (btree/gin/gist); any further columns must be
            // plain btree-type columns (valid as extra btree columns, or via btree_gist inside a gist).
            if (i > 0 && method != "btree") {
                throw illegalArg("The member #$i (${on[i]}) can not be used as secondary index element, only primitives are allowed")
            }
            elements.add(element)
        }
        val includeClause = if (includes.isEmpty()) "" else " INCLUDE (${includes.joinToString(", ") { column -> 
            val (index, _) = indexAndOpsOf(column, "include")
            if (index != "btree") throw illegalArg("The include of column $column is not possible, because it is no primitive")
            column.ident
        }})"
        val indexIdent = quoteIdent(table.name, "\$ci_", name)
        val withClause = if (primaryIndex == "gin") "" else " WITH (fillfactor=${if (PgTable.isAnyHead(table.name)) 50 else 100})"
        val sql = """CREATE INDEX IF NOT EXISTS $indexIdent
ON ${table.quotedName}
USING $primaryIndex (${elements.joinToString(", ")})$includeClause$withClause"""
        conn.execute(sql).close()
    }

    internal fun drop(conn: PgConnection, tableName: String) {
        val indexName = quoteIdent(tableName, "\$ci_", name)
        conn.execute("DROP INDEX IF EXISTS $indexName CASCADE").close()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgIndex
        if (name != other.name) return false
        if (!on.contentEquals(other.on)) return false
        if (!includes.contentEquals(other.includes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + on.contentHashCode()
        result = 31 * result + includes.contentHashCode()
        return result
    }

}
