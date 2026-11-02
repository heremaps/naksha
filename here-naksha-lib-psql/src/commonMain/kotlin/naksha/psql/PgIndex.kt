package naksha.psql

import naksha.base.illegalArg
import naksha.base.Id
import naksha.base.IdTextType
import naksha.base.illegalId
import naksha.model.objects.Index
import naksha.model.objects.MemberType
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
     * The columns to index in the order in which to index.
     * @since 3.0
     */
    @JvmField
    var on: Array<Pair<PgColumn, PgOrder>>,

    /**
     * The columns to include into the index to improve queries.
     * @since 3.0
     */
    @JvmField
    var includes: Array<PgColumn> = emptyArray(),

    /**
     * Whether the index is partial, i.e. only covers rows where the leading [on] column is not null
     * (emits a `WHERE <leading column> IS NOT NULL` predicate).
     * @since 3.0
     */
    @JvmField
    var conditional: Boolean = false
) {
    /**
     * The logical index identifier, used to derive implementation-specific physical aliases.
     * @since 3.0
     */
    val id: Id = Id(name).also {
        if (!IdTextType.UNSAFE.accepts(it.textType)) throw illegalId("Invalid custom index name")
    }

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
            MemberType.TAG_MAP -> Pair("gin", "${column.ident} jsonb_ops")
            MemberType.TAG_LIST -> Pair("gin", "${column.ident} array_ops")
            // tuple-number and boolean so far
            else -> throw illegalArg("The member type ${column.memberType} of column '$column' of index '$indexName' is not a valid index target")
        }
    }

    /**
     * Rejects index definitions unsupported by the available method/operator-class mapping before DDL. Requested includes are never silently removed. The leading key selects the method; secondary keys must be primitive, and the string pattern opclass is supported only by btree. GIN does not support INCLUDE.
     * @since 3.0
     */
    internal fun validate() {
        if (on.isEmpty()) throw illegalArg("Index without target columns: $name")
        val (primaryIndex, _) = indexAndOpsOf(on.first().first, name)
        for (i in on.indices) {
            val column = on[i].first
            val (method, _) = indexAndOpsOf(column, name)
            if (i > 0 && method != "btree") {
                throw illegalArg("Index '$name' using $primaryIndex cannot use '${column.name}' as secondary key; only primitives are supported")
            }
            if (primaryIndex != "btree" && column.memberType == MemberType.STRING) {
                throw illegalArg("Index '$name' using $primaryIndex cannot use string key '${column.name}' with text_pattern_ops")
            }
        }
        if (primaryIndex == "gin" && includes.isNotEmpty()) {
            throw illegalArg("Index '$name' using gin cannot have INCLUDE columns")
        }
        for (column in includes) {
            if (indexAndOpsOf(column, name).first != "btree") {
                throw illegalArg("Index '$name' cannot include '${column.name}'; only primitive payload columns are supported")
            }
        }
    }

    /**
     * Validated method, keys, payload and predicate for a root index. Storage parameters are not inferred from relation names.
     * @since 3.0
     */
    internal fun definition(): String {
        validate()
        val (primaryIndex, _) = indexAndOpsOf(on.first().first, name)
        val elements = ArrayList<String>(on.size)
        for (i in 0 ..< on.size) {
            val (_, element) = indexAndOpsOf(on[i].first, name)
            elements.add(if (primaryIndex == "btree") "$element ${on[i].second}" else element)
        }
        val includeClause = if (includes.isEmpty()) "" else " INCLUDE (${includes.joinToString(", ") { column -> 
            column.ident
        }})"
        val whereClause = if (conditional) " WHERE ${on.first().first.ident} IS NOT NULL" else ""
        return "USING $primaryIndex (${elements.joinToString(", ")})$includeClause$whereClause"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PgIndex
        if (name != other.name) return false
        if (!on.contentEquals(other.on)) return false
        if (!includes.contentEquals(other.includes)) return false
        if (conditional != other.conditional) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + on.contentHashCode()
        result = 31 * result + includes.contentHashCode()
        result = 31 * result + conditional.hashCode()
        return result
    }

}
