package naksha.psql.executors

import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaException
import naksha.model.TupleNumberByteArray
import naksha.model.Version
import naksha.model.request.*
import naksha.psql.*
import kotlin.jvm.JvmField

class PgReader(
    /**
     * The session to which this reader is linked.
     */
    @JvmField val session: PgSession,

    /**
     * The read request to [execute].
     */
    @JvmField val request: ReadRequest,
) {

    /**
     * The connection to use.
     */
    val conn: PgConnection = session.usePgConnection()

    /**
     * The storage.
     */
    val storage: PgStorage = session.storage

    /**
     * The version of which this reader is part.
     */
    val version: Version
        get() = session.version()

    fun execute(): Response {
        val query = PgQuery(session, request)
        val connection = session.usePgConnection()
        // TODO: Use prepare, add arguments!
        val plan = connection.prepare(query.sql, query.paramTypes)
        plan.use {
            val allBytes: ByteArray?
            val cursor = plan.execute(query.paramValues)
            cursor.use {
                allBytes = if (cursor.next()) cursor.column("rs") as ByteArray else null
            }
            if (allBytes == null) throw NakshaException(EXCEPTION, "Failed to execute query for unknown reason")
            val tupleNumberBytes = TupleNumberByteArray.fromGzip(storage, allBytes)
            return SuccessResponse(
                PgResultSet(
                    storage,
                    session,
                    tupleNumberBytes,
                    incomplete = false,
                    validTill = tupleNumberBytes.size,
                    offset = 0,
                    limit = tupleNumberBytes.size,
                    orderBy = null,
                    filters = request.resultFilters
                )
            )
        }
    }
}