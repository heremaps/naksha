package naksha.psql.executors

import naksha.model.NakshaError
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaException
import naksha.model.TupleNumberByteArray
import naksha.model.Version
import naksha.model.request.*
import naksha.psql.*
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.read.ReadQueryBuilder
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
        return when (request) {
            is ReadFeatures -> readFeatures()
            is ReadCollections -> readCollections()
            else -> throw UnsupportedOperationException("Not implemented handling of: ${request::class}")
        }
    }

    private fun readCollections(): Response {
        TODO()
    }

    private fun readFeatures(): Response {
        val query = ReadQueryBuilder().build(request)
        val connection = session.usePgConnection()
        val cursor = connection.execute(query.rawSql)
        cursor.fetch()
        val rawTupleNumber = cursor.column(tuple_number)
        if (rawTupleNumber is ByteArray) {
            val tupleNumberBytes = TupleNumberByteArray(storage, rawTupleNumber)
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
        } else {
            return ErrorResponse(NakshaException(EXCEPTION, "expected $tuple_number as byte_array"))
        }
    }

    fun plan(): PgPlan {
        TODO()
        conn.prepare(
            """
            SELECT
        """.trimIndent()
        )
    }
}