package naksha.psql

import naksha.base.ListProxy
import naksha.geo.HereTile
import naksha.geo.SpGeometry
import naksha.model.*
import naksha.model.request.ReadFeatures
import naksha.model.request.query.*

/**
 * Helper to convert a [ReadFeatures] request into a sql `WHERE` query.
 * @param request the request to wrap.
 * @since 3.0
 * @see [build]
 */
internal class PgQueryWhereBuilder(private val request: ReadFeatures) {

    private val argValues: MutableList<Any?> = mutableListOf()
    private val argTypes: MutableList<PgType> = mutableListOf()
    private val where = StringBuilder()

    /**
     * Convert the request into a `WHERE` query.
     * @return the [PgQueryWhereClause].
     * @since 3.0
     */
    fun build(): PgQueryWhereClause? {
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
            PgQueryWhereClause(where = where.toString(), argValues = argValues, argTypes = argTypes)
        }
    }

    private fun whereFeatureId() {
        val featureIds = request.featureIds.filterNotNull()
        if (featureIds.isEmpty()) return

        // Partition into numeric IDs (fn >= 0, id stored as NULL in DB) and named IDs (fn < 0, id NOT NULL).
        val numericFns = featureIds.mapNotNull { id ->
            val fn = Naksha.featureNumber(id)
            if (fn >= naksha.base.Int64(0)) fn else null
        }
        val namedIds = featureIds.filter { id -> Naksha.featureNumber(id) < naksha.base.Int64(0) }

        val conditions = mutableListOf<String>()
        if (namedIds.isNotEmpty()) {
            val placeholder = placeholderForArg(namedIds.toTypedArray(), PgType.STRING_ARRAY)
            conditions += "${PgColumn.id} = ANY($placeholder)"
        }
        if (numericFns.isNotEmpty()) {
            val placeholder = placeholderForArg(numericFns.toTypedArray<Any?>(), PgType.INT64_ARRAY)
            conditions += "${PgColumn.fn} = ANY($placeholder)"
        }
        if (conditions.isNotEmpty()) {
            if (where.isNotEmpty()) where.append(" AND ")
            if (conditions.size == 1) where.append(conditions[0])
            else where.append("(${conditions.joinToString(" OR ")})")
        }
    }

    private fun whereGuids() {
        val tupleNumbers = request.guids.mapNotNull { it?.tupleNumber }
        if (tupleNumbers.isNotEmpty()) {
            if (where.isNotEmpty()) where.append(" AND ")
            val fns = arrayOfNulls<Any>(tupleNumbers.size)
            val versions = arrayOfNulls<Any>(tupleNumbers.size)
            for (i in tupleNumbers.indices) {
                fns[i] = tupleNumbers[i].featureNumber
                versions[i] = tupleNumbers[i].version.txn
            }
            val fnPlaceholder = placeholderForArg(fns, PgType.INT64_ARRAY)
            val versionPlaceholder = placeholderForArg(versions, PgType.INT64_ARRAY)
            where.append("(${PgColumn.fn}, ${PgColumn.version}) IN (SELECT * FROM unnest($fnPlaceholder::int8[], $versionPlaceholder::int8[]))")
        }
    }

    private fun whereVersion() {
        val txn = request.version
        if (txn != null) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("${PgColumn.version} <= ${txn.txn}")
        }
        val min_txn = request.minVersion
        if (min_txn != null) {
            if (where.isNotEmpty()) where.append(" AND ")
            where.append("${PgColumn.version} >= ${min_txn.txn}")
        }
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
                val queryGeometry = nakshaGeometry(spatial.geometry)
                val geometryToCompare = when (val transformation = spatial.transformation) {
                    null -> queryGeometry
                    else -> resolveTransformation(transformation, queryGeometry)
                }
                where.append("ST_Intersects(naksha_2d(${PgColumn.geo}), $geometryToCompare)")
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

    private fun nakshaGeometry(geometry: SpGeometry): String {
        val geoBytes = Naksha.encodeGeometry(geometry)
        val geoBytesPlaceholder = placeholderForArg(geoBytes, PgType.BYTE_ARRAY)
        return "naksha_2d($geoBytesPlaceholder)"
    }

    private fun resolveTransformation(
        transformation: SpTransformation,
        basicGeometry: String
    ): String {
        return when (transformation) {
            is SpBuffer -> resolveBuffer(transformation, basicGeometry)
            else -> throw NakshaException(
                NakshaError.UNSUPPORTED_OPERATION,
                "This transformation is not yet supported: ${transformation::class.simpleName}"
            )
        }
    }

    private fun resolveBuffer(buffer: SpBuffer, basicGeometry: String): String {
        val geo = if (buffer.geography) {
            "$basicGeometry::geography"
        } else {
            basicGeometry
        }
        val distancePlaceholder = placeholderForArg(buffer.distance, PgType.DOUBLE)
        val bufferStyleParams = bufferStyleParams(buffer)
        return if (bufferStyleParams != null) {
            "ST_Buffer($geo, $distancePlaceholder, $bufferStyleParams)"
        } else {
            "ST_Buffer($geo, $distancePlaceholder)"
        }
    }

    private fun bufferStyleParams(buffer: SpBuffer): String? {
        val bufferStyleParams = StringBuilder()
        if (buffer.quadSegments != null) {
            val quadSegPlaceholder = placeholderForArg(buffer.quadSegments, PgType.INT)
            bufferStyleParams.append("quad_segs=$quadSegPlaceholder")
        }
        if (buffer.joinStyle != null) {
            val joinStylePlaceholder = placeholderForArg(buffer.joinStyle!!.value, PgType.STRING)
            if (bufferStyleParams.isNotEmpty()) bufferStyleParams.append(" ")
            bufferStyleParams.append("join=$joinStylePlaceholder")
        }
        if (buffer.joinLimit != null) {
            val joinLimitPlaceholder = placeholderForArg(buffer.joinLimit, PgType.DOUBLE)
            if (bufferStyleParams.isNotEmpty()) bufferStyleParams.append(" ")
            bufferStyleParams.append("mitre_limit=$joinLimitPlaceholder")
        }
        if (buffer.endCap != null) {
            val endCapPlaceholder = placeholderForArg(buffer.endCap!!.value, PgType.STRING)
            if (bufferStyleParams.isNotEmpty()) bufferStyleParams.append(" ")
            bufferStyleParams.append("endcap=$endCapPlaceholder")
        }
        if (buffer.side != null) {
            val sidePlaceholder = placeholderForArg(buffer.side!!.value, PgType.STRING)
            if (bufferStyleParams.isNotEmpty()) bufferStyleParams.append(" ")
            bufferStyleParams.append("side=$sidePlaceholder")
        }
        return if (bufferStyleParams.isNotEmpty()) {
            bufferStyleParams.toString()
        } else {
            null
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
        return hereTiles.joinToString(separator = " OR ") { hereTile ->
            refPointInTile(hereTile)
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
        return "(${PgColumn.here_tile} >= $lowerBoundPlaceholder AND ${PgColumn.here_tile} <= $upperBoundPlaceholder)"
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
                val isActionQuery = metaQuery.column == MetaColumn.action()
                val pgColumn =
                    if (isActionQuery) {
                        PgColumn.version
                    } else {
                        PgColumn.ofRowColumn(metaQuery.column) ?: throw NakshaException(
                            NakshaError.ILLEGAL_STATE,
                            "Couldn't find PgColumn for TupleColumn: ${metaQuery.column.name}"
                        )
                    }
                val leftOperand = if (isActionQuery) {
                    "(${PgColumn.version.name} & 3)::int4"
                } else if (pgColumn == PgColumn.created_at || pgColumn == PgColumn.author_ts) {
                    "COALESCE(${pgColumn.name}, ${PgColumn.updated_at.name})"
                } else {
                    pgColumn.name
                }
                // Action lives in the lower 2 bits of `version`; the comparison value is a small int.
                val placeholderType = if (isActionQuery) PgType.INT else pgColumn.type
                val resolvedQuery = when (val op = metaQuery.op) {
                    is StringOp -> {
                        val placeholder = placeholderForArg(metaQuery.value, placeholderType)
                        resolveStringOp(op, leftOperand, placeholder)
                    }
                    is DoubleOp -> {
                        val placeholder = placeholderForArg(metaQuery.value, placeholderType)
                        resolveDoubleOp(op, leftOperand, placeholder)
                    }
                    AnyOp.IS_ANY_OF -> {
                        val placeholder = placeholderForArg(metaQuery.value, arrayTypeFor(placeholderType))
                        "$leftOperand = ANY($placeholder)"
                    }
                    else -> throw illegalArg("Unknown op type: ${op::class.simpleName}")
                }
                where.append(resolvedQuery)
            }

            else -> throw NakshaException(
                NakshaError.ILLEGAL_ARGUMENT,
                "Unknown metadata query type: ${metaQuery::class.simpleName}"
            )
        }
    }

    private fun arrayTypeFor(pgType: PgType): PgType {
        return when (pgType) {
            PgType.BOOLEAN -> PgType.BOOLEAN_ARRAY
            PgType.SHORT -> PgType.SHORT_ARRAY
            PgType.INT -> PgType.INT_ARRAY
            PgType.INT64 -> PgType.INT64_ARRAY
            PgType.FLOAT -> PgType.FLOAT_ARRAY
            PgType.DOUBLE -> PgType.DOUBLE_ARRAY
            PgType.STRING -> PgType.STRING_ARRAY
            PgType.BYTE_ARRAY -> PgType.BYTE_ARRAY_ARRAY
            else -> throw illegalArg("Unknown array type for PgType: ${pgType::class.simpleName}")
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
            is TagOr -> {
                if(containsOnlyTagExists(tagQuery)){
                    // for tags without values we can utilize top-level-key based '?|' operand
                    // https://www.postgresql.org/docs/current/functions-json.html#FUNCTIONS-JSONB-OP-TABLE
                    val tagNames = tagQuery.filterIsInstance<TagExists>().map { it.name }
                    resolveTagNamesArrayOperation(
                        jsonbOperator = "?|", // 'jsonb_exists_any' is equivalent but will not hit the GIN index
                        tagNames = tagNames
                    )
                } else {
                    or(tagQuery.filterNotNull(), this::whereNestedTags)
                }
            }
            is TagAnd -> {
                if(containsOnlyTagExists(tagQuery)){
                    // for tags without values we can utilize top-level-key based '?&' operand
                    // https://www.postgresql.org/docs/current/functions-json.html#FUNCTIONS-JSONB-OP-TABLE
                    val tagNames = tagQuery.filterIsInstance<TagExists>().map { it.name }
                    resolveTagNamesArrayOperation(
                        jsonbOperator = "?&", // 'jsonb_exists_all' is equivalent but MIGHT not hit the GIN index
                        tagNames = tagNames
                    )
                } else {
                    and(tagQuery.filterNotNull(), this::whereNestedTags)
                }
            }
            is TagQuery -> resolveSingleTagQuery(tagQuery)
        }
    }

    private fun containsOnlyTagExists(container: ListProxy<ITagQuery>): Boolean =
        container.all { it == null || it is TagExists }

    private fun resolveTagNamesArrayOperation(jsonbOperator: String, tagNames: List<String>) {
        val tagKeysArray = tagNames.toTypedArray()
        val tagKeysPlaceholder = placeholderForArg(tagKeysArray, PgType.STRING_ARRAY)
        where.append("$tagsAsJsonb ?$jsonbOperator $tagKeysPlaceholder")
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
        argTypes.add(type)
        return "\$${argTypes.size}"
    }

    private fun resolveStringOp(
        stringOp: StringOp,
        leftOperand: String,
        rightOperand: String
    ): String {
        return when (stringOp) {
            StringOp.EQUALS -> "$leftOperand = $rightOperand"
            StringOp.NOT_EQUALS -> "$leftOperand != $rightOperand"
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
            DoubleOp.NE -> "$leftOperand != $rightOperand"
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
        private val tagsAsJsonb = "naksha_tags(${PgColumn.tags})"
    }
}
