@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaException
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.MemberType.MemberType_C.BYTE_ARRAY
import naksha.model.objects.MemberType.MemberType_C.INT64
import naksha.model.objects.MemberType.MemberType_C.STRING
import naksha.model.objects.MemberType.MemberType_C.TUPLE_NUMBER
import naksha.model.objects.StandardMembers.StandardMembers_C.Feature
import naksha.model.objects.StandardMembers.StandardMembers_C.GlobalBookFeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.Tn
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A column descriptor for database columns.
 *
 * The [extra] is used to add constraints, and the storage-method:
 * - [PLAIN] prevents either compression or out-of-line storage.
 * - [EXTENDED] allows both compression and out-of-line storage.
 * - [EXTERNAL] allows out-of-line storage but not compression.
 * - [MAIN] allows compression but not out-of-line storage. Actually, out-of-line storage will still be performed for such columns, but only as a last resort when there is no other way to make the row small enough to fit on a page.
 *
 * The TOAST code will compress and/or move field values out-of-line until the row value is shorter than TOAST_TUPLE_TARGET bytes.
 *
 * See [storage-toast.html](https://www.postgresql.org/docs/current/storage-toast.html).
 */
@JsExport
data class PgColumn(
    /**
     * The index of the column in the table.
     * @since 3.0
     */
    @JvmField
    val index: Int,

    /**
     * The name of the column.
     * @since 3.0
     */
    @JvmField
    val name: String,

    /**
     * The type of the column provided as [MemberType].
     * @since 3.0
     */
    @JvmField
    val memberType: MemberType,

    /**
     * Optional extras for the definition, for example "NOT NULL".
     * @since 3.0
     */
    @JvmField
    val extra: String? = null,

    /**
     * The type of the column.
     * @since 3.0
     */
    @JvmField
    val pgType: PgType = PgType.ofMemberType(memberType),

    /**
     * The SQL quoted identifier, the same result (just slower) can be archived using `PgUtil.quoteIdent(col.name)`.
     * @return the SQL quoted identifier (with optional double quotes).
     * @see PgUtil.quoteIdent
     */
    @JvmField
    val ident: String = PgUtil.quoteIdent(name),

    /**
     * The SQL code to added into a `CREATE TABLE` statements.
     */
    @JvmField
    val sql: String = "$ident $pgType" + if (extra != null) " $extra" else ""
) {
    /**
     * Create a new column with the same states, just a different index.
     * @param index the new index.
     * @param source the column to copy with all other attributes.
     * @since 3.0
     */
    @JsName("reindex")
    constructor(index: Int, source: PgColumn) : this(index, source.name, source.memberType, source.extra, source.pgType, source.ident)

    companion object PgColumn_C {
        /**
         * Prevents either compression or out-of-line storage. This is the only possible strategy for columns of non-TOAST-able data types.
         * @since 3.0
         */
        const val PLAIN = "PLAIN"

        /**
         * Allows both compression and out-of-line storage. This is the default for most TOAST-able data types. Compression will be attempted first, then out-of-line storage if the row is still too big.
         * @since 3.0
         */
        const val EXTENDED = "EXTENDED"

        /**
         * Allows out-of-line storage but not compression. Use of EXTERNAL will make substring operations on wide text and bytea columns faster (at the penalty of increased storage space) because these operations are optimized to fetch only the required parts of the out-of-line value when it is not compressed.
         * @since 3.0
         */
        const val EXTERNAL = "EXTERNAL"

        /**
         * Allows compression but not out-of-line storage. (Actually, out-of-line storage will still be performed for such columns, but only as a last resort when there is no other way to make the row small enough to fit on a page.)
         * @since 3.0
         */
        const val MAIN = "MAIN"

        /**
         * The feature-number.
         *
         * Together with [VERSION], forms the primary identification of a tuple within a collection. The lower 16 bits of this value are used as the partition key for distribution partitioning (see [naksha.model.Naksha.featureNumber]).
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val FN = PgColumn(0, "_fn", INT64, "STORAGE $PLAIN NOT NULL")

        /**
         * The version (with action in the lower 2 bits) of this tuple.
         *
         * Together with [FN], forms the primary identification of a tuple within a collection. See [naksha.model.Version] for the layout.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val VERSION = PgColumn(1, "_version", INT64, "STORAGE $PLAIN NOT NULL")

        /**
         * The next-version (with action in the lower 2 bits) of this tuple, only available in the history.
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val NEXT_VERSION = PgColumn(2, NextVersion.name, INT64, "STORAGE $PLAIN NOT NULL")
    }

    /** Returns the [ident] of the column, so the quoted [name]. */
    override fun toString(): String = ident
}