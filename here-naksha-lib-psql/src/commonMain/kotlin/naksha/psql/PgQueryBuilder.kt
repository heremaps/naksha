package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.HARD_TUPLE_LIMIT
import naksha.model.request.*
import naksha.model.request.query.SortOrder
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.NEXT_VERSION_NAME
import naksha.psql.PgColumn.PgColumn_C.VERSION
import naksha.psql.PgColumn.PgColumn_C.VERSION_NAME
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
        val catalogId = req.catalogId ?: throw illegalArg("Request has not 'catalogId'")
        val pgCatalog = session.getPgCatalogById(catalogId) ?: throw mapNotFound("Catalog with id '$catalogId' does not exist")
        val REQ_LIMIT = min(max(0, req.limit ?: HARD_TUPLE_LIMIT), session.storage.hardCap)
        if (REQ_LIMIT == 0) throw illegalArg("Invalid limit given: ${req.limit}, must be 0 to $HARD_TUPLE_LIMIT")
        val collectionId: String = req.collectionId ?: throw illegalArg("Request has no 'collectionId'")
        val pgCollection = session.getPgCollectionById(pgCatalog, collectionId) ?:
            throw collectionNotFound("Collection with id '$collectionId' not found in catalog '$catalogId'")

        val whereClause = PgQueryWhereBuilder(req, pgCollection).build()
        val whereQuery = whereClause?.where ?: ""

        // Column name and ordering (ASC | DESC) for customer order.
        var order_by: MutableList<Pair<String, String>>? = null
        do { // scope
            var orderBy = req.orderBy
            if (orderBy != null) {
                order_by = mutableListOf()
                if (!orderBy.isDeterministic()) {
                    var exit = 10
                    while (orderBy != null && exit >= 0) {
                        val memberName = orderBy.member ?: throw illegalArg("Missing member in orderBy")
                        // ANY resolves to DESC (latest-first); only explicit ASCENDING gives ASC.
                        val sortOrder = if (orderBy.sortOrder == SortOrder.ASCENDING) "ASC" else "DESC"
                        order_by.add(Pair(memberName, sortOrder))
                        orderBy = orderBy.next
                        exit--
                    }
                    if (exit < 0) throw illegalArg("Too many orders in orderBy")
                } else { // deterministic ordering is by `feature-number ASC, version DESC`
                    order_by.add(Pair(FN.toString(), SortOrder.ASCENDING.toString()))
                    order_by.add(Pair(VERSION.toString(), SortOrder.DESCENDING.toString()))
                }
            }
        } while(false)

        // TODO: We want to use placeholders!

        val WHERE = StringBuilder()
        var version = req.version
        if (version != null) {
            version = version or Int64(3)
            if (version == Version.HEAD.number) {
                version = null // HEAD
            } else {
                if (WHERE.isNotEmpty()) WHERE.append(" AND ")
                WHERE.append("($VERSION_NAME <= ").append(version).append(")")
            }
        }
        var minVersion = req.minVersion
        if (minVersion != null) {
            minVersion = minVersion or Int64(3)
            if (WHERE.isNotEmpty()) WHERE.append(" AND ")
            WHERE.append("($VERSION_NAME >= ").append(minVersion).append(")")
        }
        val readHistory = req.queryHistory && pgCollection.storeHistory
        val versions = req.versions
        if (readHistory && versions == 1 && version != null) { // Return latest version only, but involve history.
            if (WHERE.isNotEmpty()) WHERE.append(" AND ")
            WHERE.append("($NEXT_VERSION_NAME > ").append(version).append(" OR $NEXT_VERSION_NAME IS NULL) ")
        }
        if (whereQuery.isNotEmpty()) {
            if (WHERE.isNotEmpty()) WHERE.append(" AND ")
            WHERE.append(whereQuery)
        }
        val where = if (WHERE.isEmpty()) "" else " WHERE $WHERE"
        // The live-only filter applies to HEAD only; history queries must still return archived DELETE rows.
        val headWhere = if (req.queryDeleted) where else {
            val hw = StringBuilder(WHERE)
            if (hw.isNotEmpty()) hw.append(" AND ")
            hw.append("($VERSION_NAME & 3) < 2 ")
            " WHERE $hw"
        }

        val baseCols = listOf(FN.toString(), VERSION.toString())
        val extraCols = order_by?.map { it.first }?.filter { it !in baseCols }?.distinct() ?: emptyList()
        val selectCols = (baseCols + extraCols).joinToString(", ")

        // The query starts with select all tuple-numbers of matching tuple.
        val query = if (readHistory) """query AS
( SELECT $selectCols FROM ${pgCatalog.quotedId}.${pgCollection.headTable.quotedName}$headWhere
  UNION ALL
  SELECT $selectCols FROM ${pgCatalog.quotedId}.${pgCollection.historyTable.quotedName}$where )""" else """query AS
( SELECT $selectCols FROM ${pgCatalog.quotedId}.${pgCollection.headTable.quotedName}$headWhere )"""

        // If history is queried, and only a certain amount of versions should be returned,
        // or we want only the latest version, but no specific version target is given, then
        // we need to partition the result, so that we can select the requested amount of latest versions.
        val all = if (readHistory && versions > 1) """, query_partitioned AS
(SELECT $selectCols, ROW_NUMBER() OVER (PARTITION BY $FN ORDER BY $VERSION DESC) AS v FROM query)
, all_versions AS
(SELECT $selectCols FROM query_partitioned WHERE v <= $versions)""" else ""

        // `order_by` may be empty, if no custom ordering was requested.
        val order_by_string = order_by?.joinToString(", ") { "${it.first} ${it.second}" } ?: ""

        // apply limit and order, if given
        val limited = """, limited AS
(SELECT $selectCols FROM ${if (all.isNotEmpty()) "all_versions" else "query"} ${if (order_by_string.isNotEmpty()) "ORDER BY $order_by_string "
else if (all.isNotEmpty()) "ORDER BY $FN ASC, $VERSION DESC " else ""}LIMIT $REQ_LIMIT)"""

        // The final SQL query.
        // We only need `col_num`, `fn`, and `version`; the additional columns in limit were only for sorting.
        // If we only select from a single collection (thePgCollection != null), `col_num` is implicit.
        val SQL = """WITH $query$all$limited
SELECT $FN AS fn, $VERSION AS version FROM limited"""
        return PgQuery(
            sql = SQL,
            argValues = whereClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = whereClause?.argTypeNames ?: emptyArray(),
            pgStorage.number,
            pgCatalog.catalogNumber,
            pgCollection.collectionNumber
        )
    }
}