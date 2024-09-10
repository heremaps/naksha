package naksha.psql.executors

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
        var allBytes: ByteArray = byteArrayOf()
        while(cursor.next()){
            val bytes: ByteArray = cursor.column(tuple_number) as ByteArray
            allBytes += bytes
        }
        cursor.close()
        val tupleNumberBytes = TupleNumberByteArray(storage, allBytes)
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

    fun plan(): PgPlan {
        TODO()
        conn.prepare(
            """
            SELECT
        """.trimIndent()
        )
    }
}