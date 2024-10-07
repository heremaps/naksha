package naksha.psql.executors.query

import naksha.model.*
import naksha.model.request.ReadFeatures
import naksha.model.request.query.*
import naksha.psql.PgColumn
import naksha.psql.PgType
import naksha.psql.PgUtil

class WhereClauseBuilder(private val request: ReadFeatures) {

    private val argValues: MutableList<Any?> = mutableListOf()
    private val argTypes: MutableList<String> = mutableListOf()
    private val where = StringBuilder()

    data class WhereClause(val sql: String, val argValues: List<Any?>, val argTypes: List<String>)

    fun build(): WhereClause? {
        whereFeatureId()
        whereGuids()
        whereVersion()
        whereMetadata()
        whereSpatial()
        return if (where.isBlank()) {
            null
        } else {
            WhereClause(sql = " WHERE $where ", argValues = argValues, argTypes = argTypes)
        }
    }

    private fun whereFeatureId() {
        val featureIds = request.featureIds.filterNotNull()
        if (featureIds.isNotEmpty()) {
            if (where.isNotEmpty()) {
                where.append(" AND ")
            }
            val placeholder = placeholderForArg(featureIds.toTypedArray(), PgType.STRING_ARRAY)
            where.append("${PgColumn.id} = ANY($placeholder)")
        }
    }

    private fun whereGuids() {
        val guids = request.guids.filterNotNull()
        if (guids.isNotEmpty()) {
            if (where.isNotEmpty()) {
                where.append(" AND ")
            }
            guids.forEachIndexed { index, guid ->
                if (index > 0) {
                    where.append(" AND ")
                }
                val idPlaceholder = placeholderForArg(guid.featureId, PgType.STRING)
                val txnPlaceholder = placeholderForArg(guid.version.txn, PgType.INT64)
                val uidPlaceholder = placeholderForArg(guid.uid, PgType.INT)
                where.append("(${PgColumn.id} = $idPlaceholder AND ${PgColumn.txn} = $txnPlaceholder AND ${PgColumn.uid} = $uidPlaceholder)")
            }
        }
    }

    private fun whereVersion() {
        // TODO: request.version and request.minVersion
    }

    private fun whereSpatial() {
        request.query.spatial?.let { spatialQuery ->
            if (where.isNotEmpty()) {
                where.append(" AND ")
            }
            where.append("(")
            whereNestedSpatial(spatialQuery)
            where.append(")")
        }
    }

    private tailrec fun whereNestedSpatial(spatial: ISpatialQuery) {
        when (spatial) {
            is SpNot -> {
                where.append("NOT ")
                whereNestedSpatial(spatial.query)
            }

            is SpAnd -> {
                for (i in spatial.indices) {
                    val subSpatial = spatial[i]
                    if (subSpatial != null) {
                        if (i > 0) where.append("AND ")
                        whereNestedSpatial(subSpatial)
                    }
                }
            }

            is SpOr -> {
                for (i in spatial.indices) {
                    val subSpatial = spatial[i]
                    if (subSpatial != null) {
                        if (i > 0) where.append("OR ")
                        whereNestedSpatial(subSpatial)
                    }
                }
            }

            is SpIntersects -> {
                // TODO: Add transformations!
                val twkb = PgUtil.encodeGeometry(
                    spatial.geometry,
                    Flags().geoGzipOff().geoEncoding(GeoEncoding.TWKB)
                )
                val placeholder = placeholderForArg(twkb, PgType.BYTE_ARRAY)
                where.append("ST_Intersects(naksha_geometry(${PgColumn.geo}, ${PgColumn.flags}), ST_GeomFromTWKB($placeholder))")
            }

            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Invalid spatial query found: $spatial"
            )
        }
    }

    private fun whereMetadata() {
        request.query.metadata?.let { metaQuery ->
            if (where.isNotEmpty()) {
                where.append(" AND ")
            }
            where.append("(")
            whereNestedMetadata(metaQuery)
            where.append(")")
        }
    }

    private tailrec fun whereNestedMetadata(metaQuery: IMetaQuery) {
        when (metaQuery) {
            is MetaNot -> {
                where.append(" NOT ")
                whereNestedMetadata(metaQuery.query)
            }

            is MetaAnd -> {
                metaQuery.filterNotNull().forEachIndexed { index, subQuery ->
                    if (index == 0){
                        where.append(" ( ")
                    }
                    if (index > 0) {
                        where.append(" AND ")
                    }
                }
            }

            is MetaOr -> {
                metaQuery.filterNotNull().forEachIndexed { index, subQuery ->
                    if (index > 0) {
                        where.append(" OR ")
                    }
                    whereNestedMetadata(subQuery)
                }
            }

            is MetaQuery -> {
                val pgColumn = PgColumn.ofRowColumn(metaQuery.column) ?: throw NakshaException(
                    NakshaError.ILLEGAL_STATE,
                    "Couldn't find PgColumn for TupleColumn: ${metaQuery.column.name}"
                )
                val placeholder = placeholderForArg(metaQuery.value, pgColumn.type)
                val resolvedQuery = when (val op = metaQuery.op) {
                    is StringOp -> resolveStringOp(op, pgColumn, placeholder)
                    is DoubleOp -> resolveDoubleOp(op, pgColumn, placeholder)
                    else -> throw NakshaException(
                        NakshaError.ILLEGAL_ARGUMENT,
                        "Unknown op type: ${op::class.simpleName}"
                    )
                }
                where.append(resolvedQuery)
            }

            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown metadata query type: ${metaQuery::class.simpleName}"
            )
        }
    }

    private fun placeholderForArg(value: Any?, type: PgType): String {
        argValues.add(value)
        argTypes.add(type.toString())
        return "\$${argTypes.size}"
    }

    private fun resolveStringOp(
        stringOp: StringOp,
        column: PgColumn,
        valuePlaceholder: String
    ): String {
        return when (stringOp) {
            StringOp.EQUALS -> "${column.name} = $valuePlaceholder"
            StringOp.STARTS_WITH -> "starts_with(${column.name}, $valuePlaceholder)"
            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown StringOp: $stringOp"
            )
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
            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown DoubleOp: $doubleOp"
            )
        }
    }
}
