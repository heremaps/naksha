package naksha.psql

import naksha.model.*
import naksha.model.request.*
import naksha.model.request.query.SortOrder.SortOrderCompanion.ASCENDING
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
        val catalogId = req.catalogId ?: throw illegalArg("catalogId is missing")
        val pgCatalog = session.getPgCatalogById(catalogId) ?: throw mapNotFound("Catalog with id '$catalogId' does not exist")
        val REQ_LIMIT = min(max(0, req.limit ?: Naksha.HARD_TUPLE_LIMIT), session.storage.hardCap)
        if (REQ_LIMIT == 0) throw illegalArg("Invalid limit given: ${req.limit}, must be 0 to 16777216")
        val pgCollections: MutableList<PgCollection> = mutableListOf()
        for (collectionId in req.collectionIds) {
            if (collectionId == null) continue
            val pgCollection = session.getPgCollectionById(pgCatalog, collectionId) ?:
                throw collectionNotFound("Collection with id '$collectionId' not found in map '$catalogId'")
            pgCollections.add(pgCollection)
        }
        if (pgCollections.isEmpty()) throw illegalArg("Empty collection-ids in request")
        val version = req.version
        val minVersion = req.minVersion
        val versions = req.versions
        if (versions < 1) throw illegalArg("It is not possible to request less than one version of each feature")

        val whereClause = PgQueryWhereBuilder(req).build()
        val whereQuery = whereClause?.where ?: ""
        val thePgCollection = if (pgCollections.size == 1) pgCollections[0] else null

        // Columns to select, so `col_num`, `fn`, `version`, and whatever is needed for ordering.
        val select_cols = mutableListOf("col_num", "fn", "version")
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
                            MetaColumn.VERSION -> PgColumn.version
                            // No single "tuple-number" column exists post-refactor; sort by `fn`
                            // as a pragmatic approximation of the natural primary order.
                            MetaColumn.TUPLE_NUMBER -> PgColumn.fn
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
            val map = pgCollection.catalog
            val read = PgRead(pgCatalog, pgCollection)

            // Note: To simplify queries, we actually always embed the collection-number internally,
            //       eventually, before returning the result, we decide if we put it into the header
            //       of the tuple-number-binary or individually into each row-identifier.
            select_cols[0] = "${pgCollection.collectionNumber} AS col_num"
            val select_cols_string = select_cols.joinToString(", ")

            val where = if (whereQuery.isEmpty()) "" else "WHERE $whereQuery"
            // HEAD has no `next_version` column — substitute with NULL so predicates referencing
            // it evaluate to NULL (no HEAD rows match), matching the "no successor yet" semantics.
            val whereForHeadBase = whereQuery.replace(Regex("\\b${next_version.name}\\b"), "NULL::int8")
            // Filter tombstones from HEAD by default: only live rows ((version & 3) < 2).
            // queryDeleted=true is the sole gate that lifts this filter and exposes tombstones.
            // queryHistory=true is fully orthogonal — it adds past states from the history table
            // but does NOT change tombstone visibility in HEAD.
            val deletedFilter = if (!req.queryDeleted) "(version & 3) < 2" else null
            val whereForHead = when {
                whereForHeadBase.isEmpty() && deletedFilter != null -> "WHERE $deletedFilter"
                whereForHeadBase.isEmpty() -> ""
                deletedFilter != null -> "WHERE $whereForHeadBase AND $deletedFilter"
                else -> "WHERE $whereForHeadBase"
            }
            for (head in read.headTables) {
                if (selects.isNotEmpty()) selects.append(" UNION ALL\n")
                selects.append("\t(SELECT $select_cols_string FROM ${map.quotedId}.${head.quotedName} $whereForHead)\n")
            }

            // The shadow/deleted table has been removed. Deleted rows now live in HEAD with (version & 3) == 2.
            // queryDeleted=true is handled above by omitting the tombstone filter on HEAD.

            val historyTables = read.historyTables
            if (req.queryHistory && historyTables != null) {
                // TODO: We need to improve, because we only want $versions variants!
                // If only one version is requested, we can improve the query to only return this version!
                val better_where = if (version != null && versions == 1)
                    (if (where.isEmpty()) "WHERE " else "$where AND ") + "$next_version > $version"
                else
                    where
                for (history in historyTables) {
                    if (selects.isNotEmpty()) selects.append(" UNION ALL\n")
                    selects.append("\t(SELECT $select_cols_string FROM ${map.quotedId}.${history.quotedName} $better_where)\n")
                }
            }
        }
        // Restore original value for `col_num` selection.
        select_cols[0] = "col_num"

        // The columns we need until the last final result building.
        val select_cols_string = select_cols.joinToString(", ")

        // If history is queried, and only a certain amount of versions should be returned
        // we need to partition the result, so that we can select have the requested amount of versions.
        val part = if (req.queryHistory && versions > 1) """, query_with_v AS (
  SELECT
    $select_cols_string,
    ROW_NUMBER() OVER (PARTITION BY col_num, fn ORDER BY version DESC) AS v
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
    else if (part.isNotEmpty()) "ORDER BY col_num DESC, fn DESC, version DESC " // If multiple versions requested, order by version.
    else "" // No explicit ordering, no multiple versions, use random oder
  }LIMIT $REQ_LIMIT
)"""

        // The final SQL query.
        // We only need `col_num`, `fn`, and `version`; the additional columns in limit were only for sorting.
        // If we only select from a single collection (thePgCollection != null), `col_num` is implicit.
        val SQL = """WITH query AS (
$selects)$part$limited
SELECT ${if (thePgCollection == null) "col_num, fn, version" else "fn, version"} FROM limited"""
        return PgQuery(
            sql = SQL,
            argValues = whereClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = whereClause?.argTypeNames ?: emptyArray(),
            pgStorage.number,
            pgCatalog.catalogNumber,
            thePgCollection?.collectionNumber
        )
    }
}