@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.Platform
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.PlatformList
import naksha.base.PlatformListApi
import naksha.model.NakshaError
import naksha.model.NakshaException
import naksha.model.TagList
import naksha.model.TupleNumber
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature

/**
 * Helpers to map [CustomMember] values from a [NakshaFeature] into a [PgColumnRows] row.
 *
 * - [walkFeature]: descend a [NakshaFeature] using the member's path; returns _null_ if the path is missing.
 * - [coerce]: coerce a raw value to the type of the member; returns _null_ and logs a warning on mismatch.
 * - [pgTypeFor]: maps a [CustomMemberType] to the [PgType] used for prepared-statement binding.
 * - [pgSqlTypeFor]: returns the PostgreSQL DDL type for `CREATE TABLE` / `ALTER TABLE ADD COLUMN`.
 * - [pgColumnName]: returns the physical column name (same as [CustomMember.name]) used in Postgres.
 */
internal object PgCustomMemberValues {

    /**
     * The set of all reserved column names — any name that belongs to a built-in [PgColumn].
     * Custom members must not use any of these names; [validateMemberNames] enforces this.
     */
    private val reservedColumnNames: Set<String> by lazy {
        PgColumn.allColumns.map { it.name }.toSet()
    }

    /**
     * Returns the physical Postgres column name for the given member name.
     * The name is used as-is; collision with built-in columns is prevented by [validateMemberNames].
     */
    fun pgColumnName(memberName: String): String = memberName

    /**
     * Validates that none of the members in [members] use a reserved built-in column name.
     * Throws [NakshaException] with [NakshaError.ILLEGAL_ARGUMENT] on the first conflict found.
     * Must be called before creating a new collection.
     */
    fun validateMemberNames(members: MemberList) {
        for (member in members) {
            if (member != null && member.name in reservedColumnNames) {
                throw NakshaException(
                    NakshaError.ILLEGAL_ARGUMENT,
                    "Custom member name '${member.name}' conflicts with a built-in column name"
                )
            }
        }
    }

    fun pgTypeFor(type: MemberType): PgType = when (type) {
        MemberType.BOOLEAN -> PgType.BOOLEAN
        MemberType.INT8 -> PgType.SHORT
        MemberType.INT16 -> PgType.SHORT
        MemberType.INT32 -> PgType.INT
        MemberType.INT64 -> PgType.INT64
        MemberType.FLOAT32 -> PgType.FLOAT
        MemberType.FLOAT64 -> PgType.DOUBLE
        MemberType.STRING -> PgType.STRING
        MemberType.BYTE_ARRAY -> PgType.BYTE_ARRAY
        MemberType.SPATIAL -> PgType.BYTE_ARRAY
        MemberType.SET -> PgType.JSONB
        MemberType.TAGS -> PgType.JSONB
        MemberType.TAGS_FROM_ARRAY -> PgType.JSONB
        else -> PgType.STRING
    }

    /**
     * Returns the PostgreSQL DDL type string for the given member type, used inside `CREATE TABLE` / `ALTER TABLE ADD COLUMN`.
     * Note: there is no 1-byte signed integer type in PostgreSQL, so [MemberType.INT8] is materialized as `smallint`;
     * the storage enforces the 8-bit range on coercion.
     * [MemberType.SET], [MemberType.TAGS], and [MemberType.TAGS_FROM_ARRAY] all use `jsonb STORAGE MAIN`
     * — compressed inline, only TOASTed as a last resort. SET is stored as a `jsonb` array;
     * TAGS / TAGS_FROM_ARRAY as a `jsonb` object.
     */
    fun pgSqlTypeFor(type: MemberType): String = when (type) {
        MemberType.BOOLEAN -> "boolean"
        MemberType.INT8 -> "smallint"
        MemberType.INT16 -> "smallint"
        MemberType.INT32 -> "integer"
        MemberType.INT64 -> "bigint"
        MemberType.FLOAT32 -> "real"
        MemberType.FLOAT64 -> "double precision"
        MemberType.STRING -> "text COLLATE \"C\""
        MemberType.BYTE_ARRAY -> "bytea"
        MemberType.SPATIAL -> "bytea STORAGE EXTERNAL"
        MemberType.SET -> "jsonb STORAGE MAIN"
        MemberType.TAGS -> "jsonb STORAGE MAIN"
        MemberType.TAGS_FROM_ARRAY -> "jsonb STORAGE MAIN"
        else -> "text"
    }

    /**
     * Returns the SQL fragment for the [Member.name] / [Member.dataType] used in `CREATE TABLE`. Example: `"age" smallint`.
     */
    fun sqlDefinitionFor(member: Member): String =
        "\"${pgColumnName(member.name)}\" ${pgSqlTypeFor(member.dataType)}"

    fun walkFeature(feature: NakshaFeature, path: List<String>): Any? {
        var current: Any? = feature
        for (segment in path) {
            if (current == null) return null
            current = when (current) {
                is AnyObject -> current.getRaw(segment)
                else -> return null
            }
        }
        return current
    }

