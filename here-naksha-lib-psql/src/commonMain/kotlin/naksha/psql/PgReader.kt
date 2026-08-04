package naksha.psql

import naksha.base.BaseUtil
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.TupleNumberList
import naksha.model.request.*
import kotlin.jvm.JvmField

class PgReader(
    /**
     * The session to which this reader is linked.
     */
    session: PgSession,

    /**
     * The read request to [execute].
     */
    @JvmField val request: ReadRequest,
) : PgReaderWriterBase(session) {

    /**
     * The connection to use.
     */
    val conn: PgConnection = session.useConnection()

    /**
     * The storage.
     */
    val storage: PgStorage = session.storage

    /**
     * The version of which this reader is part.
     */
    val version: Version
        get() = session.useTransaction().asVersion

    fun execute(): Response {
        try {
            val session = this.session
            val query = PgQueryBuilder(session, request).build()
            val conn = session.useConnection()
            session.storage.adminCatalog.setSearchPath(conn)
            if (BaseUtil.ENABLE_INFO) {
                if (session.logQueries) {
                    session.logAtInfo(query.sql)
                }
                if (session.logExplain) {
                    val explain = session.explain(conn, false, query.sql, query.argTypes, query.argValues)
                    session.logAtInfo(explain)
                }
            }
            conn.prepare(query.sql, query.argTypes).use { plan ->
                // Start allocating around 8 KiB
                val tupleNumbers = TupleNumberList()
                tupleNumbers.setCapacity(1024)
                // Note: We know that each result is only 12 or 20 byte
                plan.setFetchSize(1_000_000) // Set very high to encourage parallelism on server side
                // https://www.cybertec-postgresql.com/en/parallel-query-postgresql-problems-jdbc-dbeaver/
                plan.execute(query.argValues).use { cursor ->
                    val databaseNumber = query.databaseId.number
                    val catalogNumber = query.catalog.id.number.toInt()
                    val collectionNumber = query.collection.id.number.toInt()
                    while (cursor.next()) {
                        val fn: Long = cursor["fn"]
                        val version: Long = cursor["version"]
                        val tn = TupleNumber(databaseNumber, catalogNumber, collectionNumber, fn, version)
                        tupleNumbers.add(tn)
                    }
                }
                val rs = TupleNumberResultSet(request, storage, session, tupleNumbers)
                return SuccessResponse().withResultSet(rs)
            }
        } catch (e: Exception) {
            val nakshaException = PgExceptionMapper.map(e)
            return ErrorResponse(nakshaException)
        }
    }
}
