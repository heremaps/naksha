package naksha.psql

import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.StringList
import naksha.model.Naksha
import naksha.model.NakshaError
import naksha.model.NakshaException
import naksha.model.illegalArg
import naksha.model.objects.StandardMembers
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.*
import naksha.psql.PgColumn.PgColumn_C.FN
import naksha.psql.PgColumn.PgColumn_C.VERSION

/**
 * Helper to convert a [ReadFeatures] request into a sql `WHERE` query.
 *
 * The collection need to be provided, because they potentially _(when not cached)_ need to be read from the database, therefore, they require a session. We do not want to link this code to a session _(it would break unit testing)_, so we simply expect the collections as input parameter.
 * @param request the request to wrap.
 * @param collection the collection for which to generate the `WHERE` query.
 * @since 3.0
 * @see [build]
 */
internal class PgQueryWhereBuilder(private val request: ReadFeatures, private val collection: PgCollection) {

    val where = StringBuilder()
    val argValues: MutableList<Any?> = mutableListOf()
    val argTypes: MutableList<PgType> = mutableListOf()

    /**
     * Convert the request into `WHERE` queries.
     * @return the [PgQueryWhereClause]; `null` if basically everything should be read.
     * @since 3.0
     */
    fun build(): PgQueryWhereClause? {
        var op: Op? = request.queryMembers
        if (op == null) op = QueryConverter.convert(request.query)
        if (op == null) return null
        applyOp(op)
        if (request.featureIds.isNotEmpty()) { // backward compatibility for feature IDs read requests
            whereFeatureId()
        }
        if (request.guids.isNotEmpty()) { // backward compatibility for GUIDs read requests
            whereGuids()
        }
        return PgQueryWhereClause(collection, where.toString(), argValues, argTypes)
    }

