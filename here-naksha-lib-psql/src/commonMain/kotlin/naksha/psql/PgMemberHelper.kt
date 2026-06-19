@file:Suppress("OPT_IN_USAGE")

package naksha.psql

import naksha.base.AnyList
import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.ListProxy
import naksha.base.PlatformList
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.model.TagList
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature

// TODO: This is AI generated slop, we need to review and only use what we really need!
//       What is really used should go into static Member methods!

/**
 * Helpers to map [CustomMember] values from a [NakshaFeature] into a [PgRows] row.
 *
 * - [walkFeature]: descend a [NakshaFeature] using the member's path; returns _null_ if the path is missing.
 * - [coerce]: coerce a raw value to the type of the member; returns _null_ and logs a warning on mismatch.
 * - [pgTypeFor]: maps a [CustomMemberType] to the [PgType] used for prepared-statement binding.
 * - [pgSqlTypeFor]: returns the PostgreSQL DDL type for `CREATE TABLE` / `ALTER TABLE ADD COLUMN`.
 * - [pgColumnName]: returns the physical column name (same as [CustomMember.name]) used in Postgres.
 */
class PgMemberHelper private constructor() {

    companion object PgMemberHelper_C {
        /**
         * Returns the physical Postgres column name for the given member name.
         * The name is used as-is; collision with built-in columns is prevented by [validateMemberNames].
         */
        fun pgColumnName(memberName: String): String = memberName

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
            MemberType.TAGS -> PgType.JSONB
            MemberType.TAGS_FROM_ARRAY -> PgType.JSONB
            MemberType.SET -> PgType.JSONB
            else -> PgType.STRING
        }

