package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.objects.NakshaTx

/**
 * Base class for all operations, so for:
 * - [PgTupleWriteInsert]
 * - [PgTupleWriterUpdate]
 * - [PgTupleWriterUpsert]
 * - [PgTupleWriteDelete]
 * - [PgTupleWriteDelete]
 * @since 3.0
 * @see [PgTupleWriter]
 */
internal abstract class PgTupleWriteBase protected constructor(
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
    val storageNumber: Int64
        get() = collection.storage.number

    val mapNumber: Int
        get() = collection.map.number

    val collectionNumber: Int
        get() = collection.number

    /**
     * The transaction to operate upon.
     * @since 3.0
     */
    val tx = session.useTx()

    /**
     * The Naksha transaction.
     * @since 3.0
     */
    val transaction: NakshaTx
        get() = tx.transaction

    /**
     * The rows to write.
     * @since 3.0
     */
    val rows = PgColumnRows()
        .withStorageNumber(storageNumber)
        .withMapNumber(mapNumber)
        .withCollectionNumber(collectionNumber)
        .withMinSize(writes.size)

    fun execute(conn: PgConnection) {
        collection.map.setSearchPath(conn)
        doExecute(conn)
    }

    protected abstract fun doExecute(conn: PgConnection)
}
