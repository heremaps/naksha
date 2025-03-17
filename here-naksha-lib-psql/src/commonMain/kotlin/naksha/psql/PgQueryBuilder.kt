package naksha.psql

import naksha.model.*
import naksha.model.request.*
import naksha.model.request.query.MetaColumn
import naksha.model.request.query.SortOrder.SortOrderCompanion.ASCENDING
import naksha.psql.PgColumn.PgColumnCompanion.next_tn
import kotlin.math.max
import kotlin.math.min

/**
 * Create a new SQL query of a read request (see [build])
 * @property session the session for which the query is created.
 * @property readRequest the read request for which to generate the SQL query.
 * @since 3.0
 */
class PgQueryBuilder(val session: PgSession, val readRequest: ReadRequest) {

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
            else -> throw unsupportedOp("The given read-request is unknown")
        }
    }

    private fun readFeatures(req: ReadFeatures): PgQuery {
        // Collect needed data
        val pgStorage = session.storage
        val mapId = req.mapId ?: NakshaContext.mapId()
        val pgMap = session.getPgMapById(mapId) ?: throw mapNotFound("Map with id '$mapId' does not exist")
        // We select what the client wants, maximum is always 16777216
        // Finally, the storage can limit result-size further down below 16777216 (normally we do not expect this to happen).
        val REQ_LIMIT = min(max(0, req.limit ?: 16777216), session.storage.hardCap)
        if (REQ_LIMIT == 0) throw illegalArg("Invalid limit given: ${req.limit}, must be 0 to 16777216")
        val pgCollections: MutableList<PgCollection> = mutableListOf()
        for (collectionId in req.collectionIds) {
            if (collectionId == null) continue
            val pgCollection = session.getPgCollectionById(pgMap, collectionId) ?:
                throw collectionNotFound("Collection with id '$collectionId' not found in map '$mapId'")
            pgCollections.add(pgCollection)
        }
        if (pgCollections.size <= 0) throw illegalArg("Empty collection-ids in request")
        val version = req.version
        if (version != null && !req.queryHistory) {
            throw illegalArg("Setting 'version' to '$version' requires that 'queryHistory' is enabled!")
        }
        val minVersion = req.minVersion
        if (minVersion != null && !req.queryHistory) {
            throw illegalArg("Setting 'minVersion' to '$minVersion' requires that 'queryHistory' is enabled!")
        }
        val versions = req.versions
        if (versions != 1 && !req.queryHistory) {
            throw illegalArg("Setting 'versions' to $versions requires that 'queryHistory' is enabled!")
        }
        if (versions < 1) {
            throw illegalArg("It is not possible to request less than one version of each feature")
        }

        val whereClause = PgQueryWhereBuilder(req).build()
        val whereQuery = whereClause?.where ?: ""
        val thePgCollection = if (pgCollections.size == 1) pgCollections[0] else null

        // Columns to select, so `col_num`, `tn`, and whatever is needed for ordering.
        val select_cols = mutableListOf("col_num", "tn")
        // Column name and ordering (ASC | DESC) for customer order.
        val order_by = mutableListOf<Pair<String,String>>()
        req.orderBy?.also { orderBy ->
            // The default order is descending by tuple-number, therefore we do not need
            //   any special order, when the client asks for deterministic, or it asks
            //   for tuple-number ordering (except he asks for ascending ordering)
            if (!orderBy.isDeterministic()
                && !(orderBy.column == MetaColumn.tupleNumber() && orderBy.sortOrder != ASCENDING && orderBy.next == null)
            ) {
                val selected_names = mutableListOf<String>()
                var o: OrderBy? = orderBy
                while (o != null) {
                    val col = o.column
                    if (col != null) {
                        // TODO: Support all columns
                        val col_name = col.name
                        val pgColumn = when (col_name) {
                            MetaColumn.ATTACHMENT -> PgColumn.attachment
                            MetaColumn.ID -> PgColumn.id
                            MetaColumn.VERSION -> PgColumn.tn
                            MetaColumn.TUPLE_NUMBER -> PgColumn.tn
                            MetaColumn.HASH -> PgColumn.hash
                            MetaColumn.HERE_TILE -> PgColumn.here_tile
                            MetaColumn.AUTHOR -> PgColumn.author
                            MetaColumn.AUTHOR_TS -> PgColumn.author_ts
                            MetaColumn.APP_ID -> PgColumn.app_id
                            MetaColumn.CHANGE_COUNT -> PgColumn.cc
                            MetaColumn.CV0 -> PgColumn.cv0
                            MetaColumn.CV1 -> PgColumn.cv1
                            MetaColumn.CV2 -> PgColumn.cv2
                            MetaColumn.CV3 -> PgColumn.cv3
                            MetaColumn.CS0 -> PgColumn.cs0
                            MetaColumn.CS1 -> PgColumn.cs1
                            MetaColumn.CS2 -> PgColumn.cs2
                            MetaColumn.CS3 -> PgColumn.cs3
                            else -> throw illegalArg("Invalid column for ordering: '$col_name'")
                        }
                        if (!selected_names.contains(col_name)) {
                            selected_names.add(col_name)
                            if (!select_cols.contains(pgColumn.name)) {
                                select_cols.add(pgColumn.name)
                            }
                            val SORT = if (o.sortOrder == ASCENDING) "ASC" else "DESC"
                            order_by.add(when (pgColumn) {
                                PgColumn.author_ts -> Pair("COALESCE(${PgColumn.author_ts}, ${PgColumn.updated_at})", SORT)
                                PgColumn.tn -> if (col_name == MetaColumn.VERSION)
                                                Pair("naksha_tn_version(tn)", SORT)
                                           else Pair("tn", SORT)
                                else -> Pair(pgColumn.name, SORT)
                            })
                        }
                    }
                    o = o.next
                }
            }
        }

        // Generate query.
        val selects = StringBuilder()
        for (entry in pgCollections.withIndex()) {
            val pgCollection = entry.value
            val map = pgCollection.map
            val head = pgCollection.headTable

            // Note: To simplify queries, we actually always embed the collection-number internally,
            //       eventually, before returning the result, we decide if we put it into the header
            //       of the tuple-number-binary or individually into each row-identifier.
            select_cols[0] = "${pgCollection.number} AS col_num"
            val select_cols_string = select_cols.joinToString(", ")

            val where = if (whereQuery.isEmpty()) "" else "WHERE $whereQuery"
            if (selects.isNotEmpty()) selects.append(" UNION ALL\n")
            selects.append("\t(SELECT $select_cols_string FROM ${map.quotedId}.${head.quotedName} $where)\n")

            val deleted = pgCollection.deletedTable
            if (!req.queryHistory && req.queryDeleted && deleted != null) {
                if (selects.isNotEmpty()) selects.append(" UNION ALL\n")
                selects.append("\t(SELECT $select_cols_string FROM ${map.quotedId}.${deleted.quotedName} $where)\n")
            }

            val history = pgCollection.historyTable
            if (req.queryHistory && history != null) {
                // TODO: We need to improve, because we only want $versions variants!
                // If only one version is requested, we can improve the query to only return this version!
                val better_where = if (version != null && versions == 1)
                    (if (where.isEmpty()) "WHERE " else "$where AND ") + "naksha_tn_version($next_tn) > $version"
                else
                    where
                if (selects.isNotEmpty()) selects.append(" UNION ALL\n")
                selects.append("\t(SELECT $select_cols_string FROM ${map.quotedId}.${history.quotedName} $better_where)\n")
            }
        }
        // Restore original value for `col_num` selection.
        select_cols[0] = "col_num"

        // The final tuple-number-binary to be returned, optimized for size!
        val gzip = if (thePgCollection == null)
        // If we select from multiple collections, we have to encode the collection-number in the tuple-number.
        // This results in 128-bit per row, aka 16-byte per row, but 4 byte less in the header.
        """gzip(
  int4send((0 << 28)|(2 << 24)|sum(1)::int)|| -- type (0), subtype (2), length
  int4send(20 + sum(1)::int*16)|| -- byte-size
  int8send(${pgStorage.number})|| -- shared storage-number
  int4send(${pgMap.number})|| -- shared map-number
  bytea_agg(int4send(col_num)||tn) -- aggregate all tuple-number, embed collection-number
)""" else
    // If we select only from exactly one table, we can embed the collection-number into the binary header.
    // This reduces the encoding of each tuple-number to 96-bit (12-byte), but adds 4-byte into the header
    // for the shared collection-number.
    """gzip(
  int4send((0 << 28)|(3 << 24)|sum(1)::int)|| -- type (0), subtype (3), length
  int4send(24 + sum(1)::int*12)|| -- byte-size
  int8send(${pgStorage.number})|| -- shared storage-number
  int4send(${pgMap.number})|| -- shared map-number
  int4send(${thePgCollection.number})|| -- shared collection-number
  bytea_agg(tn) -- aggregate all tuple-number
)"""

        // The columns we need until the last final result building.
        val select_cols_string = select_cols.joinToString(", ")

        // If history is queried, and only a certain amount of versions should be returned
        // we need to partition the result, so that we can select have the requested amount of versions.
        val part = if (req.queryHistory && versions > 1) """, query_with_v AS (
  SELECT
    $select_cols_string,
    ROW_NUMBER() OVER (PARTITION BY col_num, naksha_tn_feature_number(tn) ORDER BY tn DESC) AS v
  FROM query
), part AS (
  SELECT $select_cols_string
  FROM query_with_v
  WHERE v <= $versions
)""" else ""

        // `order_by` may be empty, if no custom ordering was requested.
        val order_by_string = order_by.joinToString(", ") { "${it.first} ${it.second}" }

        // apply limit and order, if given
        val limited = """, limited AS (
  SELECT $select_cols_string
  FROM ${if (part.isNotEmpty()) "part" else "query"}
  ${if (order_by_string.isNotEmpty()) "ORDER BY $order_by_string " // Explicit ordering.
    else if (part.isNotEmpty()) "ORDER BY col_num DESC, tn DESC " // If multiple versions requested, order by version.
    else "" // No explicit ordering, no multiple versions, use random oder
  }LIMIT $REQ_LIMIT
)"""

        // The final SQL query.
        val SQL = """WITH query AS (
$selects)$part$limited
SELECT $gzip AS rs
FROM limited"""
        return PgQuery(
            sql = SQL,
            argValues = whereClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = whereClause?.argTypeNames ?: emptyArray(),
        )
    }
}