package naksha.psql.read

import naksha.model.FetchMode.*
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
            val whereSql = resolveFeaturesWhere(req, allQueriesParams)

            queryBuilder.append("\n(")
            queryBuilder.append("SELECT $columnsToFetch")
            queryBuilder.append(" FROM ${table.value}")
            if (whereSql.isNotEmpty()) {
                queryBuilder.append(" WHERE $whereSql")
            }
            queryBuilder.append(")")
            if (table.index < quotedTablesToQuery.size - 1) {
                queryBuilder.append("\nUNION ALL")
            }
        }
        queryBuilder.append("\n) LIMIT ${req.limit}")

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

    private fun resolveFeaturesWhere(
        req: ReadFeatures,
        allQueriesParams: MutableList<Any?>
    ): String {
        val whereSql = StringBuilder()
        // TODO: Fix me !!!
        //resolveOps(whereSql, allQueriesParams, req.op)

        return whereSql.toString()
    }

    /**
     * Prepares WHERE query using requested Ops.
     *
     * @param whereSql - sql to add new conditions to
     * @param paramsList - list of params to add conditions values to
     */
    private fun resolveOps(whereSql: StringBuilder, paramsList: MutableList<Any?>, op: AnyOp?) {
        if (op == null) return

        // TODO: Fix me !!!
//        when (op) {
//            is LOp -> addLOp(whereSql, paramsList, op)
//            is PropertyQuery -> addPOp(whereSql, paramsList, op)
//            is SpatialOuery -> addSop(whereSql, paramsList, op)
//        }
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