package naksha.plv8.read

import naksha.model.request.*
import naksha.model.request.condition.*
import naksha.plv8.*
import naksha.plv8.COL_ID
import naksha.plv8.COL_TYPE
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
internal class ReadQueryBuilder(val sql: IPlv8Sql) {

    fun build(req: ReadRequest): Pair<String, MutableList<Any?>> {
        return when (req) {
            is ReadCollections -> TODO()
            is ReadFeatures -> buildReadFeatures(req)

            else -> throw NotImplementedError()
        }
    }

    private fun buildReadFeatures(req: ReadFeatures): Pair<String, MutableList<Any?>> {
        val queryBuilder = StringBuilder()
        val columnsToFetch = resolveColumns(req)

        val allQueriesParams = mutableListOf<Any?>()
        val quotedTablesToQuery = req.getQuotedTablesToQuery()

        queryBuilder.append("SELECT * FROM (")
        for (table in quotedTablesToQuery.withIndex()) {
            val (whereSql, params) = resolveFeaturesWhere(req)
            allQueriesParams.addAll(params)

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

        return Pair(queryBuilder.toString(), allQueriesParams)
    }

    /**
     * Returns SQL columns asked by user.
     *
     * @param req
     * @return comma delimited column names
     */
    private fun resolveColumns(req: ReadRequest): String {
        var columns = "$COL_ID, $COL_TYPE, $COL_GEO_REF, $COL_FLAGS"
        if (!req.noMeta) {
            columns += ", $COL_TXN_NEXT, $COL_TXN, $COL_UID, $COL_PTXN, $COL_PUID, $COL_ACTION, $COL_VERSION, $COL_CREATED_AT, $COL_UPDATE_AT, $COL_AUTHOR_TS, $COL_AUTHOR, $COL_APP_ID, $COL_GEO_GRID"
        }
        if (!req.noTags) {
            columns += ", $COL_TAGS"
        }
        if (!req.noGeometry) {
            columns += ", $COL_GEOMETRY"
        }
        if (!req.noFeature) {
            columns += ", $COL_FEATURE"
        }
        return columns
    }

    private fun resolveFeaturesWhere(req: ReadFeatures): Pair<String, MutableList<Any?>> {
        val whereSql = StringBuilder()
        val paramsList = mutableListOf<Any?>()

        resolveOps(whereSql, paramsList, req.op)

        return Pair(whereSql.toString(), paramsList)
    }

    /**
     * Prepares WHERE query using requested Ops.
     *
     * @param whereSql - sql to add new conditions to
     * @param paramsList - list of params to add conditions values to
     */
    private fun resolveOps(whereSql: StringBuilder, paramsList: MutableList<Any?>, op: Op?) {
        if (op == null) return

        when (op) {
            is LOp -> resolveLOp(whereSql, paramsList, op)
            is POp -> resolvePOp(whereSql, paramsList, op)
            is SOp -> TODO()
        }
    }

    /**
     * Recursively add logical operations between where conditions.
     * I.e.: (( ... AND ...) OR ...)
     *
     * @param whereSql - StringBuilder to add conditions
     * @param paramsList - list of params - will be modified whenever condition is compared to value provided by request.
     */
    private fun resolveLOp(whereSql: StringBuilder, paramsList: MutableList<Any?>, lop: LOp) {
        whereSql.append("(")
        for (el in lop.children.withIndex()) {
            resolveOps(whereSql, paramsList, el.value)
            if (el.index < lop.children.size - 1) {
                whereSql.append(" ${lop.op.operator} ")
            }
        }
        whereSql.append(")")
    }

    /**
     * Adds basic conditions like =, <, >, ≤, ≥, etc. to where statement.
     * I.e.:  id = $1
     *
     * @param whereSql - StringBuilder to add conditions
     * @param paramsList - list of params - will be modified whenever condition is compared to value provided by request.
     */
    private fun resolvePOp(whereSql: StringBuilder, paramsList: MutableList<Any?>, pop: POp) {
        // real column names
        val col = when (pop.propertyRef) {
            PRef.ID -> COL_ID
            PRef.APP_ID -> COL_APP_ID
            PRef.AUTHOR -> COL_AUTHOR
            PRef.UID -> COL_UID
            PRef.GRID -> COL_GEO_GRID
            PRef.TXN -> COL_TXN
            PRef.TXN_NEXT -> COL_TXN_NEXT
            PRef.TAGS -> COL_TAGS
        }

        whereSql.append(col)
        whereSql.append(pop.op.operation)
        if (pop.value != null) {
            val idx = paramsList.size + 1
            whereSql.append("${'$'}$idx")
            paramsList.add(pop.value)
        }
    }

    /**
     * Return all tables to query, if the request was for collection "foo" with history, then ["foo", "foo$hst"]
     * will be deleted
     *
     * @return table names to be queries
     */
    private fun ReadFeatures.getQuotedTablesToQuery(): List<String> {
        val tables = mutableListOf<String>()
        for (collection in collectionIds) {
            tables.add(sql.quoteIdent(collection))
            if (queryDeleted)
                tables.add(sql.quoteIdent("$collection\$del"))
            if (queryHistory)
                tables.add(sql.quoteIdent("$collection\$hst"))
        }
        return tables
    }
}