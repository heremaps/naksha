package naksha.psql

import naksha.base.Int64
import naksha.model.*

/**
 * Base class for all operations, so for:
 * - [PgTupleWriterInsert]
 * - [PgTupleWriterUpdate]
 * - [PgTupleWriterUpsert]
 * - [PgTupleWriterDelete]
 * - [PgTupleWriterPurge]
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
     * Set values at the given index.
     * @param i the index to set.
     * @param write the write operation from which to generate the columns.
     */
    protected fun set(i: Int, write: PgTupleWrite) {
        val tuple = write.tuple
        val meta = tuple.meta
        txn_next[i] = meta.txnNext
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
    }

    init {
        for (i in writes.indices) set(i, writes[i])
    }

    /**
     * Create a single array that contains all values stored in the [Tuple] that should be modified.
     * @since 3.0
     */
    fun toDataArray() : Array<Any?> = arrayOf(
        txn_next, updated_at, created_at, author_ts,
        cv0, cv1, cv2, cv3,
        hash, here_tile, flags, cc,
        tn, prev_tn, base_tn,
        id, app_id, author, origin, target, ft,
        cs0, cs1, cs2, cs3,
        tags, ref_point, geo, feature, attachment
    )
}
