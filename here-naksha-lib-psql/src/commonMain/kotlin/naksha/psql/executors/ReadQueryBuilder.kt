package naksha.psql.read

import naksha.model.FetchMode.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaException
import naksha.model.request.*
import naksha.model.request.query.*
import naksha.psql.*
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

internal class ReadQueryBuilder(val storage: PgStorage) {

    data class ReadQuery(val sqlQuery: String, val paramTypes: MutableList<String>, val params: MutableList<Any?>)

    /**
     * Builds SQL request based on given ReadRequest.
     *
     * @param req - read request
     * @return <SQL query string, list of params for query>
     */
    fun build(req: Request): ReadQuery {
        return when (req) {
            is ReadCollections -> buildReadFeatures(req.toReadFeatures())
            is ReadFeatures -> buildReadFeatures(req)
            else -> throw NotImplementedError()
        }
    }

    private fun buildReadFeatures(req: ReadFeatures): ReadQuery {
        val queryBuilder = StringBuilder()

        val quotedTablesToQuery = req.getQuotedTablesToQuery()
        val paramTypes = mutableListOf<String>()
        val paramValues = mutableListOf<Any?>()

        val LAST = quotedTablesToQuery.size - 1
        val WHERE = resolveFeaturesWhere(req, paramTypes, paramValues)
        val REQ_LIMIT = if (req.limit != null && req.orderBy == null && req.returnHandle != true) req.limit else null
        queryBuilder.append("SELECT gzip(bytea_agg($tuple_number)) AS rs FROM (SELECT $tuple_number FROM (\n")
        for (table in quotedTablesToQuery.withIndex()) {
            queryBuilder.append("\t(SELECT $tuple_number, $id FROM ${table.value}")
            if (WHERE.isNotEmpty()) queryBuilder.append(" WHERE $WHERE")
            if (REQ_LIMIT != null) queryBuilder.append(" LIMIT $REQ_LIMIT")
            queryBuilder.append(")")
            if (table.index < LAST) queryBuilder.append(" UNION ALL\n")
        }
        queryBuilder.append("\n) ORDER BY $id, $tuple_number")
        val HARD_LIMIT = req.limit ?: storage.hardCap
        queryBuilder.append(if (HARD_LIMIT > 0 ) ") LIMIT $HARD_LIMIT;" else ")")
        val SQL = queryBuilder.toString()
        return ReadQuery(SQL, paramTypes, paramValues)
    }

    private fun resolveFeaturesWhere(req: ReadFeatures, paramTypes: MutableList<String>, paramValues: MutableList<Any?>): String {
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
     * Return all tables to query, if the request was for collection "foo" with history, then ["foo", "foo$hst"].
     *
     * @return table names to be queries
     */
    private fun ReadFeatures.getQuotedTablesToQuery(): List<String> {
        val tables = mutableListOf<String>()
        for (index in collectionIds.indices) {
            val collectionId = collectionIds[index] ?: throw NakshaException(ILLEGAL_ARGUMENT, "Collection must not be null at index $index")
            // TODO: Fix me, we need to verify if such a collection exists and then use the in-memory representation!
            tables.add(quoteIdent(collectionId))
            if (queryDeleted) tables.add(quoteIdent("$collectionId\$del"))
            if (queryHistory) tables.add(quoteIdent("$collectionId\$hst"))
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