    private fun applyOp(rawOp: Op, negate: Boolean = false) {
        val opName = rawOp.op
        val op = Op.detect(rawOp) ?: throw illegalArg("Unknown operation: '$opName'")
        when (op) {
            is And -> {
                if (negate) where.append(" NOT ")
                val children = op.children
                if (children.size > 1) where.append('(')
                var first = true
                for (child in children) {
                    if (child == null) continue
                    if (first) first = false else where.append(" AND ")
                    // TODO optimization if only TagMapHasKey
                    applyOp(child)
                }
                if (children.size > 1) where.append(") ") else where.append(" ")
                return
            }
            is Or -> {
                if (negate) where.append(" NOT ")
                val children = op.children
                if (children.size > 1) where.append('(')
                var first = true
                for (child in children) {
                    if (child == null) continue
                    if (first) first = false else where.append(" OR ")
                    // TODO optimization if only TagMapHasKey
                    applyOp(child)
                }
                if (children.size > 1) where.append(") ") else where.append(" ")
                return
            }
            is Not -> {
                applyOp(op.child, !negate)
                return
            }
        }
        val rawAt: String = op.at ?: throw illegalArg("Missing member name for operation $opName")
        // The action is virtual: resolve it to the version bit-mask instead of a physical column.
        val isAction = rawAt == StandardMembers.Action.name
        val at: String = if (isAction) "(${PgColumn.VERSION.name} & 3)::int4" else rawAt
        when (op) {
           is IsNull -> {
                if (negate)
                    where.append(at).append(" IS NOT NULL").append(' ')
                else
                    where.append(at).append(" IS NULL").append(' ')
            }
            is IsTrue -> {
                if (negate)
                    where.append(at).append('=').append(placeholderForArg(false, PgType.BOOLEAN)).append(' ')
                else
                    where.append(at).append('=').append(placeholderForArg(true, PgType.BOOLEAN)).append(' ')
            }
            is IsFalse -> {
                if (negate)
                    where.append(at).append('=').append(placeholderForArg(true, PgType.BOOLEAN)).append(' ')
                else
                    where.append(at).append('=').append(placeholderForArg(false, PgType.BOOLEAN)).append(' ')
            }
            is Equals -> {
                val value: Any? = op.value
                if (value == null) {
                    if (negate) where.append(at).append(" IS NOT NULL ") else where.append(at).append(" IS NULL ")
                } else {
                    val placeholder = if (isAction) placeholderForArg(value, PgType.INT) else placeholderForArg(value)
                    if (negate)
                        where.append(at).append("!=").append(placeholder).append(' ')
                    else
                        where.append(at).append('=').append(placeholder).append(' ')
                }
            }
            is Lt -> {
                if (negate) // NOT Greater Than
                    where.append(at).append("<=").append(placeholderForArg(op.value)).append(' ')
                else // Greater Than
                    where.append(at).append(">").append(placeholderForArg(op.value)).append(' ')
            }
            is Gte -> {
                if (negate) // NOT Greater Than or Equal to
                    where.append(at).append("<").append(placeholderForArg(op.value)).append(' ')
                else // Greater Than or Equal to
                    where.append(at).append(">=").append(placeholderForArg(op.value)).append(' ')
            }
            is Lt -> {
                if (negate) // NOT Less Than
                    where.append(at).append(">=").append(placeholderForArg(op.value)).append(' ')
                else // Less Than
                    where.append(at).append("<").append(placeholderForArg(op.value)).append(' ')
            }
            is Lte -> {
                if (negate) // NOT Less Than or Equal to
                    where.append(at).append(">").append(placeholderForArg(op.value)).append(' ')
                else // Less Than or Equal to
                    where.append(at).append("<=").append(placeholderForArg(op.value)).append(' ')
            }
            is StartsWith -> {
                if (negate) where.append("NOT ")
                where.append("starts_with(").append(at).append(", ").append(placeholderForArg(op.value)).append(") ")
            }
            is IsAnyOf -> {
                if (negate) where.append("NOT ")
                val placeholder = if (isAction) placeholderForArg(op.items, PgType.INT_ARRAY) else placeholderForArg(op.items)
                where.append(at).append("= ANY(").append(placeholder).append(") ")
            }
            is TagMapHasKey -> {
                if (negate) where.append("NOT ")
                where.append(at).append("::jsonb").append(" ? ").append(placeholderForArg(op.key)).append(" ")
            }
            is TagMapHasAnyOf -> {
                if (negate) where.append("NOT ")
                where.append(at).append("::jsonb").append(" ?| ").append(placeholderForArg(op.keys)).append(" ")
            }
            is TagMapHasAllOf -> {
                if (negate) where.append("NOT ")
                where.append(at).append("::jsonb").append(" ?& ").append(placeholderForArg(op.keys)).append(" ")
            }
            is TagIsNull -> {
                // ( foo::jsonb ? $1 AND ((foo::jsonb)->>$1) IS [NOT ]NULL)
                where.append("( ")
                    .append(at).append("::jsonb").append(" ? ").append(placeholderForArg(op.key)).append(" AND ")
                    .append("((").append(at).append("::jsonb)").append("->>").append(placeholderForArg(op.key)).append(")")
                if (negate) where.append("IS NOT NULL) ") else where.append("IS NULL) ")
            }
            is TagEquals -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                if (negate) where.append("NOT ")
                // [NOT ]((foo::jsonb)->>$1)::int8 = $2
                where.append("((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append("=").append(placeholderForArg(value, pgType))
            }
            is TagGt -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                if (negate) where.append("NOT ")
                // [NOT ]((foo::jsonb)->>$1)::int8 > $2
                where.append("((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append(">").append(placeholderForArg(value, pgType))
            }
            is TagGte -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                if (negate) where.append("NOT ")
                // [NOT ]((foo::jsonb)->>$1)::int8 >= $2
                where.append("((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append(">=").append(placeholderForArg(value, pgType))
            }
            is TagLt -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                if (negate) where.append("NOT ")
                // [NOT ]((foo::jsonb)->>$1)::int8 < $2
                where.append("((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append("<").append(placeholderForArg(value, pgType))
            }
            is TagLte -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                if (negate) where.append("NOT ")
                // [NOT ]((foo::jsonb)->>$1)::int8 <= $2
                where.append("((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append("<=").append(placeholderForArg(value, pgType))
            }
            is TagStartsWith -> {
                val pgType = PgType.ofValue(op.value) ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.value}")
                val value = pgType.convertValue(op.value)
                // [NOT ]starts_with(((foo::jsonb)->>$1), $2)
                if (negate) where.append("NOT ")
                where.append("starts_with(((").append(at).append("::jsonb)")
                    .append("->>").append(placeholderForArg(op.key)).append("::").append(pgType).append(")")
                    .append(", ").append(placeholderForArg(value, pgType)).append(") ")
            }
            is TagListContains -> {
                val pgType = PgType.ofValue(op.item)
                    ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.item}")
                val value = pgType.convertValue(op.item)
                // [NOT ]foo::jsonb @> $1::jsonb
                val placeholder = placeholderForArg(value, pgType)
                if (negate) where.append("NOT ")
                where.append(at).append("::jsonb @> ").append(placeholder).append("::jsonb ")
            }
            is TagListContainsAllOf -> {
                val pgType = PgType.ofValue(op.items)
                    ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.items}")
                val value = pgType.convertValue(op.items)
                val placeholder = placeholderForArg(toJSON(value), pgType)
                if (negate) where.append("NOT ")
                where.append(at).append("::jsonb @> ").append(placeholder).append("::jsonb ")
            }
            is TagListContainsAnyOf -> {
                val items = op.items.filterNotNull()
                // Any-of over empty set -> false; negated -> true
                if (items.isEmpty()) {
                    if (negate) where.append("TRUE ") else where.append("FALSE ")
                    return
                }

                if (negate) where.append("NOT ")
                // Multiple items: build OR of single-element containment checks
                where.append('(')
                val pgType = PgType.ofValue(op.items.first())
                    ?: throw illegalArg("The given value is no valid argument for ${op.op}}: ${op.items.first()}")
                var first = true
                for (item in items) {
                    if (!first) where.append(" OR ")
                    first = false
                    val value = pgType.convertValue(item)
                    val placeholder = placeholderForArg(value, pgType)
                    where.append(at).append("::jsonb @> ").append(placeholder).append("::jsonb")
                }
                where.append(") ")
            }
            is Intersects -> {
                val geoBytes = Naksha.encodeGeometry(op.value)
                val geoBytesPlaceholder = placeholderForArg(geoBytes, PgType.BYTE_ARRAY)
                val queryGeometry = "naksha_2d($geoBytesPlaceholder)"
                val transformation = op.transformers
                val geometryToCompare =
                    if (transformation.isEmpty()) queryGeometry
                    else resolveTransformation(transformation, queryGeometry)
                where.append("ST_Intersects(naksha_2d(${StandardMembers.Geometry}), $geometryToCompare)")
            }
            else -> throw illegalArg("Unknown operation: '$op'")
        }
    }

    /**
     * Returns a placeholder string like `$1` for the given value, and add the values and its type into the value and type arrays.
     */
    private fun placeholderForArg(value: Any?): String {
        // TODO: Detect the type, if not possible, throw illegalArg()
        //       AnyList for + ANY()
        val type: PgType = PgType.STRING;
        argValues.add(value)
        argTypes.add(type)
        return "\$${argTypes.size}"
    }

    /**
     * Returns a placeholder string like `$1` for the given value, and add the values and its type into the value and type arrays.
     * @param value any number _(including `Int64`)_
     * @return
     */
    private fun placeholderForNumber(value: Any?): String {
        // TODO: Detect the type, which must be any number, otherwise throw illegalArg()
        val type: PgType = PgType.STRING;
        argValues.add(value)
        argTypes.add(type)
        return "\$${argTypes.size}"
    }

    /**
     * Returns a placeholder string like `$1` for the given value, and add the values and its type into the value and type arrays.
     */
    private fun placeholderForArg(value: Any?, type: PgType): String {
        // TODO: Ensure that the value matches the type, otherwise throw illegalArg()!
        argValues.add(value)
        argTypes.add(type)
        return "\$${argTypes.size}"
    }

    private fun resolveTransformation(
        transformationList: SpTransformationList,
        basicGeometry: String
    ): String {
        var geometry = basicGeometry
        for (transformation in transformationList) {
            geometry = when (transformation) {
                is SpBuffer -> resolveBuffer(transformation, geometry)
                else -> throw NakshaException(
                    NakshaError.UNSUPPORTED_OPERATION,
                    "This transformation is not yet supported: ${transformation!!::class.simpleName}"
                )
            }
        }
        return geometry
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

        private fun whereFeatureId() {
        // Partition into numeric IDs (fn >= 0, id stored as NULL in DB) and named IDs (fn < 0, id NOT NULL).
        val reqIds: StringList = request.featureIds
        val featureNumbers: MutableList<Int64> = mutableListOf()
        val featureIds: MutableList<String> = mutableListOf()
        for (id in reqIds) {
            if (id == null) continue
            val fn = Naksha.featureNumber(id)
            if (fn >= Int64(0)) {
                featureNumbers.add(fn)
            } else {
                featureIds.add(id)
            }
        }
        if (featureNumbers.isEmpty() && featureIds.isEmpty()) return

        // For each collection:
        if (where.isNotEmpty()) where.append(" AND ")

        where.append("( ")
        if (featureIds.isNotEmpty()) {
            val op = IsAnyOf(at = StandardMembers.Id, items = featureIds.toTypedArray())
            applyOp(op)
        }
        if (featureNumbers.isNotEmpty()) {
            if (featureIds.isNotEmpty()) where.append(" OR ")

            val op = IsAnyOf(at = StandardMembers.FeatureNumber, items = featureNumbers.toTypedArray())
            applyOp(op)
        }
        where.append(")")
    }

    // --------------------------------------------------------< OLD CODE >-------------------------------------------------------------
//
    private fun whereGuids() {
        val tupleNumbers = request.guids.mapNotNull { it?.tupleNumber }
        if (tupleNumbers.isNotEmpty()) {
            if (where.isNotEmpty()) where.append(" AND ")

            val fns = arrayOfNulls<Any>(tupleNumbers.size)
            val versions = arrayOfNulls<Any>(tupleNumbers.size)
            for (i in tupleNumbers.indices) {
                fns[i] = tupleNumbers[i].featureNumber
                versions[i] = tupleNumbers[i].version
            }
            val featureNumbersArg = placeholderForArg(fns, PgType.INT64_ARRAY)
            val versionsArg = placeholderForArg(versions, PgType.INT64_ARRAY)
            where.append("($FN, $VERSION) IN (SELECT * FROM unnest($featureNumbersArg::int8[], $versionsArg::int8[]))")
        }
    }
//
//    private fun whereVersion() {
//        val version = request.version
//        if (version != null) {
//            if (where.isNotEmpty()) where.append(" AND ")
//            where.append("$VERSION <= ${version.toInt()}")
//        }
//        val minVersion = request.minVersion
//        if (minVersion != null) {
//            if (where.isNotEmpty()) where.append(" AND ")
//            where.append("$VERSION >= ${minVersion.toInt()}")
//        }
//    }
//
//    private fun whereSpatial() {
//        val spatialQuery = request.query.spatial
//        if (spatialQuery != null) {
//            if (where.isNotEmpty()) {
//                where.append(" AND (")
//            } else {
//                where.append(" (")
//            }
//            whereNestedSpatial(spatialQuery)
//            where.append(")")
//        }
//    }
//
//    private fun whereNestedSpatial(spatial: ISpatialQuery) {
//        when (spatial) {
//            is SpNot -> not(
//                subClause = spatial.query,
//                subClauseResolver = this::whereNestedSpatial
//            )
//
//            is SpAnd -> and(
//                subClauses = spatial.filterNotNull(),
//                subClauseResolver = this::whereNestedSpatial
//            )
//
//            is SpOr -> or(
//                subClauses = spatial.filterNotNull(),
//                subClauseResolver = this::whereNestedSpatial
//            )
//
//            is SpIntersects -> {
//                val queryGeometry = nakshaGeometry(spatial.geometry)
//                val geometryToCompare = when (val transformation = spatial.transformation) {
//                    null -> queryGeometry
//                    else -> resolveTransformation(transformation, queryGeometry)
//                }
//                where.append("ST_Intersects(naksha_2d(${StandardMembers.Geometry}), $geometryToCompare)")
//            }
//
//            is SpRefInHereTile -> {
//                where.append(refPointInTile(spatial.getHereTile()))
//            }
//
//            else -> throw NakshaException(
//                NakshaError.ILLEGAL_ARGUMENT,
//                "Invalid spatial query found: $spatial"
//            )
//        }
//    }
//
//
//    private fun whereRefTiles() {
//        val hereTiles = request.query.refTiles
//            .filterNotNull()
//            .map { HereTile(it) }
//        if (hereTiles.isNotEmpty()) {
//            if (where.isNotEmpty()) {
//                where.append(" AND (")
//            } else {
//                where.append(" (")
//            }
//            where.append(refPointInAnyOfTiles(hereTiles))
//            where.append(")")
//        }
//    }
//
//    private fun refPointInAnyOfTiles(hereTiles: List<HereTile>): String {
//        return hereTiles.joinToString(separator = " OR ") { hereTile ->
//            refPointInTile(hereTile)
//        }
//    }
//
//    private fun refPointInTile(hereTile: HereTile): String {
//        val lowerBoundPlaceholder = placeholderForArg(
//            hereTile.maxLevelLowerBound().intKey,
//            PgType.INT
//        )
//        val upperBoundPlaceholder = placeholderForArg(
//            hereTile.maxLevelUpperBound().intKey,
//            PgType.INT
//        )
//        return "(${StandardMembers.HereTile} >= $lowerBoundPlaceholder AND ${StandardMembers.HereTile} <= $upperBoundPlaceholder)"
//    }
//
//    private fun whereMetadata() {
//        val metaQuery = request.query.members
//        if (metaQuery != null) {
//            if (where.isNotEmpty()) {
//                where.append(" AND (")
//            } else {
//                where.append(" (")
//            }
//            whereNestedMetadata(metaQuery)
//            where.append(")")
//        }
//    }
//
//    private fun whereNestedMetadata(metaQuery: IMemberQuery) {
//        when (metaQuery) {
//            is MemberNot -> not(
//                subClause = metaQuery.query,
//                subClauseResolver = this::whereNestedMetadata
//            )
//
//            is MemberAnd -> and(
//                subClauses = metaQuery.filterNotNull(),
//                subClauseResolver = this::whereNestedMetadata
//            )
//
//            is MemberOr -> or(
//                subClauses = metaQuery.filterNotNull(),
//                subClauseResolver = this::whereNestedMetadata
//            )
//
//            is MemberQuery -> {
//                val isActionQuery = metaQuery.member == MetaColumn.action()
//                val pgColumn =
//                    if (isActionQuery) {
//                        StandardMembers.Version
//                    } else {
//                        PgColumn.ofRowColumn(metaQuery.member) ?: throw NakshaException(
//                            NakshaError.ILLEGAL_STATE,
//                            "Couldn't find PgColumn for TupleColumn: ${metaQuery.member.name}"
//                        )
//                    }
//                val leftOperand = if (isActionQuery) {
//                    "(${PgColumn.version.name} & 3)::int4"
//                } else if (pgColumn == PgColumn.created_at || pgColumn == PgColumn.author_ts) {
//                    "COALESCE(${pgColumn.name}, ${PgColumn.updated_at.name})"
//                } else {
//                    pgColumn.name
//                }
//                // Action lives in the lower 2 bits of `version`; the comparison value is a small int.
//                val placeholderType = if (isActionQuery) PgType.INT else pgColumn.type
//                val resolvedQuery = when (val op = metaQuery.op) {
//                    is StringOp -> {
//                        val placeholder = placeholderForArg(metaQuery.value, placeholderType)
//                        resolveStringOp(op, leftOperand, placeholder)
//                    }
//                    is DoubleOp -> {
//                        val placeholder = placeholderForArg(metaQuery.value, placeholderType)
//                        resolveDoubleOp(op, leftOperand, placeholder)
//                    }
//                    is AnyOp.IS_ANY_OF -> {
//                        val placeholder = placeholderForArg(metaQuery.value, arrayTypeFor(placeholderType))
//                        "$leftOperand = ANY($placeholder)"
//                    }
//                    else -> throw illegalArg("Unknown op type: ${op::class.simpleName}")
//                }
//                where.append(resolvedQuery)
//            }
//
//            else -> throw NakshaException(
//                NakshaError.ILLEGAL_ARGUMENT,
//                "Unknown metadata query type: ${metaQuery::class.simpleName}"
//            )
//        }
//    }
//
//    private fun arrayTypeFor(pgType: PgType): PgType {
//        return when (pgType) {
//            PgType.BOOLEAN -> PgType.BOOLEAN_ARRAY
//            PgType.SHORT -> PgType.SHORT_ARRAY
//            PgType.INT -> PgType.INT_ARRAY
//            PgType.INT64 -> PgType.INT64_ARRAY
//            PgType.FLOAT -> PgType.FLOAT_ARRAY
//            PgType.DOUBLE -> PgType.DOUBLE_ARRAY
//            PgType.STRING -> PgType.STRING_ARRAY
//            PgType.BYTE_ARRAY -> PgType.BYTE_ARRAY_ARRAY
//            else -> throw illegalArg("Unknown array type for PgType: ${pgType::class.simpleName}")
//        }
//    }
//
//    private fun whereTags() {
//        val tagQuery = request.query.tags
//        if (tagQuery != null) {
//            if (where.isNotEmpty()) {
//                where.append(" AND (")
//            } else {
//                where.append(" (")
//            }
//            whereNestedTags(tagQuery)
//            where.append(")")
//        }
//    }
//
//    private fun whereNestedTags(tagQuery: ITagQuery) {
//        when (tagQuery) {
//            is TagSetContains -> resolveTagSetContains(tagQuery)
//            is TagNot -> not(tagQuery.query, this::whereNestedTags)
//            is TagOr -> {
//                if(containsOnlyTagExists(tagQuery)){
//                    // for tags without values we can utilize top-level-key based '?|' operand
//                    // https://www.postgresql.org/docs/current/functions-json.html#FUNCTIONS-JSONB-OP-TABLE
//                    val tagNames = tagQuery.filterIsInstance<TagMapHasKey>().map { it.name }
//                    resolveTagNamesArrayOperation(
//                        jsonbOperator = "?|", // 'jsonb_exists_any' is equivalent but will not hit the GIN index
//                        tagNames = tagNames
//                    )
//                } else {
//                    or(tagQuery.filterNotNull(), this::whereNestedTags)
//                }
//            }
//            is TagAnd -> {
//                if(containsOnlyTagExists(tagQuery)){
//                    // for tags without values we can utilize top-level-key based '?&' operand
//                    // https://www.postgresql.org/docs/current/functions-json.html#FUNCTIONS-JSONB-OP-TABLE
//                    val tagNames = tagQuery.filterIsInstance<TagMapHasKey>().map { it.name }
//                    resolveTagNamesArrayOperation(
//                        jsonbOperator = "?&", // 'jsonb_exists_all' is equivalent but MIGHT not hit the GIN index
//                        tagNames = tagNames
//                    )
//                } else {
//                    and(tagQuery.filterNotNull(), this::whereNestedTags)
//                }
//            }
//            is TagQuery -> resolveSingleTagQuery(tagQuery)
//        }
//    }
//
//    private fun containsOnlyTagExists(container: ListProxy<ITagQuery>): Boolean =
//        container.all { it == null || it is TagMapHasKey }
//
//    /**
//     * Element containment on a set-form tags column (jsonb array): `tags @> '[<element>]'::jsonb`.
//     * The `@>` operator matches the element in its type (string, boolean, number) and is supported
//     * by the GIN index over the column.
//     */
//    private fun resolveTagSetContains(tagQuery: TagSetContains) {
//        val element = AnyList()
//        element.add(tagQuery.element)
//        val placeholder = placeholderForArg(toJSON(element), PgType.STRING)
//        where.append("$tagsAsJsonb @> $placeholder::jsonb")
//    }
//
//    private fun resolveTagNamesArrayOperation(jsonbOperator: String, tagNames: List<String>) {
//        val tagKeysArray = tagNames.toTypedArray()
//        val tagKeysPlaceholder = placeholderForArg(tagKeysArray, PgType.STRING_ARRAY)
//        where.append("$tagsAsJsonb ?$jsonbOperator $tagKeysPlaceholder")
//    }
//
//    private fun resolveSingleTagQuery(tagQuery: TagQuery) {
//        when (tagQuery) {
//            is TagMapHasKey -> {
//                val tagNamePlaceholder = placeholderForArg(tagQuery.name, PgType.STRING)
//                where.append("$tagsAsJsonb ?? $tagNamePlaceholder")
//            }
//
//            is TagValueIsNull -> {
//                val tagValuePlaceholder = placeholderForArg(selectTagValue(tagQuery), PgType.STRING)
//                where.append("$tagValuePlaceholder IS NULL")
//            }
//
//            is TagValueIsBool -> {
//                if (tagQuery.value) {
//                    where.append(selectTagValue(tagQuery, PgType.BOOLEAN))
//                } else {
//                    where.append("not(${selectTagValue(tagQuery, PgType.BOOLEAN)})")
//                }
//            }
//
//            is TagValueIsDouble -> {
//                val queryValuePlaceholder = placeholderForArg(tagQuery.value, PgType.DOUBLE)
//                val doubleOp = resolveDoubleOp(
//                    tagQuery.op,
//                    selectTagValue(tagQuery, PgType.DOUBLE),
//                    queryValuePlaceholder
//                )
//                where.append(doubleOp)
//            }
//
//            is TagValueIsString -> {
//                val queryValuePlaceholder = placeholderForArg(tagQuery.value, PgType.STRING)
//                val stringEquals = resolveStringOp(
//                    StringOp.EQUALS,
//                    selectTagValue(tagQuery, PgType.STRING),
//                    queryValuePlaceholder
//                )
//                where.append(stringEquals)
//            }
//
//            is TagValueMatches -> {
//                val jsonPathPlaceholder = placeholderForArg(
//                    "\$.${tagQuery.name} ? (@ like_regex \"${tagQuery.regex}\")",
//                    PgType.STRING
//                )
//                where.append("$tagsAsJsonb @?? $jsonPathPlaceholder::jsonpath")
//            }
//        }
//    }
//
//    private fun selectTagValue(tagQuery: TagQuery, castTo: PgType? = null): String {
//        val tagKeyPlaceholder = placeholderForArg(tagQuery.name, PgType.STRING)
//        return when (castTo) {
//            null -> "$tagsAsJsonb->$tagKeyPlaceholder"
//            PgType.STRING -> "$tagsAsJsonb->>$tagKeyPlaceholder"
//            else -> "($tagsAsJsonb->$tagKeyPlaceholder)::${castTo.value}"
//        }
//    }
//
//    private fun <T : IQuery> not(subClause: T, subClauseResolver: (T) -> Unit) {
//        where.append(" NOT (")
//        subClauseResolver(subClause)
//        where.append(") ")
//    }
//
//    private fun <T : IQuery> and(subClauses: List<T>, subClauseResolver: (T) -> Unit) =
//        multiClause("AND", subClauses, subClauseResolver)
//
//    private fun <T : IQuery> or(subClauses: List<T>, subClauseResolver: (T) -> Unit) =
//        multiClause("OR", subClauses, subClauseResolver)
//
//    private fun <T : IQuery> multiClause(
//        operand: String,
//        subClauses: List<T>,
//        subClauseResolver: (T) -> Unit
//    ) {
//        where.append(" (")
//        subClauses.forEachIndexed { index, subClause ->
//            if (index > 0) {
//                where.append(" $operand ")
//            }
//            subClauseResolver(subClause)
//        }
//        where.append(") ")
//    }
//
//
//    private fun resolveStringOp(
//        stringOp: StringOp,
//        leftOperand: String,
//        rightOperand: String
//    ): String {
//        return when (stringOp) {
//            StringOp.EQUALS -> "$leftOperand = $rightOperand"
//            StringOp.NOT_EQUALS -> "$leftOperand != $rightOperand"
//            StringOp.STARTS_WITH -> "starts_with($leftOperand, $rightOperand)"
//            else -> throw NakshaException(
//                NakshaError.ILLEGAL_ARGUMENT,
//                "Unknown StringOp: $stringOp"
//            )
//        }
//    }
//
//    private fun resolveDoubleOp(
//        doubleOp: DoubleOp,
//        leftOperand: String,
//        rightOperand: String
//    ): String {
//        return when (doubleOp) {
//            DoubleOp.EQ -> "$leftOperand = $rightOperand"
//            DoubleOp.NE -> "$leftOperand != $rightOperand"
//            DoubleOp.GT -> "$leftOperand > $rightOperand"
//            DoubleOp.GTE -> "$leftOperand >= $rightOperand"
//            DoubleOp.LT -> "$leftOperand < $rightOperand"
//            DoubleOp.LTE -> "$leftOperand <= $rightOperand"
//            else -> throw NakshaException(
//                NakshaError.ILLEGAL_ARGUMENT,
//                "Unknown DoubleOp: $doubleOp"
//            )
//        }
//    }
}
