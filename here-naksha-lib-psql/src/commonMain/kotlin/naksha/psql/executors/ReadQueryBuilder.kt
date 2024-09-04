package naksha.psql.read

import naksha.model.FetchMode.*
import naksha.model.Flags
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB
import naksha.model.geoEncoding
import naksha.model.request.*
import naksha.model.request.query.*
import naksha.psql.*
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

internal class ReadQueryBuilder {

    data class SqlReadQuery(val rawSql: String, val params: MutableList<Any?>?)

    /**
     * Builds SQL request based on given ReadRequest.
     *
     * @param req - read request
     * @return <SQL query string, list of params for query>
     */
    fun build(req: Request): SqlReadQuery {
        return when (req) {
            is ReadCollections -> buildReadFeatures(req.toReadFeatures())
            is ReadFeatures -> buildReadFeatures(req)
            else -> throw NotImplementedError()
        }
    }

    private fun buildReadFeatures(req: ReadFeatures): SqlReadQuery {
        val queryBuilder = StringBuilder()
        val columnsToFetch = resolveColumns(req)

        val allQueriesParams = mutableListOf<Any?>()
        val quotedTablesToQuery = req.getQuotedTablesToQuery()

        queryBuilder.append("SELECT $tuple_number FROM (")
        for (table in quotedTablesToQuery.withIndex()) {

            queryBuilder.append("\n(")
            queryBuilder.append("SELECT $columnsToFetch")
            queryBuilder.append(" FROM ${table.value}")
            whereClause(req, allQueriesParams)?.let { rawWhere ->
                queryBuilder.append(rawWhere)
            }
            queryBuilder.append(")")
            if (table.index < quotedTablesToQuery.size - 1) {
                queryBuilder.append("\nUNION ALL")
            }
        }
        if (req.limit != null) {
            queryBuilder.append("\n) LIMIT ${req.limit}")
        }

        return SqlReadQuery(queryBuilder.toString(), allQueriesParams)
    }

    /**
     * Returns SQL columns asked by user.
     *
     * @param req
     * @return comma delimited column names
     */
    private fun resolveColumns(req: ReadRequest): String {
        return when (req.fetchMode) {
            FETCH_ID -> "$PgColumn.id"
            FETCH_ALL -> PgColumn.allColumns.joinToString(", ")
            FETCH_META -> PgColumn.metaColumn.joinToString(", ")
            else -> TODO()
        }
    }

    private fun whereClause(req: ReadFeatures, allQueriesParams: MutableList<Any?>): String? {
        if (req.query.isEmpty()) return null
        val whereClauseBuilder = WhereClauseBuilder(allQueriesParams)
        req.query.spatial
        req.query["spatial"]?.let { spatialQuery -> // TODO: why .spatial isn't working?
            when (spatialQuery) {
                is SpIntersects -> intersectsClause(spatialQuery, whereClauseBuilder)
                else -> throw UnsupportedOperationException("We don't support ${spatialQuery::class} yet")
            }
        }
        req.query.tags?.let {
            throw UnsupportedOperationException("We don't support tags query yet")
        }
        req.query.properties?.let {
            throw UnsupportedOperationException("We don't support properties query yet")
        }
        return whereClauseBuilder.buildRawWhere()
    }

    private fun intersectsClause(intersects: SpIntersects, builder: WhereClauseBuilder) {
        val geoBytes = PgUtil.encodeGeometry(intersects.geometry, Flags().geoEncoding(TWKB))
            ?: throw IllegalArgumentException("Unable to encode geometry with TWKB")
        val placeholder = builder.addArgAndGetPlaceholder(geoBytes)
        // naksha geometry in second arg is not necesary - use end-function
        builder.addSubClause(
            "ST_Intersects(naksha_geometry(${PgColumn.flags.name}, ${PgColumn.geo.name}), naksha_geometry($TWKB, $placeholder)))"
        )
    }

    /**
     * Recursively add logical operations between where conditions.
     * I.e.: (( ... AND ...) OR ...)
     *
     * @param whereSql - StringBuilder to add conditions
     * @param paramsList - list of params - will be modified whenever condition is compared to value provided by request.
     */
//    private fun addLOp(whereSql: StringBuilder, paramsList: MutableList<Any?>, lop: LOp) {
//        whereSql.append("(")
//        for (el in lop.children.withIndex()) {
//            resolveOps(whereSql, paramsList, el.value)
//            if (el.index < lop.children.size - 1) {
//                whereSql.append(" ${lop.op.operator} ")
//            }
//        }
//        whereSql.append(")")
//    }

