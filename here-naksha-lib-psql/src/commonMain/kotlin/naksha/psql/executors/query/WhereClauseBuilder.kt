package naksha.psql.executors.query

import naksha.geo.HereTile
import naksha.model.*
import naksha.model.GeoEncoding.GeoEncoding_C.TWKB
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
        whereRefTiles()
        whereTags()
        return if (where.isBlank()) {
            null
        } else {
            WhereClause(sql = " WHERE $where", argValues = argValues, argTypes = argTypes)
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
        val spatialQuery = request.query.spatial
        if (spatialQuery != null) {
            if (where.isNotEmpty()) {
                where.append(" AND (")
            } else {
                where.append(" (")
            }
            whereNestedSpatial(spatialQuery)
            where.append(")")
        }
    }

    private fun whereNestedSpatial(spatial: ISpatialQuery) {
        when (spatial) {
            is SpNot -> not(
                subClause = spatial.query,
                subClauseResolver = this::whereNestedSpatial
            )

            is SpAnd -> and(
                subClauses = spatial.filterNotNull(),
                subClauseResolver = this::whereNestedSpatial
            )

            is SpOr -> or(
                subClauses = spatial.filterNotNull(),
                subClauseResolver = this::whereNestedSpatial
            )

            is SpIntersects -> {
                val twkb = PgUtil.encodeGeometry(
                    spatial.geometry,
                    Flags().geoGzipOff().geoEncoding(TWKB)
                )
                val twkbPlaceholder = placeholderForArg(twkb, PgType.BYTE_ARRAY)
                val twkbAsGeometry = "ST_GeomFromTWKB($twkbPlaceholder)"
                val geometryToCompare = when(spatial.transformation) {
                    null -> twkbAsGeometry
                    else -> resolveTransformation(spatial.transformation!!, twkbAsGeometry)
                }
                where.append("ST_Intersects(naksha_geometry(${PgColumn.geo}, ${PgColumn.flags}), $geometryToCompare)")
            }

            is SpRefInHereTile -> {
                where.append(refPointInTile(spatial.getHereTile()))
            }

            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Invalid spatial query found: $spatial"
            )
        }
    }

    private fun resolveTransformation(transformation: SpTransformation, geometryStmt: String): String {
        return when(transformation){
            is SpBuffer -> "ST_Buffer($geometryStmt, ${transformation.distance})"
            else -> throw NakshaException(
                NakshaError.UNSUPPORTED_OPERATION,
                "This transformation is not yet supported: ${transformation::class.simpleName}"
            )
        }
    }

    private fun whereRefTiles() {
        val hereTiles = request.query.refTiles
            .filterNotNull()
            .map { HereTile(it) }
        if (hereTiles.isNotEmpty()) {
            if (where.isNotEmpty()) {
                where.append(" AND (")
            } else {
                where.append(" (")
            }
            where.append(refPointInAnyOfTiles(hereTiles))
            where.append(")")
        }
    }

    private fun refPointInAnyOfTiles(hereTiles: List<HereTile>): String {
        return hereTiles.joinToString(separator = " OR ") {
            hereTile -> refPointInTile(hereTile)
        }
    }

    private fun refPointInTile(hereTile: HereTile): String {
        val lowerBoundPlaceholder = placeholderForArg(
            hereTile.maxLevelLowerBound().intKey,
            PgType.INT
        )
        val upperBoundPlaceholder = placeholderForArg(
            hereTile.maxLevelUpperBound().intKey,
            PgType.INT
        )
        return "(${PgColumn.geo_grid} >= $lowerBoundPlaceholder AND ${PgColumn.geo_grid} <= $upperBoundPlaceholder)"
    }

    private fun whereMetadata() {
        val metaQuery = request.query.metadata
        if (metaQuery != null) {
            if (where.isNotEmpty()) {
                where.append(" AND (")
            } else {
                where.append(" (")
            }
            whereNestedMetadata(metaQuery)
            where.append(")")
        }
    }

    private fun whereNestedMetadata(metaQuery: IMetaQuery) {
        when (metaQuery) {
            is MetaNot -> not(
                subClause = metaQuery.query,
                subClauseResolver = this::whereNestedMetadata
            )

            is MetaAnd -> and(
                subClauses = metaQuery.filterNotNull(),
                subClauseResolver = this::whereNestedMetadata
            )

            is MetaOr -> or(
                subClauses = metaQuery.filterNotNull(),
                subClauseResolver = this::whereNestedMetadata
            )

            is MetaQuery -> {
                val pgColumn = PgColumn.ofRowColumn(metaQuery.column) ?: throw NakshaException(
                    NakshaError.ILLEGAL_STATE,
                    "Couldn't find PgColumn for TupleColumn: ${metaQuery.column.name}"
                )
                val placeholder = placeholderForArg(metaQuery.value, pgColumn.type)
                val resolvedQuery = when (val op = metaQuery.op) {
                    is StringOp -> resolveStringOp(op, pgColumn.name, placeholder)
                    is DoubleOp -> resolveDoubleOp(op, pgColumn.name, placeholder)
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

    private fun whereTags() {
        val tagQuery = request.query.tags
        if (tagQuery != null) {
            if (where.isNotEmpty()) {
                where.append(" AND (")
            } else {
                where.append(" (")
            }
            whereNestedTags(tagQuery)
            where.append(")")
        }
    }

    private fun whereNestedTags(tagQuery: ITagQuery) {
        when (tagQuery) {
            is TagNot -> not(tagQuery.query, this::whereNestedTags)
            is TagOr -> or(tagQuery.filterNotNull(), this::whereNestedTags)
            is TagAnd -> and(tagQuery.filterNotNull(), this::whereNestedTags)
            is TagQuery -> resolveSingleTagQuery(tagQuery)
        }
    }

    private fun resolveSingleTagQuery(tagQuery: TagQuery) {
        when (tagQuery) {
            is TagExists -> {
                val tagNamePlaceholder = placeholderForArg(tagQuery.name, PgType.STRING)
                where.append("$tagsAsJsonb ?? $tagNamePlaceholder")
            }

            is TagValueIsNull -> {
                val tagValuePlaceholder = placeholderForArg(selectTagValue(tagQuery), PgType.STRING)
                where.append("$tagValuePlaceholder IS NULL")
            }

            is TagValueIsBool -> {
                if (tagQuery.value) {
                    where.append(selectTagValue(tagQuery, PgType.BOOLEAN))
                } else {
                    where.append("not(${selectTagValue(tagQuery, PgType.BOOLEAN)})")
                }
            }

            is TagValueIsDouble -> {
                val queryValuePlaceholder = placeholderForArg(tagQuery.value, PgType.DOUBLE)
                val doubleOp = resolveDoubleOp(
                    tagQuery.op,
                    selectTagValue(tagQuery, PgType.DOUBLE),
                    queryValuePlaceholder
                )
                where.append(doubleOp)
            }

            is TagValueIsString -> {
                val queryValuePlaceholder = placeholderForArg(tagQuery.value, PgType.STRING)
                val stringEquals = resolveStringOp(
                    StringOp.EQUALS,
                    selectTagValue(tagQuery, PgType.STRING),
                    queryValuePlaceholder
                )
                where.append(stringEquals)
            }

            is TagValueMatches -> {
                val jsonPathPlaceholder = placeholderForArg(
                    "\$.${tagQuery.name} ? (@ like_regex \"${tagQuery.regex}\")",
                    PgType.STRING
                )
                where.append("$tagsAsJsonb @?? $jsonPathPlaceholder::jsonpath")
            }
        }
    }

    private fun selectTagValue(tagQuery: TagQuery, castTo: PgType? = null): String {
        val tagKeyPlaceholder = placeholderForArg(tagQuery.name, PgType.STRING)
        return when (castTo) {
            null -> "$tagsAsJsonb->$tagKeyPlaceholder"
            PgType.STRING -> "$tagsAsJsonb->>$tagKeyPlaceholder"
            else -> "($tagsAsJsonb->$tagKeyPlaceholder)::${castTo.value}"
        }
    }

    private fun <T : IQuery> not(subClause: T, subClauseResolver: (T) -> Unit) {
        where.append(" NOT (")
        subClauseResolver(subClause)
        where.append(") ")
    }

    private fun <T : IQuery> and(subClauses: List<T>, subClauseResolver: (T) -> Unit) =
        multiClause("AND", subClauses, subClauseResolver)

    private fun <T : IQuery> or(subClauses: List<T>, subClauseResolver: (T) -> Unit) =
        multiClause("OR", subClauses, subClauseResolver)

    private fun <T : IQuery> multiClause(
        operand: String,
        subClauses: List<T>,
        subClauseResolver: (T) -> Unit
    ) {
        where.append(" (")
        subClauses.forEachIndexed { index, subClause ->
            if (index > 0) {
                where.append(" $operand ")
            }
            subClauseResolver(subClause)
        }
        where.append(") ")
    }

    private fun placeholderForArg(value: Any?, type: PgType): String {
        argValues.add(value)
        argTypes.add(type.toString())
        return "\$${argTypes.size}"
    }

    private fun resolveStringOp(
        stringOp: StringOp,
        leftOperand: String,
        rightOperand: String
    ): String {
        return when (stringOp) {
            StringOp.EQUALS -> "$leftOperand = $rightOperand"
            StringOp.STARTS_WITH -> "starts_with($leftOperand, $rightOperand)"
            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown StringOp: $stringOp"
            )
        }
    }

    private fun resolveDoubleOp(
        doubleOp: DoubleOp,
        leftOperand: String,
        rightOperand: String
    ): String {
        return when (doubleOp) {
            DoubleOp.EQ -> "$leftOperand = $rightOperand"
            DoubleOp.GT -> "$leftOperand > $rightOperand"
            DoubleOp.GTE -> "$leftOperand >= $rightOperand"
            DoubleOp.LT -> "$leftOperand < $rightOperand"
            DoubleOp.LTE -> "$leftOperand <= $rightOperand"
            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown DoubleOp: $doubleOp"
            )
        }
    }

    companion object {
        private val tagsAsJsonb = "naksha_tags(${PgColumn.tags}, ${PgColumn.flags})"
    }
}