        /**
         * Returns the PostgreSQL DDL type string for the given member type, used inside `CREATE TABLE` / `ALTER TABLE ADD COLUMN`.
         * Note: there is no 1-byte signed integer type in PostgreSQL, so [MemberType.INT8] is materialized as `smallint`;
         * the storage enforces the 8-bit range on coercion.
         * [MemberType.TAGS], [MemberType.TAGS_FROM_ARRAY], and [MemberType.SET] all use `jsonb STORAGE MAIN` —
         * compressed inline, only TOASTed as a last resort. The only difference is the JSON shape:
         * TAGS and TAGS_FROM_ARRAY persist a JSON object, SET persists a JSON array.
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
            MemberType.TAGS -> "jsonb STORAGE MAIN"
            MemberType.TAGS_FROM_ARRAY -> "jsonb STORAGE MAIN"
            MemberType.SET -> "jsonb STORAGE MAIN"
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
                MemberType.TAGS -> coerceTags(value, featureId, memberName)
                MemberType.TAGS_FROM_ARRAY -> coerceTagsFromArray(value, featureId, memberName)
                MemberType.SET -> coerceSet(value, featureId, memberName)
                else -> {
                    warnMismatch(featureId, memberName, type.toString(), value)
                    null
                }
            }
        }

        private fun coerceBoolean(value: Any, featureId: String, memberName: String): Boolean? = when (value) {
            is Boolean -> value
            else -> {
                warnMismatch(featureId, memberName, "boolean", value); null
            }
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
            is Double -> if (value.isFinite() && value == value.toLong().toDouble()) Int64(value.toLong()) else {
                warnMismatch(featureId, memberName, "int64", value); null
            }

            is Float -> if (value.isFinite() && value == value.toLong().toFloat()) Int64(value.toLong()) else {
                warnMismatch(featureId, memberName, "int64", value); null
            }

            else -> {
                warnMismatch(featureId, memberName, "int64", value); null
            }
        }

        private fun coerceFloat32(value: Any, featureId: String, memberName: String): Float? = when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Int64 -> value.toLong().toFloat()
            is Short -> value.toFloat()
            is Byte -> value.toFloat()
            else -> {
                warnMismatch(featureId, memberName, "float32", value); null
            }
        }

        private fun coerceFloat64(value: Any, featureId: String, memberName: String): Double? = when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Int64 -> value.toLong().toDouble()
            is Short -> value.toDouble()
            is Byte -> value.toDouble()
            else -> {
                warnMismatch(featureId, memberName, "float64", value); null
            }
        }

        private fun coerceString(value: Any, featureId: String, memberName: String): String? = when (value) {
            is String -> value
            else -> {
                warnMismatch(featureId, memberName, "string", value); null
            }
        }

        private fun coerceByteArray(value: Any, featureId: String, memberName: String): ByteArray? = when (value) {
            is ByteArray -> value
            else -> {
                warnMismatch(featureId, memberName, "byte_array", value); null
            }
        }

        private fun coerceTags(value: Any, featureId: String, memberName: String): String? {
            if (value !is AnyObject) {
                warnMismatch(featureId, memberName, "tags", value)
                return null
            }
            return try {
                toJSON(value)
            } catch (_: Exception) {
                warnMismatch(featureId, memberName, "tags", value); null
            }
        }

        private fun coerceTagsFromArray(value: Any, featureId: String, memberName: String): String? {
            val tagList = when (value) {
                is TagList -> value
                is AnyObject -> value.proxy(TagList::class)
                else -> {
                    warnMismatch(featureId, memberName, "tags_from_array", value); return null
                }
            }
            val tagMap = tagList.toTagMap()
            return try {
                toJSON(tagMap)
            } catch (_: Exception) {
                warnMismatch(featureId, memberName, "tags_from_array", value); null
            }
        }

        /**
         * Coerces a [MemberType.SET] value: a JSON array of unique primitives (booleans, numbers, strings).
         * The array is persisted unmodified (element order preserved). Entries that are `null`, non-primitive,
         * or duplicates violate the set contract; the value is then not materialized (warning + NULL column).
         */
        private fun coerceSet(value: Any, featureId: String, memberName: String): String? {
            val list: ListProxy<*> = when (value) {
                is ListProxy<*> -> value
                is PlatformList -> value.proxy(AnyList::class)
                else -> {
                    warnMismatch(featureId, memberName, "set", value); return null
                }
            }
            val seen = HashSet<Any>()
            for (e in list) {
                if (e == null) {
                    warnMismatch(featureId, memberName, "set (entries must not be null)", "null")
                    return null
                }
                if (!isPrimitive(e)) {
                    warnMismatch(featureId, memberName, "set (entries must be primitives)", e)
                    return null
                }
                if (!seen.add(e)) {
                    warnMismatch(featureId, memberName, "set (entries must be unique)", e)
                    return null
                }
            }
            return try {
                toJSON(list)
            } catch (_: Exception) {
                warnMismatch(featureId, memberName, "set", list); null
            }
        }

        private fun isPrimitive(value: Any): Boolean = when (value) {
            is String, is Boolean, is Byte, is Short, is Int, is Long, is Int64, is Float, is Double -> true
            else -> false
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
         * 5. Opaque variable-length ([BYTE_ARRAY], [SPATIAL], [TAGS], [TAGS_FROM_ARRAY], [SET]) — last
         */
        fun columnSortOrder(type: MemberType): Int = when (type) {
            MemberType.INT64 -> 0
            MemberType.FLOAT64 -> 1
            MemberType.INT32 -> 2
            MemberType.FLOAT32 -> 3
            MemberType.INT16 -> 4
            MemberType.INT8 -> 5
            MemberType.BOOLEAN -> 6
            MemberType.STRING -> 7
            MemberType.BYTE_ARRAY -> 8
            MemberType.SPATIAL -> 8
            MemberType.TAGS -> 9
            MemberType.TAGS_FROM_ARRAY -> 10
            MemberType.SET -> 11
            else -> 12
        }

        private fun warnMismatch(featureId: String, memberName: String, expected: String, value: Any) {
            logger.warn("Custom member '$memberName' on feature '$featureId': expected $expected, got ${value::class.simpleName}")
        }
    }
}
