package naksha.psql.executors

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadRequest
import naksha.model.request.query.*
import naksha.psql.*
import naksha.psql.PgColumn.PgColumnCompanion.flags
import naksha.psql.PgColumn.PgColumnCompanion.geo
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.PgColumn.PgColumnCompanion.txn
import naksha.psql.PgColumn.PgColumnCompanion.uid
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

/**
 * Create a new SQL query of a read request.
 * @constructor Creates a new WHERE query.
 * @property session the session for which the query is created.
 * @property request the read request for which to generate the SQL query.
 */
internal class PgQuery(val session: PgSession, val request: ReadRequest) {

    // TODO: Add support for reading multiple versions using (req.versions = 2+)!
    // TODO: Add support for orderBy!

    /**
     * The SQL query to be prepared.
     */
    val sql: String

    /**
     * The parameter types for the [naksha.psql.PgConnection.prepare].
     */
    val paramTypes: Array<String>

    /**
     * The parameter values for the [naksha.psql.PgPlan.execute].
     */
    val paramValues: Array<Any?>

    private val types = mutableListOf<String>()
    private val values = mutableListOf<Any?>()

    init {
        sql = when (request) {
            is ReadCollections -> readCollections(request)
            is ReadFeatures -> readFeatures(request)
            else -> throw NakshaException(
                UNSUPPORTED_OPERATION,
                "The given read-request is unknown"
            )
        }
        paramTypes = types.toTypedArray()
        paramValues = values.toTypedArray()
    }

    private fun readCollections(req: ReadCollections): String {
        val rf = ReadFeatures()
        rf.collectionIds += NKC_TABLE
        rf.resultFilters = req.resultFilters
        rf.limit = req.limit
        rf.featureIds = req.collectionIds
        return readFeatures(rf)
    }

    /**
     * Return all tables to query, if the request was for collection "foo" with history, then ["foo", "foo$hst"].
     *
     * @return table names to be queries
     */
    private fun ReadFeatures.getQuotedTablesToQuery(): List<String> {
        val tables = mutableListOf<String>()
        for (index in collectionIds.indices) {
            val collectionId = collectionIds[index] ?: throw NakshaException(
                ILLEGAL_ARGUMENT,
                "Collection must not be null at index $index"
            )
            // TODO: Fix me, we need to verify if such a collection exists and then use the in-memory representation!
            tables.add(quoteIdent(collectionId))
            if (queryDeleted) tables.add(quoteIdent("$collectionId\$del"))
            if (queryHistory) tables.add(quoteIdent("$collectionId\$hst"))
        }
        return tables
    }

    private fun readFeatures(req: ReadFeatures): String {
        if (req.versions < 1) {
            throw NakshaException(
                ILLEGAL_ARGUMENT,
                "It is not possible to request less than one version of each feature"
            )
        }
        if (req.versions > 1 && !req.queryHistory) {
            throw NakshaException(
                ILLEGAL_ARGUMENT,
                "When multiple versions of features are requested, queryHistory is mandatory"
            )
        }
        val queryBuilder = StringBuilder()
        val REQ_LIMIT =
            if (req.limit != null && req.orderBy == null && req.returnHandle != true) req.limit else null
        queryBuilder.append("SELECT gzip(bytea_agg($tuple_number)) AS rs FROM (SELECT $tuple_number FROM (\n")
        val quotedTablesToQuery = req.getQuotedTablesToQuery()
        val where = whereCreate(req)
        for (table in quotedTablesToQuery.withIndex()) {
            queryBuilder.append("\t(SELECT $tuple_number, $id FROM ${table.value}")
            if (where.isNotEmpty()) queryBuilder.append(" WHERE $where")
            if (REQ_LIMIT != null) queryBuilder.append(" LIMIT $REQ_LIMIT")
            queryBuilder.append(")")
            if (table.index < quotedTablesToQuery.lastIndex) queryBuilder.append(" UNION ALL\n")
        }
        queryBuilder.append("\n) ORDER BY $id, $tuple_number")
        val HARD_LIMIT = req.limit ?: session.storage.hardCap
        queryBuilder.append(if (HARD_LIMIT > 0) ") LIMIT $HARD_LIMIT;" else ")")
        return queryBuilder.toString()
    }

