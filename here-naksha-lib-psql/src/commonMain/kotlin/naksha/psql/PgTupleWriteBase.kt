package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.request.WriteOp
import naksha.psql.PgColumn.PgColumnCompanion.allColumns
import kotlin.jvm.JvmField

/**
 * Base class for all operations, so for:
 * - [PgTupleWriterInsert]
 * - [PgTupleWriterUpdate]
 * - [PgTupleWriterUpsert]
 * - [PgTupleWriterDelete]
 * - [PgTupleWriterDelete]
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal open class PgTupleWriteBase protected constructor(
    /**
     * The session to which this writer is bound.
     * @since 3.0
     */
    val session: PgSession,

    /**
     * The collection to operate upon.
     * @since 3.0
     */
    val collection: PgCollection,

    /**
     * The list of writes to perform.
     * @since 3.0
     */
    val writes: List<PgTupleWrite>
) {
    companion object PgTupleWriteBase_C {
        /**
         * The names of all database columns, as comma separated list.
         * @since 3.0
         */
        @JvmField
        val allColumnNames = allColumns.joinToString(",") { it.name }

        /**
         * The placeholders when inserting all columns, like &#36;1, &#36;2, ...
         * @since 3.0
         */
        @JvmField
        val allColumnPlaceholders = allColumns.joinToString(",") { "\$${(it.i + 1)}" }

        /**
         * The array type names for the values of all columns, for example `int8[]` for a column being [Int64].
         * @since 3.0
         */
        @JvmField
        val allColumnTypeNames = Array(allColumns.size) { allColumns[it].type.text+"[]" }
    }

    /**
     * The transaction to operate upon.
     * @since 3.0
     */
    val tx = session.useTx()

    /**
     * @see [PgColumn.txn_next]
     * @since 3.0
     */
    val txn_next = arrayOfNulls<Int64>(writes.size)

    /**
     * @see [PgColumn.updated_at]
     * @since 3.0
     */
    val updated_at = arrayOfNulls<Int64>(writes.size)

    /**
     * @see [PgColumn.created_at]
     * @since 3.0
     */
    val created_at = arrayOfNulls<Int64>(writes.size)

    /**
     * @see [PgColumn.author_ts]
     * @since 3.0
     */
    val author_ts = arrayOfNulls<Int64>(writes.size)

    /**
     * @see [PgColumn.cv0]
     * @since 3.0
     */
    val cv0 = arrayOfNulls<Double>(writes.size)

    /**
     * @see [PgColumn.cv1]
     * @since 3.0
     */
    val cv1 = arrayOfNulls<Double>(writes.size)

    /**
     * @see [PgColumn.cv2]
     * @since 3.0
     */
    val cv2 = arrayOfNulls<Double>(writes.size)

    /**
     * @see [PgColumn.cv3]
     * @since 3.0
     */
    val cv3 = arrayOfNulls<Double>(writes.size)

    /**
     * @see [PgColumn.hash]
     * @since 3.0
     */
    val hash = arrayOfNulls<Int>(writes.size)

    /**
     * @see [PgColumn.here_tile]
     * @since 3.0
     */
    val here_tile = arrayOfNulls<Int>(writes.size)

    /**
     * @see [PgColumn.flags]
     * @since 3.0
     */
    val flags = arrayOfNulls<Int>(writes.size)

    /**
     * @see [PgColumn.cc]
     * @since 3.0
     */
    val cc = arrayOfNulls<Int>(writes.size)

    /**
     * @see [PgColumn.tn]
     * @since 3.0
     */
    val tn = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.prev_tn]
     * @since 3.0
     */
    val prev_tn = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.base_tn]
     * @since 3.0
     */
    val base_tn = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.id]
     * @since 3.0
     */
    val id = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.app_id]
     * @since 3.0
     */
    val app_id = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.author]
     * @since 3.0
     */
    val author = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.origin]
     * @since 3.0
     */
    val origin = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.target]
     * @since 3.0
     */
    val target = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.ft]
     * @since 3.0
     */
    val ft = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.cs0]
     * @since 3.0
     */
    val cs0 = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.cs1]
     * @since 3.0
     */
    val cs1 = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.cs2]
     * @since 3.0
     */
    val cs2 = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.cs3]
     * @since 3.0
     */
    val cs3 = arrayOfNulls<String>(writes.size)

    /**
     * @see [PgColumn.tags]
     * @since 3.0
     */
    val tags = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.ref_point]
     * @since 3.0
     */
    val ref_point = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.geo]
     * @since 3.0
     */
    val geo = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.feature]
     * @since 3.0
     */
    val feature = arrayOfNulls<ByteArray>(writes.size)

    /**
     * @see [PgColumn.attachment]
     * @since 3.0
     */
    val attachment = arrayOfNulls<ByteArray>(writes.size)

    /**
     * The version in which to modify the feature, only used for atomic operations.
     * @since 3.0
     */
    val version = arrayOfNulls<Int64>(writes.size)

    /**
     * Set values at the given index.
     * @param i the index to set.
     * @param write the write operation from which to generate the columns.
     */
    protected fun set(i: Int, write: PgTupleWrite) {
        val tuple = write.tuple
        val meta = tuple?.meta
        txn_next[i] = null
        if (meta != null) {
            updated_at[i] = meta.updatedAt
            created_at[i] = meta.createdAt
            author_ts[i] = meta.authorTs
            cv0[i] = meta.cv0
            cv1[i] = meta.cv1
            cv2[i] = meta.cv2
            cv3[i] = meta.cv3
            hash[i] = meta.hash
            here_tile[i] = meta.hereTile
            flags[i] = meta.flags
            cc[i] = meta.changeCount
            tn[i] = meta.tupleNumber.toByteArray(TupleNumberVariant.B160)
            prev_tn[i] = meta.prevTupleNumber?.toByteArray(TupleNumberVariant.B96)
            base_tn[i] = meta.baseTupleNumber?.toByteArray(TupleNumberVariant.B96)
            id[i] = meta.id
            app_id[i] = meta.appId
            author[i] = meta.author
            origin[i] = meta.origin
            target[i] = meta.target
            ft[i] = meta.ft
            cs0[i] = meta.cs0
            cs1[i] = meta.cs1
            cs2[i] = meta.cs2
            cs3[i] = meta.cs3
            tags[i] = tuple.tags
            ref_point[i] = tuple.referencePoint
            feature[i] = tuple.feature
            geo[i] = tuple.geo
            attachment[i] = tuple.attachment
        } else {
            check(write.op == WriteOp.DELETE || write.op == WriteOp.PURGE)
            id[i] = write.original.id
            app_id[i] = session.options.appId
            author[i] = session.options.author
        }
        version[i] = write.version?.txn
    }

    init {
        for (i in writes.indices) set(i, writes[i])
    }
}