    /**
     * Adds basic conditions like =, <, >, ≤, ≥, etc. to where statement.
     * I.e.:  id = $1
     *
     * @param whereSql - StringBuilder to add conditions
     * @param paramsList - list of params - will be modified whenever condition is compared to value provided by request.
     */
    private fun addPOp(whereSql: StringBuilder, paramsList: MutableList<Any?>, pop: PQuery) {
        // TODO: Fix me !!!
        // real column names
//        val col = when (pop.property) {
//            PRef.ID -> COL_ID
//            PRef.APP_ID -> COL_APP_ID
//            PRef.AUTHOR -> COL_AUTHOR
//            PRef.UID -> COL_UID
//            PRef.GRID -> COL_GEO_GRID
//            PRef.TXN -> COL_TXN
//            PRef.TXN_NEXT -> COL_TXN_NEXT
//            PRef.TAGS -> "tags_to_jsonb($COL_TAGS)"
//            PRef.UUID -> return customUuidOp(whereSql, paramsList, pop)
//            is PRef.NON_INDEXED_PREF -> (pop.property as PRef.NON_INDEXED_PREF).path.joinToString { "->" }
//        }
//
//        whereSql.append(col)
//        if (pop.property.isJsonField)
//            whereSql.append(pop.op.jsonOperation)
//        else
//            whereSql.append(pop.op.operation)
//        if (pop.value != null) {
//            whereSql.append(paramsList.nextPlaceHolder())
//            paramsList.add(pop.value)
//        }
    }

    /**
     * Adds Spatial conditions to where section
     *
     * @param whereSql - StringBuilder to add conditions
     * @param paramsList - list of params - will be modified whenever condition is compared to value provided by request.
     * @param sop - requested spatial operations.
     */
    private fun addSop(
        whereSql: StringBuilder,
        paramsList: MutableList<Any?>,
//        sop: AbstractSpatialQuery
    ) {
        // TODO: Fix me !!!
//        val valuePlaceholder = paramsList.nextPlaceHolder()
//        val GEO_TWKB = 0
//        when (sop.op) {
//            INTERSECTS -> {
//                val wrapperForReqValuePlaceholder = geometryTransformer.wrapWithTransformation(
//                    sop.transformation,
//                    "ST_Force3D(naksha_geometry_in_type($GEO_TWKB::int2,$valuePlaceholder))"
//                )
//                whereSql.append("ST_Intersects(naksha_geometry(flags,geo), $wrapperForReqValuePlaceholder)")
//
//                // TODO FIXME, sop.geometry should be transformed to byteArray once we have Platform <-> twkb converter
//                paramsList.add(sop.geometry)
//            }
//        }
        TODO("Fix me, we changed to flags!")
    }

    /**
     * Custom implementation of UUID search. UUID is kept in database in set of columns.
     * To query UUID we have to query particular columns.
     * Only `=` operation is allowed.
     */
    private fun customUuidOp(whereSql: StringBuilder, paramsList: MutableList<Any?>, pop: PQuery) {
        // TODO: Fix me !!!
//        check(pop.value != null)
//        check(pop.op == POpType.EQ)
//
//        val guid = Guid.fromString(pop.value as String)
//        val uuidOp = LOp.and(
//            PropertyQuery.eq(PRef.TXN, guid.luid.txn.value),
//            PropertyQuery.eq(PRef.UID, guid.luid.uid)
//        )
//        resolveOps(whereSql, paramsList, uuidOp)
    }

    /**
     * Return all tables to query, if the request was for collection "foo" with history, then ["foo", "foo$hst"]
     * will be deleted
     *
     * @return table names to be queries
     */
    private fun ReadFeatures.getQuotedTablesToQuery(): List<String> {
        require(collectionIds.isNotEmpty()) {
            "ReadFeatures must define at least one collection to query"
        }
        val tables = mutableListOf<String>()
        for (collection in collectionIds.filterNotNull()) {
            tables.add(quoteIdent(collection))
            if (queryDeleted)
                tables.add(quoteIdent("$collection\$del"))
            if (queryHistory)
                tables.add(quoteIdent("$collection\$hst"))
        }
        return tables
    }

    private fun ReadCollections.toReadFeatures(): ReadFeatures {
        val rf = ReadFeatures()
        rf.collectionIds += NKC_TABLE
        rf.resultFilters = resultFilters
//        rf.queryDeleted = queryDeleted
        rf.limit = limit
        rf.featureIds = collectionIds
        return rf
    }

    private fun MutableList<*>.nextPlaceHolder() = "${"$"}${this.size + 1}"
}

private class WhereClauseBuilder(val allQueriesParams: MutableList<Any?>) {
    private val subClauses = mutableListOf<String>()

    fun addSubClause(subClause: String) {
        subClauses.add(subClause)
    }

    fun addArgAndGetPlaceholder(arg: Any): String {
        allQueriesParams.add(arg)
        return "\$${allQueriesParams.size}" // psql starts indexing from 1
    }

    fun buildRawWhere(): String? {
        if (subClauses.isEmpty()) return null
        return "WHERE ${subClauses.joinToString(separator = " AND ")}"
    }
}
