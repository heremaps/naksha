package naksha.psql

import naksha.model.*
import naksha.model.request.*
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
            /*
            WITH query AS (
                (SELECT -14938719 AS col_num, tn FROM naksha_psql_test.read_by_tags_index_test WHERE  ( (naksha_tags(tags, flags) ?? $1 OR naksha_tags(tags, flags) ?? $2 OR naksha_tags(tags, flags) ?? $3 OR naksha_tags(tags, flags) ?? $4 OR naksha_tags(tags, flags) ?? $5 OR naksha_tags(tags, flags) ?? $6 OR naksha_tags(tags, flags) ?? $7 OR naksha_tags(tags, flags) ?? $8 OR naksha_tags(tags, flags) ?? $9 OR naksha_tags(tags, flags) ?? $10 OR naksha_tags(tags, flags) ?? $11 OR naksha_tags(tags, flags) ?? $12 OR naksha_tags(tags, flags) ?? $13 OR naksha_tags(tags, flags) ?? $14 OR naksha_tags(tags, flags) ?? $15 OR naksha_tags(tags, flags) ?? $16 OR naksha_tags(tags, flags) ?? $17 OR naksha_tags(tags, flags) ?? $18 OR naksha_tags(tags, flags) ?? $19 OR naksha_tags(tags, flags) ?? $20 OR naksha_tags(tags, flags) ?? $21 OR naksha_tags(tags, flags) ?? $22 OR naksha_tags(tags, flags) ?? $23 OR naksha_tags(tags, flags) ?? $24 OR naksha_tags(tags, flags) ?? $25 OR naksha_tags(tags, flags) ?? $26 OR naksha_tags(tags, flags) ?? $27 OR naksha_tags(tags, flags) ?? $28 OR naksha_tags(tags, flags) ?? $29 OR naksha_tags(tags, flags) ?? $30 OR naksha_tags(tags, flags) ?? $31 OR naksha_tags(tags, flags) ?? $32 OR naksha_tags(tags, flags) ?? $33 OR naksha_tags(tags, flags) ?? $34 OR naksha_tags(tags, flags) ?? $35 OR naksha_tags(tags, flags) ?? $36 OR naksha_tags(tags, flags) ?? $37 OR naksha_tags(tags, flags) ?? $38 OR naksha_tags(tags, flags) ?? $39 OR naksha_tags(tags, flags) ?? $40 OR naksha_tags(tags, flags) ?? $41 OR naksha_tags(tags, flags) ?? $42 OR naksha_tags(tags, flags) ?? $43 OR naksha_tags(tags, flags) ?? $44 OR naksha_tags(tags, flags) ?? $45 OR naksha_tags(tags, flags) ?? $46 OR naksha_tags(tags, flags) ?? $47 OR naksha_tags(tags, flags) ?? $48 OR naksha_tags(tags, flags) ?? $49 OR naksha_tags(tags, flags) ?? $50 OR naksha_tags(tags, flags) ?? $51 OR naksha_tags(tags, flags) ?? $52 OR naksha_tags(tags, flags) ?? $53 OR naksha_tags(tags, flags) ?? $54 OR naksha_tags(tags, flags) ?? $55 OR naksha_tags(tags, flags) ?? $56 OR naksha_tags(tags, flags) ?? $57 OR naksha_tags(tags, flags) ?? $58 OR naksha_tags(tags, flags) ?? $59 OR naksha_tags(tags, flags) ?? $60 OR naksha_tags(tags, flags) ?? $61 OR naksha_tags(tags, flags) ?? $62 OR naksha_tags(tags, flags) ?? $63 OR naksha_tags(tags, flags) ?? $64 OR naksha_tags(tags, flags) ?? $65 OR naksha_tags(tags, flags) ?? $66 OR naksha_tags(tags, flags) ?? $67 OR naksha_tags(tags, flags) ?? $68 OR naksha_tags(tags, flags) ?? $69 OR naksha_tags(tags, flags) ?? $70 OR naksha_tags(tags, flags) ?? $71 OR naksha_tags(tags, flags) ?? $72 OR naksha_tags(tags, flags) ?? $73 OR naksha_tags(tags, flags) ?? $74 OR naksha_tags(tags, flags) ?? $75 OR naksha_tags(tags, flags) ?? $76 OR naksha_tags(tags, flags) ?? $77 OR naksha_tags(tags, flags) ?? $78 OR naksha_tags(tags, flags) ?? $79 OR naksha_tags(tags, flags) ?? $80 OR naksha_tags(tags, flags) ?? $81 OR naksha_tags(tags, flags) ?? $82 OR naksha_tags(tags, flags) ?? $83 OR naksha_tags(tags, flags) ?? $84 OR naksha_tags(tags, flags) ?? $85 OR naksha_tags(tags, flags) ?? $86 OR naksha_tags(tags, flags) ?? $87 OR naksha_tags(tags, flags) ?? $88 OR naksha_tags(tags, flags) ?? $89 OR naksha_tags(tags, flags) ?? $90 OR naksha_tags(tags, flags) ?? $91 OR naksha_tags(tags, flags) ?? $92 OR naksha_tags(tags, flags) ?? $93 OR naksha_tags(tags, flags) ?? $94 OR naksha_tags(tags, flags) ?? $95 OR naksha_tags(tags, flags) ?? $96 OR naksha_tags(tags, flags) ?? $97 OR naksha_tags(tags, flags) ?? $98 OR naksha_tags(tags, flags) ?? $99 OR naksha_tags(tags, flags) ?? $100) ))
            ), limited AS (
              SELECT col_num, tn
              FROM query
              LIMIT 16777216
            )
            SELECT tn FROM limited
            -- should be SELECT col_num, tn FROM limited
            -- col_num DOES NOT have to be included all the time
            -- col_num == collection number, not always needed (ie when reading single collection)
             */
            val query = PgQueryBuilder(session, request).build()
            val conn = session.useConnection()
            session.storage.adminMap.setSearchPath(conn)
            // TODO: Use prepare, add arguments!
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
