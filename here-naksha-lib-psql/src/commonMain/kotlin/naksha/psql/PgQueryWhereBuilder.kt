package naksha.psql

import naksha.model.*
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.*

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
        if (op == null) op = PgQueryConverter.convert(request.query)
        // TODO: Convert `featureIds`
        // TODO: Convert `guids`
        if (op == null) return null
        applyOp(op)
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
        val at: String = op.at ?: throw illegalArg("Missing member name for operation $opName")
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
                    if (negate)
                        where.append(at).append("!=").append(placeholderForArg(value)).append(' ')
                    else
                        where.append(at).append('=').append(placeholderForArg(value)).append(' ')
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
                where.append(at).append("= ANY(").append(placeholderForArg(op.items)).append(") ")
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
                // TODO: Implement me
            }
            is TagListContainsAllOf -> {
                // TODO: Implement me
            }
            is TagListContainsAnyOf -> {
                // TODO: Implement me
            }
            is Intersects -> {
                // TODO: Implement me
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
}
