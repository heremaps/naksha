package naksha.psql.executors

import naksha.model.IMetadataArray
import naksha.model.request.WriteList
import naksha.model.request.WriteOp
import naksha.psql.PgCollection
import naksha.psql.PgSession

/**
 *
 */
internal open class PgWriterExecute(
    val session: PgSession,
    val op: WriteOp,
    val map: String,
    val collection: PgCollection,
    val writes: WriteList
) {

    /**
     * Start the writes, may block or fork a new thread to execute them.
     */
    fun start() {

    }

    fun getResults(): IMetadataArray {
        TODO("Implement me!")
    }
}