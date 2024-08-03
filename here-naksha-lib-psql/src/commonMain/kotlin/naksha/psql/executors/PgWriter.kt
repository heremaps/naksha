package naksha.psql.executors

import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.NakshaError.NakshaErrorCompanion.COLLECTION_NOT_FOUND
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.MAP_NOT_FOUND
import naksha.model.request.*
import naksha.psql.PgCollection
import naksha.psql.PgConnection
import naksha.psql.PgSession
import kotlin.jvm.JvmField

// TODO: We need to fix NakshaBulkLoaderPlan to make this faster again !
//       Maybe it can just extend the simple writer, and add bulk loading capabilities?
//       It could reuse some of the code, especially about result-set building, because
//       it would not have to return the written data, actually, it can be highly optimised
//       to only do writing as fast as possible, because we only need the row-ids.
//       It should store what it has into the NakshaCache, but when its not available,
//       forget about it, the result-set will load it on demand (separation of concerns)
//       The point is: When the client does not need the results, best performance is available!

/**
 * A simple basic implementation of a writer.
 *
 * It does ignore optional settings within the [WriteRequest], it actually can be used for any simple list of write instructions, especially as well internally. Its performance is sub-optional, but fine for small amount of writes. It acts as a reference implementation that can be extended.
 */
open class PgWriter(
    /**
     * The session to which this writer is linked.
     */
    session: PgSession,

    /**
     * The write instructions to [execute].
     */
    @JvmField val writes: WriteList,

    /**
     * The connection to use, if not given, acquire the current session connection.
     */
    @JvmField val conn: PgConnection = session.usePgConnection()
) : RowUpdater(session) {

    //private val collectionWrites: WriteList

    /**
     * The write operations ordered by map, collection, partition-number.
     */
    private val orderedWrites: WriteList

    init {
        orderedWrites = writes.copy(false)
        @Suppress("LeakingThis")
        orderedWrites.sortedWith(this::compareEm)
    }

    // Sort write operations by map, collection, partition-number
    private fun compareEm(a: Write?, b: Write?): Int {
        if (a === b) return 0
        if (b == null) return -1
        if (a == null) return 1
        return if (a.mapId == b.mapId) {
            if (a.collectionId == b.collectionId) {
                val a_part = partitionNumber(a.featureId())
                val b_part = partitionNumber(b.featureId())
                if (a_part == b_part) 0 else if (a_part < b_part) -1 else 1
            } else if (a.collectionId < b.collectionId) -1 else 1
        } else if (a.mapId < b.mapId) -1 else 1
    }

    fun execute(): Response {

        // TODO: Fetch all existing states, do we need?

        var map: String? = null
        var i = 0
        while (i < writes.size) {
            val write = writes[i]
            if (write == null) {
                i++
                continue
            }
            if (map == null) {
                map = write.mapId
            } else if (map != write.mapId) {
                throw NakshaException(ILLEGAL_ARGUMENT, "Writing into different maps not supported, found $map and ${write.mapId}")
            }

            // TODO: If the XYZ namespace of the feature stores a different id in UUID
            //       or a different collection, then we need to add the "Origin" tag!

            when (write.op) {
                WriteOp.CREATE -> results += insert(write)
                WriteOp.UPDATE -> results += update(write)
                WriteOp.UPSERT -> results += upsert(write)
                WriteOp.DELETE -> results += delete(write)
                WriteOp.PURGE -> results += purge(write)
                else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown write instruction #$i: ${write.op}")
            }
            i++
        }
        TODO("Finish me")
    }

    private lateinit var version: Version
    private val results = mutableListOf<Row>()

    private fun collectionOf(write: Write): PgCollection {
        val map = write.mapId
        val schema = session.storage[session.storage.mapIdToSchema(map)]
        if (!schema.exists()) throw NakshaException(MAP_NOT_FOUND, "No such map: $map")
        val collectionId = write.collectionId
        val collection = schema[collectionId]
        if (!collection.exists()) throw NakshaException(COLLECTION_NOT_FOUND, "No such collection: $collectionId")
        return collection
    }

    internal fun insert(write: Write) : Row {
        val collection = collectionOf(write)
        TODO("Implement me")
    }

    internal fun update(write: Write) : Row {
        TODO("Implement me")
    }

    internal fun upsert(write: Write) : Row {

        TODO("Implement me")
    }

    internal fun delete(write: Write) : Row {
        TODO("Implement me")
    }

    internal fun purge(write: Write) : Row {
        TODO("Implement me")
    }
}