    fun coerce(value: Any?, type: MemberType, featureId: String, memberName: String): Any? {
        if (value == null) return null
        return when (type) {
            MemberType.BOOLEAN -> coerceBoolean(value, featureId, memberName)
            MemberType.INT8 -> coerceInt8(value, featureId, memberName)
            MemberType.INT16 -> coerceInt16(value, featureId, memberName)
            MemberType.INT32 -> coerceInt32(value, featureId, memberName)
            MemberType.INT64 -> coerceInt64(value, featureId, memberName)
            MemberType.FLOAT32 -> coerceFloat32(value, featureId, memberName)
            MemberType.FLOAT64 -> coerceFloat64(value, featureId, memberName)
            MemberType.STRING -> coerceString(value, featureId, memberName)
            MemberType.BYTE_ARRAY -> coerceByteArray(value, featureId, memberName)
            MemberType.SPATIAL -> coerceByteArray(value, featureId, memberName)
            MemberType.SET -> coerceSet(value, featureId, memberName)
            MemberType.TAGS -> coerceTags(value, featureId, memberName)
            MemberType.TAGS_FROM_ARRAY -> coerceTagsFromArray(value, featureId, memberName)
            else -> {
                warnMismatch(featureId, memberName, type.toString(), value)
                null
            }
        }
    }

    private fun coerceBoolean(value: Any, featureId: String, memberName: String): Boolean? = when (value) {
        is Boolean -> value
        else -> { warnMismatch(featureId, memberName, "boolean", value); null }
    }