    private fun whereCreate(req: ReadFeatures): String {
        val where = StringBuilder()
        whereFeatureId(req, where)
        whereGuids(req, where)
        whereVersion(req, where)
        req.query.metadata?.let {
            whereMetadata(req, it, where)
        }
        val query = req.query
        val spatial = query.spatial
        if (spatial != null) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("(")
            whereQuerySpatial(req, spatial, where)
            where.append(")")
        }
        return where.toString()
    }

    private fun whereMetadata(req: ReadFeatures, metaQuery: IMetaQuery, where: StringBuilder) {
        when (metaQuery) {
            is MetaNot -> {
                where.append(" NOT ")
                whereMetadata(req, metaQuery.query, where)
            }

            is MetaAnd -> {
                metaQuery.filterNotNull().forEachIndexed { index, subQuery ->
                    if (index > 0) {
                        where.append(" AND ")
                    }
                    whereMetadata(req, subQuery, where)
                }
            }

            is MetaOr -> {
                metaQuery.filterNotNull().forEachIndexed { index, subQuery ->
                    if (index > 0) {
                        where.append(" OR ")
                    }
                    whereMetadata(req, subQuery, where)
                }
            }

            is MetaQuery -> {
                val pgColumn = PgColumn.ofRowColumn(metaQuery.column) ?: throw NakshaException(
                    ILLEGAL_STATE,
                    "Couldn't find PgColumn for TupleColumn: ${metaQuery.column.name}"
                )
                val placeholder = placeholderFor(metaQuery.value, pgColumn.type)
                val resolvedQuery = when (val op = metaQuery.op) {
                    is StringOp -> resolveStringOp(op, pgColumn, placeholder)
                    is DoubleOp -> resolveDoubleOp(op, pgColumn, placeholder)
                    else -> throw NakshaException(
                        ILLEGAL_ARGUMENT,
                        "Unknown op type: ${op::class.simpleName}"
                    )
                }
                where.append(resolvedQuery)
            }

            else -> throw NakshaException(
                ILLEGAL_ARGUMENT,
                "Unknown metadata query type: ${metaQuery::class.simpleName}"
            )
        }
    }

    private fun resolveStringOp(
        stringOp: StringOp,
        column: PgColumn,
        valuePlaceholder: String
    ): String {
        return when (stringOp) {
            StringOp.EQUALS -> "${column.name} = $valuePlaceholder"
            StringOp.STARTS_WITH -> "starts_with(${column.name}, $valuePlaceholder)"
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown StringOp: $stringOp")
        }
    }

    private fun resolveDoubleOp(
        doubleOp: DoubleOp,
        column: PgColumn,
        valuePlaceholder: String
    ): String {
        return when (doubleOp) {
            DoubleOp.EQ -> "${column.name} = $valuePlaceholder"
            DoubleOp.GT -> "${column.name} > $valuePlaceholder"
            DoubleOp.GTE -> "${column.name} >= $valuePlaceholder"
            DoubleOp.LT -> "${column.name} < $valuePlaceholder"
            DoubleOp.LTE -> "${column.name} <= $valuePlaceholder"
            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Unknown DoubleOp: $doubleOp")
        }
    }

    private fun whereFeatureId(req: ReadFeatures, where: StringBuilder) {
        if (req.featureIds.size > 0) {
            val featureIds = req.featureIds.filterNotNull()
            if (featureIds.isNotEmpty()) {
                if (where.isNotEmpty()) where.append(" AND ")
                val placeholder = placeholderFor(featureIds.toTypedArray(), PgType.STRING_ARRAY)
                where.append("$id = ANY($placeholder)")
            }
        }
    }

    private fun whereGuids(req: ReadFeatures, where: StringBuilder) {
        if (req.guids.isNotEmpty()) {
            val guids = req.guids.filterNotNull()
            if (guids.isNotEmpty()) {
                for (i in guids.indices) {
                    val guid = guids[i]
                    if (where.isNotEmpty()) where.append(" AND ")
                    val idPlaceholder = placeholderFor(guid.featureId, PgType.STRING)
                    val txnPlaceholder = placeholderFor(guid.version.txn, PgType.INT64)
                    val uidPlaceholder = placeholderFor(guid.uid, PgType.INT)
                    where.append("($id = $idPlaceholder AND $txn = $txnPlaceholder AND $uid = $uidPlaceholder)")
                }
            }
        }
    }

    private fun whereVersion(req: ReadFeatures, where: StringBuilder) {
        // TODO: req.version and req.minVersion
    }

    private tailrec fun whereQuerySpatial(
        req: ReadFeatures,
        spatial: ISpatialQuery,
        where: StringBuilder
    ) {
        when (spatial) {
            is SpNot -> {
                where.append("NOT ")
                whereQuerySpatial(req, spatial.query, where)
            }

            is SpAnd -> {
                for (i in spatial.indices) {
                    val subSpatial = spatial[i]
                    if (subSpatial != null) {
                        if (i > 0) where.append("AND ")
                        whereQuerySpatial(req, subSpatial, where)
                    }
                }
            }

            is SpOr -> {
                for (i in spatial.indices) {
                    val subSpatial = spatial[i]
                    if (subSpatial != null) {
                        if (i > 0) where.append("OR ")
                        whereQuerySpatial(req, subSpatial, where)
                    }
                }
            }

            is SpIntersects -> {
                // TODO: Add transformations!
                val twkb = PgUtil.encodeGeometry(
                    spatial.geometry,
                    Flags().geoGzipOff().geoEncoding(GeoEncoding.TWKB)
                )
                val placeholder = placeholderFor(twkb, PgType.BYTE_ARRAY)
                where.append("ST_Intersects(naksha_geometry($geo, $flags), ST_GeomFromTWKB($placeholder))")
            }

            else -> throw NakshaException(ILLEGAL_ARGUMENT, "Invalid spatial query found: $spatial")
        }
    }

    private fun placeholderFor(value: Any?, type: PgType): String {
        values.add(value)
        types.add(type.toString())
        return "\$${types.size}"
    }
}
