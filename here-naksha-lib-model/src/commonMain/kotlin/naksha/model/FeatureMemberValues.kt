@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.AnyObject
import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.geo.GeoUtil.GeoUtil_C.toTWKB
import naksha.geo.SpGeometry
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaFeature

/**
 * Helpers to walk a [NakshaFeature] by JSON path and coerce raw values to the expected [MemberType].
 *
 * - [walkFeature]: descend a [NakshaFeature] using the member's path; returns _null_ if the path is missing.
 * - [coerce]: coerce a raw value to the type of the member; returns _null_ and logs a warning on mismatch.
 *
 * @since 3.0
 */
object FeatureMemberValues {

    /**
     * Descend a [NakshaFeature] using the given [path] segments; returns _null_ if any segment is missing.
     *
     * @param feature the feature to walk.
     * @param path the list of path segments (e.g. `["properties", "@ns:com:here:xyz", "updatedAt"]`).
     * @return the value at the path, or _null_ if the path does not exist.
     */
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

    /**
     * Coerce a raw value to the type expected by the member.
     *
     * For [MemberType.SPATIAL], the value is expected to be a [SpGeometry] (or [naksha.geo.SpPoint]) which will be
     * encoded as TWKB bytes. For other types, the value is expected to already be the raw JSON value.
     *
     * @param value the raw value extracted from the feature.
     * @param type the expected [MemberType].
     * @param featureId the feature identifier (for logging).
     * @param memberName the member name (for logging).
     * @return the coerced value, or _null_ if coercion failed.
     */
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
            MemberType.SPATIAL -> coerceSpatial(value, featureId, memberName)
            MemberType.TAG_MAP -> coerceTags(value, featureId, memberName)
            MemberType.TAG_MAP_FROM_ARRAY -> coerceTagsFromArray(value, featureId, memberName)
            // A tag list is stored the same way as a tag array: converted to a flat jsonb map.
            MemberType.TAG_LIST -> coerceTagsFromArray(value, featureId, memberName)
            MemberType.TUPLE_NUMBER -> coerceTupleNumber(value, featureId, memberName)
            else -> {
                warnMismatch(featureId, memberName, type.toString(), value)
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Individual coercion implementations
    // -------------------------------------------------------------------------

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

    /**
     * Coerce a tuple-number value: an already-materialized [TupleNumber], or a string/byte-array encoding.
     */
    private fun coerceTupleNumber(value: Any, featureId: String, memberName: String): TupleNumber? = when (value) {
        is TupleNumber -> value
        is String -> TupleNumber.fromString(value)
        is ByteArray -> TupleNumber.fromByteArray(value)
        else -> { warnMismatch(featureId, memberName, "tuple_number", value); null }
    }

    /**
     * Coerce a spatial value to TWKB bytes.
     *
     * The expected input is a [SpGeometry] (which may be a [naksha.geo.SpPoint] or any other geometry subclass).
     * The geometry is encoded as TWKB and returned as a [ByteArray].
     */
    private fun coerceSpatial(value: Any, featureId: String, memberName: String): ByteArray? = when (value) {
        is SpGeometry -> toTWKB(value)
        is ByteArray -> value
        else -> { warnMismatch(featureId, memberName, "spatial", value); null }
    }

    private fun coerceTags(value: Any, featureId: String, memberName: String): String? {
        if (value !is AnyObject) {
            warnMismatch(featureId, memberName, "tags", value)
            return null
        }
        return try { toJSON(value) } catch (_: Exception) { warnMismatch(featureId, memberName, "tags", value); null }
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

    private fun warnMismatch(featureId: String, memberName: String, expected: String, value: Any) {
        logger.warn("Member '$memberName' on feature '$featureId': expected $expected, got ${value::class.simpleName}")
    }
}
