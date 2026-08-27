package naksha.psql

import naksha.base.AnyList
import naksha.base.Int64
import naksha.base.StringList
import naksha.model.Naksha
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.geo.SpGeometry
import naksha.model.Naksha.NakshaCompanion.featureNumberAsLong
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import naksha.model.objects.StandardMembers.StandardMembers_C.Action
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureNumber
import naksha.model.objects.StandardMembers.StandardMembers_C.FeatureVersion
import naksha.model.objects.StandardMembers.StandardMembers_C.Id
import naksha.model.objects.StandardMembers.StandardMembers_C.Tn
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthorTimestamp
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzCreatedAt
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzUpdatedAt
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.*
import naksha.psql.PgColumn.PgColumn_C.FnColumn
import naksha.psql.PgColumn.PgColumn_C.VersionColumn
import naksha.psql.PgType.Companion.NULL

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
    /**
     * The WHERE clause.
     * @since 3.0
     */
    val where = StringBuilder()

    /**
     * The values of the arguments in order, the entry at index `0` matches the argument `$1`.
     * @since 3.0
     */
    val argValues: MutableList<Any?> = mutableListOf()

    /**
     * The PostgresQL data types of the arguments in order, the entry at index `0` matches the argument `$1`.
     * @since 3.0
     */
    val argTypes: MutableList<PgType> = mutableListOf()

    /**
     * Convert the request into `WHERE` queries.
     * @return the [PgQueryWhereClause]; `null` if basically everything should be read.
     * @since 3.0
     */
    fun build(): PgQueryWhereClause? {
        var op: Op? = request.queryMembers
        if (op == null) {
            // When no `queryMembers` is given, we apply backward compatibility.
            op = QueryConverter.convert(request.query)
            if (op == null && request.featureIds.isEmpty() && request.guids.isEmpty()) return null
            if (request.featureIds.isNotEmpty()) whereFeatureId()
            if (request.guids.isNotEmpty()) whereGuids()
            if (op != null) applyOp(op)
        } else {
            // ReadFeatures clearly states that you have to use either the new syntax or the old, not mix them!
            // Note: We want to get people to switch to new syntax, not start improving old queries with new features!
            if (request.query.isNotEmpty())
                throw illegalArg("Old 'query' option must not be combined with new 'queryMember'")
            if (request.featureIds.isNotEmpty())
                throw illegalArg("Old 'featureIds' option must not be combined with new 'queryMember'")
            if (request.guids.isNotEmpty())
                throw illegalArg("Old 'guids' option must not be combined with new 'queryMember'")
            applyOp(op)
        }
        if (where.isEmpty()) return null
        return PgQueryWhereClause(collection, where.toString(), argValues, argTypes)
    }

    /**
     * Returns the column name of the given member or the extracted value for virtual members.
     *
     * **Does not work for `id`, because the value is needed special hack is needed!**
     **/
    private fun memberColumn(memberName: String): String = when (memberName) {
        Tn.name -> throw illegalArg("Query at tuple-number (Tn) member is not supported by lib-psql")
        Action.name -> "((${VersionColumn.name} & 3)::int4)"
        XyzCreatedAt.name, XyzAuthorTimestamp.name -> "COALESCE($memberName, ${XyzUpdatedAt.name})"
        else -> memberName
    }

    /**
     * Returns the given value, if it is a valid value matching the type of the member.
     *
     * The method treats a `null` value as always valid, it is up to the caller to decide if `null` is acceptable in the concrete situation.
     * @param memberName the name of the member.
     * @param opName the name of the operation to perform.
     * @param value the value to test.
     * @param memberType the data-type of the member, which much match that of the given value.
     * @return the real column name to query and the real valid value to query against.
     * @throws NakshaException if the given value is not of the desired member-type.
     */
    private fun columnAndValue(memberName: String, opName: String, value: Any?, memberType: MemberType): Pair<String, Any?> {
        if (value != null && !memberType.isInstance(value)) {
            throw illegalArg("The value for '$memberName' $opName '$value' is not the correct type, excepted: $memberType")
        }
        return if (memberName == Id.name) {
            val numeric = featureNumberAsLong(value as String)
            // If `id` is a string, but a positive integer, the `id` column will be NULL.
            // Therefore, in that case we need to search for the intger in the feature-number (fn) column!
            if (numeric >= 0) Pair(FeatureNumber.name, numeric) else Pair(Id.name, value)
        } else Pair(memberColumn(memberName), value)
    }

    /**
     * Update [where], [argValues] and [argTypes] from the given operation(s).
     * @since 3.0
     */
    private fun applyOp(rawOp: Op, negate: Boolean = false) {
        val opName = rawOp.getRaw("op") as? String? ?: throw illegalArg("Unknown operation, 'op' property is no string")
        val op = Op.detect(rawOp)
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
        val memberName: String = op.at ?: throw illegalArg("Missing 'at' (member name) for operation '$opName'")
        val memberType = collection.column(memberName)?.memberType ?: when (memberName) {
            Action.name -> MemberType.INT32
            FeatureVersion.name -> MemberType.INT64
            FeatureNumber.name -> MemberType.INT64
            Tn.name -> throw illegalArg("PgQueryWhereBuilder: The tuple-number can't be queried in lib-psql")
            else -> throw illegalArg("PgQueryWhereBuilder: Unknown member '$memberName' in collection '${collection.id}'")
        }
        when (op) {
            is IsNull -> _IsNull(negate, memberName, opName, memberType)
            is IsTrue -> _IsTrue(negate, memberName, opName, memberType)
            is IsFalse -> _IsFalse(negate, memberName, opName, memberType)
            is Equals -> _Equals(negate, memberName, opName, op.value, memberType)
            is Gt -> _Gt(negate, memberName, opName, op.value, memberType)
            is Gte -> _Gte(negate, memberName, opName, op.value, memberType)
            is Lt -> _Lt(negate, memberName, opName, op.value, memberType)
            is Lte -> _Lte(negate, memberName, opName, op.value, memberType)
            is StartsWith -> _StartsWith(negate, memberName, opName, op.value, memberType)
            is IsAnyOf -> _IsAnyOf(negate, memberName, opName, op.items, memberType)
            is TagMapHasKey -> _TagMapHasKey(negate, memberName, op.key)
            is TagMapHasAnyOf -> _TagMapHasAnyOf(negate, memberName, op.tagKeys)
            is TagMapHasAllOf -> _TagMapHasAllOf(negate, memberName, op.tagKeys)
            is TagIsNull -> _TagIsNull(negate, memberName, op.key)
            is TagEquals -> _TagEquals(negate, memberName, op.key, op.value)
            is TagGt -> _TagGt(negate, memberName, op.key, op.value)
            is TagGte -> _TagGte(negate, memberName, op.key, op.value)
            is TagLt -> _TagLt(negate, memberName, op.key, op.value)
            is TagLte -> _TagLte(negate, memberName, op.key, op.value)
            is TagStartsWith -> _TagStartsWith(negate, memberName, op.key, op.value)
            is TagMatches -> _TagMatches(negate, memberName, op.key, op.regex)
            is TagListContains -> _TagListContains(negate, memberName, op.item)
            is TagListContainsAllOf -> _TagListContainsAllOf(negate, memberName, op.items)
            is TagListContainsAnyOf -> _TagListContainsAnyOf(negate, memberName, op.items)
            is Intersects -> _Intersects(negate, memberName, op.value, op.transformers)
            else -> throw illegalArg("PgQueryWhereBuilder: Unknown operation: '$op' at '$memberName' in collection '${collection.id}'")
        }
    }

    private fun _IsNull(negate: Boolean, memberName: String, opName: String, memberType: MemberType) {
        val (at, _) = columnAndValue(memberName, opName, null, memberType)
        if (negate)
            where.append(at).append(" IS NOT NULL").append(' ')
        else
            where.append(at).append(" IS NULL").append(' ')
    }

    private fun _IsTrue(negate: Boolean, memberName: String, opName: String, memberType: MemberType) {
        val (at, _) = columnAndValue(memberName, opName, null, memberType)
        if (negate)
            where.append(at).append('=').append(placeholderForArg(false, PgType.BOOLEAN)).append(' ')
        else
            where.append(at).append('=').append(placeholderForArg(true, PgType.BOOLEAN)).append(' ')
    }

    private fun _IsFalse(negate: Boolean, memberName: String, opName: String, memberType: MemberType) {
        val (at, _) = columnAndValue(memberName, opName, null, memberType)
        if (negate)
            where.append(at).append('=').append(placeholderForArg(true, PgType.BOOLEAN)).append(' ')
        else
            where.append(at).append('=').append(placeholderForArg(false, PgType.BOOLEAN)).append(' ')
    }

    private fun _Equals(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (value == null) {
            if (negate) where.append(at).append(" IS NOT NULL ") else where.append(at).append(" IS NULL ")
        } else {
            val placeholder = placeholderForArg(value, at)
            if (negate)
                where.append(at).append("!=").append(placeholder).append(' ')
            else
                where.append(at).append('=').append(placeholder).append(' ')
        }
    }

    private fun _Gt(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (negate) // NOT Greater Than
            where.append(at).append("<=").append(placeholderForArg(value, at)).append(' ')
        else // Greater Than
            where.append(at).append(">").append(placeholderForArg(value, at)).append(' ')
    }

    private fun _Gte(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (negate) // NOT Greater Than or Equal to
            where.append(at).append("<").append(placeholderForArg(value, at)).append(' ')
        else // Greater Than or Equal to
            where.append(at).append(">=").append(placeholderForArg(value, at)).append(' ')
    }

    private fun _Lt(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (negate) // NOT Less Than
            where.append(at).append(">=").append(placeholderForArg(value, at)).append(' ')
        else // Less Than
            where.append(at).append("<").append(placeholderForArg(value, at)).append(' ')
    }

    private fun _Lte(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (negate) // NOT Less Than or Equal to
            where.append(at).append(">").append(placeholderForArg(value, at)).append(' ')
        else // Less Than or Equal to
            where.append(at).append("<=").append(placeholderForArg(value, at)).append(' ')
    }

    private fun _StartsWith(negate: Boolean, memberName: String, opName: String, opValue: Any?, memberType: MemberType) {
        if (memberType != MemberType.STRING) throw illegalArg("StartsWith: The operation can only target String members")
        val (at, value) = columnAndValue(memberName, opName, opValue, memberType)
        if (value is Number) {
            // TODO: This happens for `id`.
            //       We store identifiers as fature-number, when they are positive numbers.
            //       As we in these cases have `id` being `null`, we can't really test for starts with.
            //       Any solution, apart from full-table-scan, is prefered!
            // Solution:
            //   WHERE id = 123
            //      OR id BETWEEN 1230  AND 1239
            //      OR id BETWEEN 12300 AND 12399
            //      -- continue up to bigint's 19-digit limit
            throw unsupportedOp("StartsWith: The operation is currently not implemented for numeric identifiers")
        }
        if (negate) where.append("NOT ")
        where.append("starts_with(").append(at).append(", ").append(placeholderForArg(value, at)).append(") ")
    }

    private fun _IsAnyOf(negate: Boolean, memberName: String, opName: String, items: AnyList, memberType: MemberType) {
        if (items.isEmpty()) return

        // For `id` we can have a mixture of strings and numbers.
        // We simply ignore invalid values being in the array.
        if (memberName == Id.name) {
            val fn_array = LongArray(items.size)
            var fn_end = 0
            val id_array = arrayOfNulls<String>(items.size)
            var id_end = 0
            for (item in items) {
                if (item !is String) continue
                val numeric = featureNumberAsLong(item)
                if (numeric >= 0L) {
                    fn_array[fn_end++] = numeric
                } else {
                    id_array[id_end++] = item
                }
            }
            if (negate) where.append("NOT ")
            where.append('(')
            if (fn_end > 0) {
                val placeholder = if (fn_array.size == fn_end) placeholderForArg(fn_array, PgType.INT64_ARRAY)
                                else placeholderForArg(fn_array.copyOf(fn_end), PgType.INT64_ARRAY)
                where.append(FeatureNumber.name).append("= ANY(").append(placeholder).append(")")
            }
            if (id_end > 0) {
                if (fn_end > 0) where.append(" OR ")
                val placeholder = if (id_array.size == id_end) placeholderForArg(id_array, PgType.STRING_ARRAY)
                else placeholderForArg(id_array.copyOf(id_end), PgType.STRING_ARRAY)
                where.append(Id.name).append("= ANY(").append(placeholder).append(")")
            }
            where.append(") ")
            return
        }

        // For all other members the values are uniform and only one column is queried.
        val at = memberColumn(memberName)
        val (values, arrayType) = when(memberType) {
            MemberType.INT8,
            MemberType.INT16,
            MemberType.INT32 -> Pair(items.toIntArray(true), PgType.INT_ARRAY)
            MemberType.INT64 -> Pair(items.toLongArray(true), PgType.INT64_ARRAY)
            MemberType.FLOAT32 -> Pair(items.toFloatArray(true), PgType.FLOAT_ARRAY)
            MemberType.FLOAT64 -> Pair(items.toDoubleArray(true), PgType.DOUBLE_ARRAY)
            MemberType.STRING -> Pair(items.toStringArray(true), PgType.STRING_ARRAY)
            MemberType.BYTE_ARRAY -> Pair(items.toByteArrayArray(true), PgType.BYTE_ARRAY_ARRAY)
            else -> throw illegalArg("The member '$memberName' can't be used for $opName")
        }
        val placeholder = placeholderForArg(values, arrayType)
        if (negate) where.append("NOT ")
        where.append(at).append("= ANY(").append(placeholder).append(") ")
    }

    private fun _TagMapHasKey(negate: Boolean, memberName: String, key: String) {
        val _key_ = placeholderForArg(key, PgType.STRING)
        // jsonb ? text → boolean
        //   Does the text string exist as a top-level key or array element within the JSON value?
        //   '{"a":1, "b":2}'::jsonb ? 'b' → t
        //   '["a", "b", "c"]'::jsonb ? 'b' → t
        if (negate) where.append("NOT ")
        where.append(memberName).append(" ?? ").append(_key_).append(" ")
    }

    private fun _TagMapHasAnyOf(negate: Boolean, memberName: String, keys: StringList) {
        val keys_array = keys.proxy(AnyList::class).toStringArray(true)
        if (keys_array.isEmpty()) return
        val _keys_array_ = placeholderForArg(keys_array, PgType.STRING_ARRAY)
        // jsonb ?| text[] → boolean
        //   Do any of the strings in the text array exist as top-level keys or array elements?
        //   '{"a":1, "b":2, "c":3}'::jsonb ?| array['b', 'd'] → t
        if (negate) where.append("NOT ")
        where.append(memberName).append(" ??| ").append(_keys_array_).append(" ")
    }

    private fun _TagMapHasAllOf(negate: Boolean, memberName: String, keys: StringList) {
        val keys_array = keys.proxy(AnyList::class).toStringArray(true)
        if (keys_array.isEmpty()) return
        val _keys_array_ = placeholderForArg(keys_array, PgType.STRING_ARRAY)
        // jsonb ?& text[] → boolean
        //   Do all of the strings in the text array exist as top-level keys or array elements?
        //   '["a", "b", "c"]'::jsonb ?& array['a', 'b'] → t
        if (negate) where.append("NOT ")
        where.append(memberName).append(" ??& ").append(_keys_array_).append(" ")
    }

    private fun _TagIsNull(negate: Boolean, memberName: String, key: String) {
        val _key_ = placeholderForArg(key, PgType.STRING)
        // jsonb @> jsonb → boolean
        //   Does the first JSON value contain the second?
        //   SELECT '{"a":1, "b":null}'::jsonb @> jsonb_build_object('b', null);
        if (negate) where.append("NOT ")
        where.append(memberName).append(" @> jsonb_build_object(").append(_key_).append(",null) ")
    }

    private fun _TagEquals(negate: Boolean, memberName: String, key: String, value: Any?) {
        val pg_value_type = PgType.ofValue(value)
            ?: throw illegalArg("TagEquals: The given value is invalid: '$value'")
        if (pg_value_type == NULL) {
            _TagIsNull(negate, memberName, key)
            return
        }
        val pg_value = pg_value_type.convertValue(value)
        val _key_ = placeholderForArg(key, PgType.STRING)
        val _value_ = placeholderForArg(pg_value, pg_value_type)// jsonb @> jsonb → boolean
        // jsonb @> jsonb → boolean
        //   Does the first JSON value contain the second?
        //   SELECT '{"a":1, "b":null}'::jsonb @> jsonb_build_object('a', 1);
        if (negate) where.append("NOT ")
        where.append(memberName).append(" @> jsonb_build_object(").append(_key_).append(',').append(_value_).append(") ")
    }

    private fun _TagGt(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is Number && value !is Int64) {
            throw illegalArg("TagGt: The given value is number: '$value'")
        }
        val expression = if (negate) "@ <= $value" else "@ > $value"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagGte(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is Number && value !is Int64) {
            throw illegalArg("TagGte: The given value is number: '$value'")
        }
        val expression = if (negate) "@ < $value" else "@ >= $value"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagLt(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is Number && value !is Int64) {
            throw illegalArg("TagLt: The given value is no number: '$value'")
        }
        val expression = if (negate) "@ >= $value" else "@ < $value"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagLte(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is Number && value !is Int64) {
            throw illegalArg("The given value is no valid tag value (number expected): $value")
        }
        val expression = if (negate) "@ > $value" else "@ <= $value"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagStartsWith(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is String) {
            throw illegalArg("TagStartsWith: The given value is no string: '$value'")
        }
        if (negate) where.append("NOT ")
        val expression = "@ starts with ${quoteString(value)}"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagMatches(negate: Boolean, memberName: String, key: String, value: Any?) {
        if (value !is String) {
            throw illegalArg("TagMatches: The given value is no string: '$value'")
        }
        if (negate) where.append("NOT ")
        val expression = "@ like_regex ${quoteString(value)}"
        evaluateJsonPath(memberName, key, expression)
    }

    private fun _TagListContains(negate: Boolean, memberName: String, item: Any?) {
        val data = Array(1) {
            item as? String? ?: throw illegalArg("TagListContains: The value is no string: '$item'")
        }
        val _value_ = placeholderForArg(data, PgType.STRING_ARRAY)
        // @> —— Contains all specified elements
        // When we only provide a single one, it effectively means `contains` a single one.
        if (negate) where.append("NOT ")
        where.append("(").append(memberName).append(" @> ").append(_value_).append("::text[]) ")
    }

    private fun _TagListContainsAllOf(negate: Boolean, memberName: String, items: Any?) {
        if (items !is List<*>) {
            throw illegalArg("TagListContainsAllOf: The items must be a list of strings")
        }
        val data = Array(items.size) {
            items[it] as? String? ?: throw illegalArg("TagListContainsAllOf: The item[$it] is no string")
        }
        val _value_ = placeholderForArg(data, PgType.STRING_ARRAY)
        // @> —— Contains all specified elements
        if (negate) where.append("NOT ")
        where.append("(").append(memberName).append(" @> ").append(_value_).append("::text[]) ")
    }

    private fun _TagListContainsAnyOf(negate: Boolean, memberName: String, items: Any?) {
        if (items !is List<*>) {
            throw illegalArg("TagListContainsAnyOf: The items must be a list of strings")
        }
        val data = Array(items.size) {
            items[it] as? String? ?: throw illegalArg("TagListContainsAnyOf: The item[$it] is no string")
        }
        val _value_ = placeholderForArg(data, PgType.STRING_ARRAY)
        // && —— Contains any specified elements
        if (negate) where.append("NOT ")
        where.append("(").append(memberName).append(" && ").append(_value_).append("::text[]) ")
    }

    private fun _Intersects(negate: Boolean, memberName: String, geometry: SpGeometry, transformers: SpTransformationList) {
        val geoBytes = Naksha.encodeGeometry(geometry)
        val geoBytesPlaceholder = placeholderForArg(geoBytes, PgType.BYTE_ARRAY)
        val basicGeometry = "naksha_2d($geoBytesPlaceholder)"
        val geometryToCompare = if (transformers.isEmpty()) basicGeometry
                                else resolveTransformation(transformers, basicGeometry)
        if (negate) where.append("NOT ")
        where.append("ST_Intersects(naksha_2d($memberName), $geometryToCompare)")
    }

    /**
     * Evaluates the given `jsonpath` expression against the given key of the given `JSONB` column.
     *
     * A simplified general syntax for a `jsonpath` evaluations is:
     * - `$.key ? (@ op value)`
     * - `$` = A variable representing the JSON value being queried (the context item).
     * - `.key` = Member accessor that returns an object member with the specified key.
     * - `? (...)` = The expression to evaluate.
     * - `@` = A variable representing the result of path evaluation in filter expressions (value of key).
     *
     * What need to be supplied to this method is, next to the name of the `JSONB` column, the `key` to evaluate and the expression. This is used to search in tag-map for some value.
     *
     * The `jsonpath` is explained in the [PostgresQL jsonpath documentation](https://www.postgresql.org/docs/18/datatype-json.html#DATATYPE-JSONPATH). The operators within a `jsonpath` expression is explained at a different part of the [PostgresQL functions-json documentation](https://www.postgresql.org/docs/18/functions-json.html#FUNCTIONS-SQLJSON-PATH-OPERATORS).
     * @param memberName the name of the `JSONB` column.
     * @param key the key to select and against which's value the [jsonpath operators](https://www.postgresql.org/docs/18/functions-json.html#FUNCTIONS-SQLJSON-PATH-OPERATORS) or [jsonpath filter](https://www.postgresql.org/docs/18/functions-json.html#FUNCTIONS-SQLJSON-FILTER-EX-TABLE) should be evaluated.
     * @param expression the `jsonpath` expression to be evaluated, for example `@ >= 5`.
     * @since 3.0
     */
    private fun evaluateJsonPath(memberName: String, key: String, expression: String) {
        val _jsonpath_ = placeholderForArg(
            "$.${quoteString(key)} ?? ($expression)",
            PgType.STRING
        )
        // jsonb @? jsonpath → boolean
        //   Does JSON path return any item for the specified JSON value?
        //   '{"a":5}'::jsonb @? '$.a ? (@ > 2)' → t
        //   '{"a":[1,2,3,4,5]}'::jsonb @? '$.a[*] ? (@ > 2)' → t
        where.append(memberName).append(" @?? ").append(_jsonpath_)
    }

    /**
     * SQL-Quote the given `string`, for example:
     * - `Hello "Joe" ~World`
     * - Becomes: `"Hello \"Joe\" ~World"`.
     * @param string the key to escape.
     * @return the escaped string.
     * @since 3.0
     */
    fun quoteString(string: String): String = buildString(string.length + 10) {
        append('"')
        for (c in string) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                }
                else -> append(c)
            }
        }
        append('"')
    }

    /**
     * Returns a placeholder string like `$1` for the given value, and add the values and its type into the value and type arrays.
     * @param value the value.
     * @param at the name of the column/member for error messages.
     * @throws NakshaException with error [ILLEGAL_ARGUMENT][NakshaError.ILLEGAL_ARGUMENT] if the given value is no supported valid type or `null`.
     * @since 3.0
     */
    private fun placeholderForArg(value: Any?, at: String): String {
        val type: PgType = when (value) {
            is String -> PgType.STRING
            is Boolean -> PgType.BOOLEAN
            is Short -> PgType.SHORT
            is Int -> PgType.INT
            is Long -> PgType.INT64
            is Int64 -> PgType.INT64
            is Float -> PgType.FLOAT
            is Double -> PgType.DOUBLE
            is ByteArray -> PgType.BYTE_ARRAY
            else -> throw illegalArg("The value for member '$at' is invalid: '$value'")
        };
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

    private fun resolveTransformation(transformers: SpTransformationList, basicGeometry: String): String {
        var geometry = basicGeometry
        for (transformation in transformers) {
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

    // TODO: Do we need this, and if so, how do we integrate into changed code?
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
            where.append("($FnColumn, $VersionColumn) IN (SELECT * FROM unnest($featureNumbersArg::int8[], $versionsArg::int8[]))")
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
