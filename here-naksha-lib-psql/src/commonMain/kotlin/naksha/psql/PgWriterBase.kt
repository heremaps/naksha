package naksha.psql

import naksha.base.Int64
import naksha.model.objects.NakshaTx

/**
 * Base class for all operations, so for:
 * - [PgWriterInsert]
 * - [PgWriterUpsert]
 * - [PgWriterUpdate]
 * - [PgWriterDelete]
 * @since 3.0
 * @see [PgWriter]
 */
internal abstract class PgWriterBase protected constructor(
    /**
     * The [writer][PgWriter] to which this write is bound.
     * @since 3.0
     */
    val writer: PgWriter,

    /**
     * The collection to operate upon.
     * @since 3.0
     */
    val collection: PgCollection,

    /**
     * The list of writes to perform.
     * @since 3.0
     */
    val writes: List<PgWrite>
) {
    val session: PgSession
        get() = writer.session

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
    val inRows = PgColumnRows()
        .withStorageNumber(storageNumber)
        .withMapNumber(mapNumber)
        .withCollectionNumber(collectionNumber)
        .withMinSize(writes.size)

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     */
    fun execute(conn: PgConnection) {
        collection.map.setSearchPath(conn)
        return doExecute(conn)
    }

    /**
     * Execute the operation.
     * @param conn the connection to be used.
     */
    protected abstract fun doExecute(conn: PgConnection)
}
