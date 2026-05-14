@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.model.TagList
import naksha.model.objects.CustomMember
import naksha.model.objects.CustomMemberType
import naksha.model.objects.NakshaFeature

/**
 * Helpers to map [CustomMember] values from a [NakshaFeature] into a [PgColumnRows] row.
 *
 * - [walkFeature]: descend a [NakshaFeature] using the member's path; returns _null_ if the path is missing.
 * - [coerce]: coerce a raw value to the type of the member; returns _null_ and logs a warning on mismatch.
 * - [pgTypeFor]: maps a [CustomMemberType] to the [PgType] used for prepared-statement binding.
 * - [pgSqlTypeFor]: returns the PostgreSQL DDL type for `CREATE TABLE` / `ALTER TABLE ADD COLUMN`.
 * - [pgColumnName]: returns the physical column name (`$<member.name>`) used in Postgres.
 */
internal object PgCustomMemberValues {

    /**
     * Returns the physical Postgres column name for the given member name. Custom columns are namespaced with `$` so they cannot collide with built-in columns (whose names contain no `$`).
     */
    fun pgColumnName(memberName: String): String = "\$$memberName"

    fun pgTypeFor(type: CustomMemberType): PgType = when (type) {
        CustomMemberType.BOOLEAN -> PgType.BOOLEAN
        CustomMemberType.INT8 -> PgType.SHORT
        CustomMemberType.INT16 -> PgType.SHORT
        CustomMemberType.INT32 -> PgType.INT
        CustomMemberType.INT64 -> PgType.INT64
        CustomMemberType.FLOAT32 -> PgType.FLOAT
        CustomMemberType.FLOAT64 -> PgType.DOUBLE
        CustomMemberType.STRING -> PgType.STRING
        CustomMemberType.BYTE_ARRAY -> PgType.BYTE_ARRAY
        CustomMemberType.FLAT_MAP -> PgType.JSONB
        CustomMemberType.TAGS -> PgType.JSONB
        else -> PgType.STRING
    }

    /**
     * Returns the PostgreSQL DDL type string for the given member type, used inside `CREATE TABLE` / `ALTER TABLE ADD COLUMN`. Note: there is no 1-byte signed integer type in PostgreSQL, so [CustomMemberType.INT8] is materialized as `smallint`; the storage enforces the 8-bit range on coercion.
     */
    fun pgSqlTypeFor(type: CustomMemberType): String = when (type) {
        CustomMemberType.BOOLEAN -> "boolean"
        CustomMemberType.INT8 -> "smallint"
        CustomMemberType.INT16 -> "smallint"
        CustomMemberType.INT32 -> "integer"
        CustomMemberType.INT64 -> "bigint"
        CustomMemberType.FLOAT32 -> "real"
        CustomMemberType.FLOAT64 -> "double precision"
        CustomMemberType.STRING -> "text COLLATE \"C\""
        CustomMemberType.BYTE_ARRAY -> "bytea"
        CustomMemberType.FLAT_MAP -> "jsonb"
        CustomMemberType.TAGS -> "jsonb"
        else -> "text"
    }

    /**
     * Returns the comma-prefixed SQL fragment for the [CustomMember.name] / [CustomMember.dataType] used in `CREATE TABLE`. Example: `"$age" smallint`.
     */
    fun sqlDefinitionFor(member: CustomMember): String =
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

    fun coerce(value: Any?, type: CustomMemberType, featureId: String, memberName: String): Any? {
        if (value == null) return null
        return when (type) {
            CustomMemberType.BOOLEAN -> coerceBoolean(value, featureId, memberName)
            CustomMemberType.INT8 -> coerceInt8(value, featureId, memberName)
            CustomMemberType.INT16 -> coerceInt16(value, featureId, memberName)
            CustomMemberType.INT32 -> coerceInt32(value, featureId, memberName)
            CustomMemberType.INT64 -> coerceInt64(value, featureId, memberName)
            CustomMemberType.FLOAT32 -> coerceFloat32(value, featureId, memberName)
            CustomMemberType.FLOAT64 -> coerceFloat64(value, featureId, memberName)
            CustomMemberType.STRING -> coerceString(value, featureId, memberName)
            CustomMemberType.BYTE_ARRAY -> coerceByteArray(value, featureId, memberName)
            CustomMemberType.FLAT_MAP -> coerceFlatMap(value, featureId, memberName)
            CustomMemberType.TAGS -> coerceTags(value, featureId, memberName)
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

    private fun coerceFlatMap(value: Any, featureId: String, memberName: String): String? {
        if (value !is AnyObject) {
            warnMismatch(featureId, memberName, "flat_map", value)
            return null
        }
        return try { toJSON(value) } catch (_: Exception) { warnMismatch(featureId, memberName, "flat_map", value); null }
    }

    private fun coerceTags(value: Any, featureId: String, memberName: String): String? {
        val tagList = when (value) {
            is TagList -> value
            is AnyObject -> value.proxy(TagList::class)
            else -> { warnMismatch(featureId, memberName, "tags", value); return null }
        }
        val tagMap = tagList.toTagMap()
        return try { toJSON(tagMap) } catch (_: Exception) { warnMismatch(featureId, memberName, "tags", value); null }
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

    private fun warnMismatch(featureId: String, memberName: String, expected: String, value: Any) {
        logger.warn("Custom member '$memberName' on feature '$featureId': expected $expected, got ${value::class.simpleName}")
    }
}
