package naksha.psql.executors.query

import naksha.model.*
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.UNSUPPORTED_OPERATION
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadRequest
import naksha.model.request.query.*
import naksha.psql.*
import naksha.psql.PgColumn.PgColumnCompanion.id
import naksha.psql.PgColumn.PgColumnCompanion.tuple_number
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

/**
 * Create a new SQL query of a read request (see [build])
 * @property session the session for which the query is created.
 * @property request the read request for which to generate the SQL query.
 */
class PgQueryBuilder(val session: PgSession, val request: ReadRequest) {

    // TODO: Add support for reading multiple versions using (req.versions = 2+)!
    // TODO: Add support for orderBy!

    fun build(): PgQuery {
        return when (request) {
            is ReadCollections -> readCollections(request)
            is ReadFeatures -> readFeatures(request)
            else -> throw NakshaException(
                UNSUPPORTED_OPERATION,
                "The given read-request is unknown"
            )
        }
    }

    private fun readCollections(req: ReadCollections): PgQuery {
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

    private fun readFeatures(req: ReadFeatures): PgQuery {
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
        val whereClause = WhereClauseBuilder(req).build()
        for (table in quotedTablesToQuery.withIndex()) {
            queryBuilder.append("\t(SELECT $tuple_number, $id FROM ${table.value}")
            if (whereClause != null) queryBuilder.append(whereClause.sql)
            if (REQ_LIMIT != null) queryBuilder.append(" LIMIT $REQ_LIMIT")
            queryBuilder.append(")")
            if (table.index < quotedTablesToQuery.lastIndex) queryBuilder.append(" UNION ALL\n")
        }
        queryBuilder.append("\n) ORDER BY $id, $tuple_number")
        val HARD_LIMIT = req.limit ?: session.storage.hardCap
        queryBuilder.append(if (HARD_LIMIT > 0) ") LIMIT $HARD_LIMIT;" else ")")
        return PgQuery(
            sql = queryBuilder.toString(),
            argValues = whereClause?.argValues?.toTypedArray() ?: emptyArray(),
            argTypes = whereClause?.argTypes?.toTypedArray() ?: emptyArray()
        )
    }
}
