package naksha.psql

import naksha.model.*

/**
 * A transaction helper, internally used by the Naksha session to manage transactions.
 */
internal class NakshaTx(
    val collection: NakshaCollectionCache,
    val txn: Txn,
    var uid: Int = 0,
    var rowsInserted: Int = 0,
    var rowsUpdated: Int = 0,
    var rowsDeleted: Int = 0
) {
    /**
     * Apply changes to [Row.meta] of the given [NEW] row can be inserted into **HEAD**, and reflects an inserted row. Optionally adjust
     * encoding of binary data, when necessary for the collection/storage.
     *
     * If the given [NEW] row does have a [guid][Row.guid], then it possibly has forked the feature off, from an existing row in another
     * collection or storage, or the `id` of the feature changes. This happens for example when a topology is split, then all copies do
     * have the `guid` of the topology that was split, and in this case, [Metadata.origin] must be set to the provided `guid`.
     * @param OLD the existing row, as read from the database; is expected to be _null_.
     * @param NEW the new row to write, as provided by the client.
     * @return the row that can be persisted into the HEAD collection.
     */
    fun insertRow(OLD: Row?, NEW: Row): Row {
        TODO("Implement me!")
        // -> write into HEAD
    }

    /**
     * Apply changes to [Row.meta], so that given [NEW] row can be inserted into **HEAD**, and reflects an updated row. Optionally adjust
     * encoding of binary data, when necessary for the collection/storage.
     *
     * @param OLD the existing row, as read from the database.
     * @param NEW the new row to write, as provided by the client.
     * @return the row that can be persisted into the HEAD collection.
     */
    fun updateRow(OLD: Row, NEW: Row): Row {
        // -> head row
        // -> history row
        TODO("Implement me!")
    }

    /**
     * Apply changes to [Row.meta], so that given [OLD] row can be removed from **HEAD** and inserted into **DELETE**. Optionally adjust
     * encoding of binary data, when necessary for the collection/storage.
     *
     * @param OLD the existing row, as read from the database.
     * @param NEW the new row to write, as provided by the client.
     * @return the row that can be persisted into the HEAD collection.
     */
    fun deleteRow(OLD: Row): Row {
        // -> head row (null)
        // -> history row (the updated state, just move)
        // -> history row (the deleted state, tombstone)
        // -> delete row
        TODO("Implement me!")
    }

    /**
     * Modify the given row, so that it can be copied into **HISTORY**. This operation is applied
     * @param row the **HEAD** state read from database.
     * @return a slightly modified version that can be written into **HISTORY**.
     */
    fun toHistory(row: Row): Row {
        val meta = row.meta
        require(meta != null) { "The row must be completely read from HEAD, to move it into history" }
        return row.copy(meta = meta.copy(txnNext = txn.value))
    }

    /**
     * Modify the given row, read from **HEAD** table, so that it can be copied into **DELETE**.
     * @param row the **HEAD** state read from database.
     * @return a slightly modified version that can be written into **DELETE**.
     */
    fun toDelete(row: Row): Row {
        val meta = row.meta
        require(meta != null) { "The row must be completely read from HEAD, to move it into delete table" }
        require(meta.flags.action() == Action.DELETED) { "Only rows being in DELETED state can be moved to delete table" }
        return row.copy(meta = meta.copy(txnNext = meta.txn))
    }
}