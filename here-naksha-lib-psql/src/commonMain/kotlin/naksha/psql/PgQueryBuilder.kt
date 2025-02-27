package naksha.psql

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.request.*
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.tn
import naksha.psql.PgColumn.PgColumnCompanion.txn_next
import kotlin.math.max
import kotlin.math.min

/**
 * Create a new SQL query of a read request (see [build])
 * @property session the session for which the query is created.
 * @property readRequest the read request for which to generate the SQL query.
 * @since 3.0
 */
class PgQueryBuilder(val session: PgSession, val readRequest: ReadRequest) {

    // TODO: Add support for reading multiple versions using (req.versions = 2+)!
    // TODO: Add support for orderBy!

    /**
     * Create the [PgQuery] from the given request.
     * @return the [PgQuery].
     * @since 3.0
     */
    fun build(): PgQuery {
        return when (readRequest) {
            is ReadFeatures -> readFeatures(readRequest)
            is ReadCollections -> readFeatures(readRequest.toReadFeatures())
            is ReadMaps -> readFeatures(readRequest.toReadFeatures())
            else -> throw NakshaException(UNSUPPORTED_OPERATION, "The given read-request is unknown")
        }
    }

    private fun readFeatures(req: ReadFeatures): PgQuery {
        if (req.versions < 1) {
            throw NakshaException(
                ILLEGAL_ARGUMENT,
                "It is not possible to request less than one version of each feature"
            )
        }

        // Collect needed data
        val pgStorage = session.storage
        val mapId = req.mapId ?: NakshaContext.mapId()
        val pgMap = session.getPgMapById(mapId) ?: throw NakshaException(ILLEGAL_ARGUMENT, "Map with id '$mapId' does not exist")
        // We select what the client wants, maximum is always 16777216
        // Finally, the storage can limit result-size further down below 16777216 (normally we do not expect this to happen).
        val REQ_LIMIT = max(min(0, req.limit ?: 16777216), session.storage.hardCap)
        if (REQ_LIMIT == 0) throw NakshaException(ILLEGAL_ARGUMENT, "Invalid limit given: ${req.limit}, must be 0 to 16777216")
        val pgCollections: MutableList<PgCollection> = mutableListOf()
        for (collectionId in req.collectionIds) {
            if (collectionId == null) continue
            val pgCollection = session.getPgCollectionById(pgMap, collectionId) ?:
                throw NakshaException(ILLEGAL_ARGUMENT, "Collection with id '$collectionId' not found in map '$mapId'")
            pgCollections.add(pgCollection)
        }
        if (pgCollections.size <= 0) throw NakshaException(ILLEGAL_ARGUMENT, "Empty collection-ids in request")
        val whereClause = PgQueryWhereBuilder(req).build()
        val whereQuery = whereClause?.where ?: ""
        val thePgCollection = if (pgCollections.size == 1) pgCollections[0] else null
        val versions = req.versions
        val txn = req.version
        val txn_min = req.minVersion

        // Generate query.
        val selects = StringBuilder()
        for (entry in pgCollections.withIndex()) {
            val pgCollection = entry.value
            val map = pgCollection.map
            val head = pgCollection.headTable
            // We only need to select the column number, if we select from multiple collections!
            val col_num = if (thePgCollection == null) "${pgCollection.number} AS col_num, " else ""
            val where = if (whereQuery.isEmpty()) "" else "WHERE $whereQuery"
            selects.append("\t(SELECT $col_num$tn FROM ${map.quotedId}.${head.quotedName} $where)\n")

            val deleted = pgCollection.deletedTable
            if (req.queryDeleted && deleted != null) {
                selects.append("\t(SELECT $col_num$tn FROM ${map.quotedId}.${deleted.quotedName} $where)\n")
            }

            val history = pgCollection.historyTable
            if (req.queryHistory && history != null && (txn != null || txn_min != null || versions != 1)) {
                // If only one version is requested, we can improve the query to only return this version!
                val better_where = if (txn != null && versions == 1)
                    (if (where.isEmpty()) "WHERE " else "$where AND ") + "$txn_next > $txn"
                else
                    where
                selects.append("\t(SELECT $col_num$tn FROM ${map.quotedId}.${history.quotedName} $better_where")
            }

            if (entry.index < pgCollections.lastIndex) selects.append(" UNION ALL\n")
        }
        val SQL = if (thePgCollection == null)
// If we select from multiple collections, we have to encode the collection-number in the tuple-number.
// This results in 128-bit per row, aka 16-byte per row, but 4 byte less in the header.
"""WITH query AS (
$selects),
result AS (
  SELECT DISTINCT col_num, tn
  FROM query
  ORDER BY col_num, tn
  LIMIT $REQ_LIMIT
)
SELECT gzip( -- compress the binary
 int4send((0 << 28)|(2 << 24)|sum(1)::int)|| -- type (0), subtype (2), length
 int4send(20 + sum(1)::int*16)|| -- byte-size
 int8send(${pgStorage.number})|| -- shared storage-number
 int4send(${pgMap.number})|| -- shared map-number
 bytea_agg(int4send(col_num)||tn) -- aggregate all tuple-number, embed collection-number
) AS rs FROM result;""" else
// If we select only from exactly one table, we can embed the collection-number into the binary header.
// This reduces the encoding of each tuple-number to 96-bit (12-byte), but adds 4-byte into the header
// for the shared collection-number.
"""WITH query AS (
$selects),
result AS (
  SELECT DISTINCT tn
  FROM query
  ORDER BY tn
  LIMIT $REQ_LIMIT
)
SELECT gzip( -- compress the binary
 int4send((0 << 28)|(3 << 24)|sum(1)::int)|| -- type (0), subtype (3), length
 int4send(24 + sum(1)::int*12)|| -- byte-size
 int8send(${pgStorage.number})|| -- shared storage-number
 int4send(${pgMap.number})|| -- shared map-number
 int4send(${thePgCollection.number})|| -- shared collection-number
 bytea_agg(tn) -- aggregate all tuple-number
) AS rs FROM result;"""
        return PgQuery(
            sql = SQL,
            argValues = whereClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = whereClause?.argTypeNames ?: emptyArray(),
        )
    }
}
