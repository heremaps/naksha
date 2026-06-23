package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.INTERNAL_ERROR
import naksha.model.NakshaException
import naksha.model.objects.Index
import naksha.model.objects.IndexType
import naksha.model.objects.IndexType.IndexType_C.BTREE
import naksha.model.objects.IndexType.IndexType_C.SPATIAL
import naksha.model.objects.IndexType.IndexType_C.TAG_MAP
import naksha.model.objects.IndexType.IndexType_C.TAG_LIST
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
     * The index type.
     * @since 3.0
     */
    @JvmField
    var type: IndexType,

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

    internal fun create(conn: PgConnection, tableName: String) {
        val includeClause = if (includes.isEmpty()) "" else " INCLUDE (${includes.joinToString(", ")})"
        val using = when (type) {
            BTREE -> "btree"
            SPATIAL -> "gist"
            TAG_MAP, TAG_LIST -> "gin"
            else -> throw NakshaException(INTERNAL_ERROR, "Invalid index type for index $name on table $tableName")
        }
        val indexName = quoteIdent(tableName, "\$i_", tableName)
        val fillFactor = if (PgTable.isAnyHead(tableName)) "(fillfactor=50)" else "(fillfactor=100)"
        val sql = """CREATE INDEX IF NOT EXISTS $indexName 
ON ${quoteIdent(tableName)}
USING $using$includeClause
WITH $fillFactor"""
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
        if (type != other.type) return false
        if (!on.contentEquals(other.on)) return false
        if (!includes.contentEquals(other.on)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + on.contentHashCode()
        result = 31 * result + includes.contentHashCode()
        return result
    }

}