    private fun coerceInt8(value: Any, featureId: String, memberName: String): Short? {
        val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "int8", value) }
        if (asLong !in Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()) {
            warnMismatch(featureId, memberName, "int8 (out of range)", value)
            return null
        }
        return asLong.toShort()
    }

    private fun coerceInt16(value: Any, featureId: String, memberName: String): Short? {
        val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "int16", value) }
        if (asLong !in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()) {
            warnMismatch(featureId, memberName, "int16 (out of range)", value)
            return null
        }
        return asLong.toShort()
    }

    private fun coerceInt32(value: Any, featureId: String, memberName: String): Int? {
        val asLong = numberToLongOrNull(value) ?: return null.also { warnMismatch(featureId, memberName, "int32", value) }
        if (asLong !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            warnMismatch(featureId, memberName, "int32 (out of range)", value)
            return null
        }
        return asLong.toInt()
    }

    private fun coerceInt64(value: Any, featureId: String, memberName: String): Int64? = when (value) {
        is Int64 -> value
        is Int -> Int64(value.toLong())
        is Long -> Int64(value)
        is Short -> Int64(value.toLong())
        is Byte -> Int64(value.toLong())
        is Double -> if (value.isFinite() && value == value.toLong().toDouble()) Int64(value.toLong()) else { warnMismatch(featureId, memberName, "int64", value); null }
        is Float -> if (value.isFinite() && value == value.toLong().toFloat()) Int64(value.toLong()) else { warnMismatch(featureId, memberName, "int64", value); null }
        else -> { warnMismatch(featureId, memberName, "int64", value); null }
    }

    private fun coerceFloat32(value: Any, featureId: String, memberName: String): Float? = when (value) {
        is Float -> value
        is Double -> value.toFloat()
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is Int64 -> value.toLong().toFloat()
        is Short -> value.toFloat()
        is Byte -> value.toFloat()
        else -> { warnMismatch(featureId, memberName, "float32", value); null }
    }

    private fun coerceFloat64(value: Any, featureId: String, memberName: String): Double? = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Int64 -> value.toLong().toDouble()
        is Short -> value.toDouble()
        is Byte -> value.toDouble()
        else -> { warnMismatch(featureId, memberName, "float64", value); null }
    }

    private fun coerceString(value: Any, featureId: String, memberName: String): String? = when (value) {
        is String -> value
        else -> { warnMismatch(featureId, memberName, "string", value); null }
    }

    private fun coerceByteArray(value: Any, featureId: String, memberName: String): ByteArray? = when (value) {
        is ByteArray -> value
        else -> { warnMismatch(featureId, memberName, "byte_array", value); null }
    }

    private fun coerceTags(value: Any, featureId: String, memberName: String): String? {
        if (value !is AnyObject) {
            warnMismatch(featureId, memberName, "tags", value)
            return null
        }
        return try { toJSON(value) } catch (_: Exception) { warnMismatch(featureId, memberName, "tags", value); null }
    }

    /**
     * Coerces a raw value into the JSON-array wire form of a [MemberType.SET] column.
     *
     * Accepts any iterable / list-shaped input ([naksha.model.TagList], [naksha.base.AnyList],
     * `List<*>`, `Array<*>`). Each entry must be a primitive — `String`, `Boolean`, `Number`,
     * [naksha.base.Int64], or [naksha.model.TupleNumber] (serialised via its canonical
     * `{sn}:{mn}:{cn}:{fn}:{v}` stringification). `null` entries are dropped. Duplicates are
     * dropped while preserving insertion order. Returns `null` if the input cannot be interpreted
     * as a list, contains a non-primitive entry, or the resulting set is empty.
     */
    private fun coerceSet(value: Any, featureId: String, memberName: String): String? {
        val rawEntries: List<Any?> = when (value) {
            is ListProxy<*> -> (0 until value.size).map { value[it] }
            is PlatformList -> {
                val size = PlatformListApi.array_get_length(value)
                (0 until size).map { PlatformListApi.array_get(value, it) }
            }
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> { warnMismatch(featureId, memberName, "set", value); return null }
        }
        val seen = HashSet<Any>(rawEntries.size)
        val unique = Platform.newList()
        for (e in rawEntries) {
            val normalized: Any = when (e) {
                null -> continue
                is TupleNumber -> e.toString()
                is String, is Boolean, is Number, is Int64 -> e
                else -> { warnMismatch(featureId, memberName, "set (non-primitive entry)", e); return null }
            }
            if (seen.add(normalized)) PlatformListApi.array_push(unique, normalized)
        }
        if (PlatformListApi.array_get_length(unique) == 0) return null
        return try { toJSON(unique) } catch (_: Exception) { warnMismatch(featureId, memberName, "set", value); null }
    }

    private fun coerceTagsFromArray(value: Any, featureId: String, memberName: String): String? {
        val tagList = when (value) {
            is TagList -> value
            is AnyObject -> value.proxy(TagList::class)
            else -> { warnMismatch(featureId, memberName, "tags_from_array", value); return null }
        }
        val tagMap = tagList.toTagMap()
        return try { toJSON(tagMap) } catch (_: Exception) { warnMismatch(featureId, memberName, "tags_from_array", value); null }
    }

    private fun numberToLongOrNull(value: Any): Long? = when (value) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        is Int64 -> value.toLong()
        is Float -> if (value.isFinite() && value == value.toLong().toFloat()) value.toLong() else null
        is Double -> if (value.isFinite() && value == value.toLong().toDouble()) value.toLong() else null
        else -> null
    }

    /**
     * Returns the sort priority for a [MemberType] based on PostgreSQL alignment size, to minimise
     * tuple padding when columns are laid out in declaration order:
     * 1. 8-byte types ([INT64], [FLOAT64]) — first, to get 8-byte alignment right away
     * 2. 4-byte types ([INT32], [FLOAT32]) — next, still fixed-width
     * 3. 1/2-byte types ([INT16], [INT8], [BOOLEAN]) — small fixed-width
     * 4. Variable-length text ([STRING]) — variable but human-readable
     * 5. Opaque variable-length ([BYTE_ARRAY], [SPATIAL], [SET], [TAGS], [TAGS_FROM_ARRAY]) — last
     */
    fun columnSortOrder(type: MemberType): Int = when (type) {
        MemberType.INT64   -> 0
        MemberType.FLOAT64 -> 1
        MemberType.INT32   -> 2
        MemberType.FLOAT32 -> 3
        MemberType.INT16   -> 4
        MemberType.INT8    -> 5
        MemberType.BOOLEAN -> 6
        MemberType.STRING  -> 7
        MemberType.BYTE_ARRAY      -> 8
        MemberType.SPATIAL         -> 8
        MemberType.SET             -> 9
        MemberType.TAGS            -> 9
        MemberType.TAGS_FROM_ARRAY -> 10
        else -> 11
    }

    /**
     * The set of member names that correspond to pre-defined optional [PgColumn]s (e.g. `geo`, `tags`, `cc`).
     * When two members have the same type sort-order, pre-defined members are placed before user-invented ones.
     */
    private val predefinedMemberNames: Set<String> by lazy {
        val mandatory = PgColumn.mandatoryColumns.map { it.name }.toSet()
        PgColumn.headColumns.map { it.name }.filter { it !in mandatory }.toSet()
    }

    /**
     * Sorts [members] in-place for optimal PostgreSQL column layout.
     *
     * Ordering rules (applied only at **collection-creation** time; never on updates):
     * 1. Primary: type alignment group ([columnSortOrder])
     * 2. Secondary: pre-defined members (matching a built-in optional column name) before user-invented ones
     * 3. Tertiary: member name lexicographically ascending
     *
     * The sort is stable within each group so that the caller's intent is preserved as a tie-breaker.
     */
    fun sortMembersForStorage(members: MemberList) {
        if (members.size <= 1) return
        val snapshot = (0 until members.size).map { members[it]!! }
        val sorted = snapshot.sortedWith(compareBy(
            { columnSortOrder(it.dataType) },
            { if (it.name in predefinedMemberNames) 0 else 1 },
            { it.name }
        ))
        members.clear()
        members.addAll(sorted)
    }

    private fun warnMismatch(featureId: String, memberName: String, expected: String, value: Any) {
        logger.warn("Custom member '$memberName' on feature '$featureId': expected $expected, got ${value::class.simpleName}")
    }
}
