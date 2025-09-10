package naksha.psql

import naksha.base.PlatformUtil
import naksha.model.*
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
        get() = session.useTransaction().version

    fun execute(): Response {
        try {
            val session = this.session
            val query = PgQueryBuilder(session, request).build()
            val conn = session.useConnection()
            session.storage.adminMap.setSearchPath(conn)
            if (PlatformUtil.ENABLE_INFO) {
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
                val featureTuples = FeatureTupleList()
                featureTuples.setCapacity(1024)
                // Note: We know that each result is only 12 or 20 byte
                plan.setFetchSize(100_000)
                plan.execute(query.argValues).use { cursor ->
                    val storageNumber = query.storageNumber
                    val mapNumber = query.mapNumber
                    val collectionNumber = query.collectionNumber
                    while (cursor.next()) {
                        val col_num: Int = collectionNumber ?: cursor["col_num"]
                        val tn: ByteArray = cursor["tn"]
                        featureTuples.add(FeatureTuple(TupleNumber.fromB160(tn, storageNumber, mapNumber, col_num)))
                    }
                }
                return SuccessResponse().withFeatureTupleList(featureTuples)
            }
        } catch (e: Exception) {
            val nakshaException = PgExceptionMapper.map(e)
            return ErrorResponse(nakshaException)
        }
    }
}
