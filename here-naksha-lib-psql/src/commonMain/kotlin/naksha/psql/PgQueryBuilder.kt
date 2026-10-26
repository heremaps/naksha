package naksha.psql

import naksha.base.Id
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Version.VersionCompanion.HEAD
import naksha.base.collectionNotFound
import naksha.base.illegalArg
import naksha.base.catalogNotFound
import naksha.base.internalError
import naksha.base.unsupportedOp
import naksha.model.Naksha.NakshaCompanion.HARD_TUPLE_LIMIT
import naksha.model.objects.StandardMembers.StandardMembers_C.FN
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.NEXT_VERSION
import naksha.model.objects.StandardMembers.StandardMembers_C.NextVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.VERSION
import naksha.model.request.*
import naksha.model.request.query.SortOrder
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
        // Collect needed data.
        val pgStorage = session.storage
        val REQ_LIMIT = min(max(0, req.limit ?: Int.MAX_VALUE), session.storage.hardCap)
        if (REQ_LIMIT == 0) throw illegalArg("Invalid limit given: ${req.limit}, must be between 1 and $HARD_TUPLE_LIMIT")
        val catalogId = Id(req.catalogId ?: throw illegalArg("Request has not 'catalogId'"))
        val collectionId = Id(req.collectionId ?: throw illegalArg("Request has no 'collectionId'"))
        val (pgCatalog, pgCollection) = session.useConnNonNull(readOnly = true) {
            Pair(
                session.getPgCatalog(catalogId.intValue)
                    ?: throw catalogNotFound("Catalog with id '$catalogId' does not exist"),
                session.getPgCollection(catalogId.intValue, collectionId.intValue)
                    ?: throw collectionNotFound("Collection with id '$collectionId' not found in catalog '$catalogId'")
            )
        }
        val customClause: PgQueryWhereClause? = PgQueryWhereBuilder(req, pgCollection).build()
        val customWHERE = customClause?.where ?: ""

        // Create ordering instructions.
        val baseCols = listOf(FN, VERSION, NEXT_VERSION)
        val customCols = mutableListOf<String>()
        var order_by: MutableList<Pair<PgColumn, PgOrder>>? = null
        do { // scope
            var orderBy = req.orderBy
            if (!orderBy.isNullOrEmpty()) {
                order_by = mutableListOf()
                if (!orderBy.isDeterministic()) {
                    var i = 0
                    while (orderBy != null) {
                        val memberName = orderBy.member ?: throw illegalArg("Missing 'member' property in orderBy")
                        val sortOrder = if (orderBy.sortOrder == SortOrder.ASCENDING) PgOrder.ASC else PgOrder.DESC
                        // Column must exist.
                        val pgColumn = pgCollection.column(memberName) ?: throw illegalArg("Missing member '$memberName' in collection")

                        // Add order and columns to select list, we can select because its part of the index, so we still get the index-only order.
                        order_by.add(Pair(pgColumn, sortOrder))
                        if (!baseCols.contains(memberName) && !customCols.contains(memberName)) {
                            customCols.add(memberName)
                        }
                        orderBy = orderBy.next
                        if (++i > 10) throw illegalArg("Too many orders in orderBy")
                    }
                } else {
                    // For simple deterministic ordering we use primary-key: next-version DESC, feature-number DESC, version DESC
                    val pgNv = pgCollection.column(NextVersion) ?: throw internalError("Missing next-version column in collection")
                    order_by.add(Pair(pgNv, PgOrder.DESC))
                    val pgFn = pgCollection.column(FeatureNumber) ?: throw internalError("Missing feature-number column in collection")
                    order_by.add(Pair(pgFn, PgOrder.DESC))
                    val pgVersion = pgCollection.column(FeatureVersion) ?: throw internalError("Missing feature-version column in collection")
                    order_by.add(Pair(pgVersion, PgOrder.DESC))
                }
            }
        } while (false)
        val selectCols = baseCols + customCols
        // `order_by` may be empty, if no custom ordering was requested.
        // .first = member-name
        // .second = sort-order (ASC | DESC)
        var order_by_string = order_by?.joinToString { "${it.first} ${it.second}" } ?: ""

        // Create the basic WHERE query, to be extended by the custom WHERE later.
        val WHERE = StringBuilder()

        // Specify version range.
        val minVersion: Long = min(HEAD.number, max(0L, (req.minVersion ?: 0L) and 3L.inv()))
        if (minVersion > 0L) WHERE.append("$VERSION >= ").append(minVersion).append(" ")
        val version: Long = min(HEAD.number, max(3L, (req.version ?: HEAD.number) or 3L))
        // `version` should be in range 3 to HEAD now.
        if (version < HEAD.number) {
            if (WHERE.isNotEmpty()) WHERE.append("AND ")
            WHERE.append("$VERSION <= ").append(version).append(" ")
        }

        // If minVersion=0 and version=HEAD, WHERE will yet be empty —> we do not limit version!
        val q_ranked: String
        val q_deleted: String
        val q_versions: String
        if (!req.queryHistory) {
            // If no HISTORY should be queried, limit access to HEAD table.
            if (WHERE.isNotEmpty()) WHERE.append("AND ")
            WHERE.append("$NEXT_VERSION = ").append(HEAD).append(" ") // nv=HEAD

            // Ignore `req.versions`, because there will only be one version in HEAD!
            if (req.versions > 1) {
                logger.warn(
                    "Ignore 'versions={}', while at the same time 'queryHistory=false', HEAD only contains 1 version!",
                    req.versions
                )
            }

            // If no deleted rows are wanted, filter them out.
            if (!req.queryDeleted) WHERE.append("AND (($VERSION & 3) < 2) ")

            // No window function needed.
            q_ranked = ""
            q_deleted = ""
            q_versions = ""
        } else if (req.versions == 1) {
            // We should query HISTORY, but only need the latest single state close to version.
            if (WHERE.isNotEmpty()) WHERE.append("AND ")

            // If the user wants to limit values to a specific version.
            if (version < HEAD.number) {
                // We know that above we have already added `WHERE version <= $version`.
                // When combined with this, it will only return the closest version.
                WHERE.append("$NEXT_VERSION > ").append(version).append(" ")
            } else
            // otherwise the user wants HEAD, then actually this trims the request to HEAD table.
            // Technically, this query misses purged rows, but that is intentional. So, if
            // HEAD does not contain a tombstone, this request will not return the row.
            {
                // We know, that there are no other version checks
                WHERE.append("$NEXT_VERSION = ").append(HEAD).append(" ") // nv=HEAD
            }

            // If no deleted rows are wanted, filter them out.
            if (!req.queryDeleted) WHERE.append("AND (($VERSION & 3) < 2) ")

            // No window function needed.
            q_ranked = ""
            q_deleted = ""
            q_versions = ""
        } else { // req.versions > 1 && query-history
            // We want multiple versions from history in the given range.
            // To be fast enough and to be able to filter out deleted, we need to use a window function.
            q_ranked = ",q_ranked AS (SELECT ${selectCols.joinToString()}, ROW_NUMBER() OVER (PARTITION BY $FN ORDER BY $VERSION DESC) AS row FROM query)\n"
            if (req.queryDeleted) {
                // The user is okay with tombstones, so we do not unselect deleted.
                q_deleted = ""
                q_versions = ",q_versions AS (SELECT ${selectCols.joinToString()} FROM q_ranked WHERE row <= ${req.versions})\n"
            } else {
                // The user does not want those tuples where the latest state is DELETED.
                // We assume there are not that many deleted features, so we select those that are deleted and should not be part of the result.
                // Other option would be to select all that are NOT deleted, but very likely this list would be much larger.
                // Eventually we select all from ranked without those where the latest state is deleted.
                q_deleted = ",q_deleted AS (SELECT $FN FROM q_ranked WHERE row = 1 AND (version & 3) = 2)\n"
                q_versions = ",q_versions AS (SELECT ${selectCols.joinToString { "q_ranked.$it" }} FROM q_ranked, q_deleted WHERE q_ranked.row <= ${req.versions} AND q_ranked.$FN NOT IN q_deleted)\n"
            }
            // At this point we have two or three CTEs defined, with the last one always being `q_versions`.
            // `q_versions` contains all tuple, not ordered.
            // If there is no custom ordering, we should order by fn, version:
            if (order_by_string.isEmpty()) order_by_string = "$FN DESC, $VERSION DESC"
        }

        // Create the WHERE statement.
        if (customWHERE.isNotEmpty()) {
            if (WHERE.isEmpty()) {
                WHERE.append(customWHERE)
            } else {
                WHERE.append("AND ").append(customWHERE)
            }
        }

        // The final SQL query.
        // We only need `fn` and `version`; the additional columns are only used for sorting.
        val SQL = """WITH 
query AS (SELECT $selectCols FROM ${pgCatalog.id.catalogName()}.${pgCollection.id.collectionName()}${if (WHERE.isEmpty()) "" else " WHERE $WHERE"})
$q_ranked$q_deleted$q_versions
SELECT $FN, $VERSION FROM ${if (q_versions.isNotEmpty()) "q_versions" else "query"}
${if (order_by_string.isNotEmpty()) " ORDER BY $order_by_string" else ""})
${if (REQ_LIMIT < Int.MAX_VALUE) " LIMIT $REQ_LIMIT" else ""}"""
        return PgQuery(
            sql = SQL,
            argValues = customClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = customClause?.argTypeNames ?: emptyArray(),
            pgStorage,
            pgCatalog,
            pgCollection
        )
    }
}
