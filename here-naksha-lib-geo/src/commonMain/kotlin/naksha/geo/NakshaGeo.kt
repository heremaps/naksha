// This will be exposed
// - in JavaScript at the namespace: naksha.geo.{name}
// - jn Java at the class naksha.geo.NakshaGeoKt.{name}
@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformUtil.PlatformUtil_C.rd
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.math.round

/**
 * The package name `naksha.geo`
 * @since 3.0
 */
const val PACKAGE_NAME = "naksha.geo"

/**
 * The [PlatformType][naksha.base.PlatformType] of the [ICoordinates] interface.
 * @since 3.0
 */
@JvmField
@JsStatic
val ICoordinates_TYPE = forKClass(ICoordinates::class).withPackageName(PACKAGE_NAME)

/**
 * The multiplier/divisor used when converting doubles into spatial components _(`10_000_000.0` aka `1e7`)_.
 * @since 3.0
 */
const val SP_COMPONENT_MULTIPLIER_DOUBLE: Double = 10_000_000.0 // 1e7

/**
 * The maximum 32-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
const val SP_COMPONENT_MAX_INT: Int = 1_800_000_000

/**
 * The maximum 64-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
@JvmField
@JsStatic
val SP_COMPONENT_MAX_INT64: Int64 = Int64(1_800_000_000)

/**
 * The minimum double value of a spatial fixed component.
 * @since 3.0
 */
const val SP_COMPONENT_MIN_DOUBLE: Double = -1_800_000_000.0

/**
 * The minimum 32-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
const val SP_COMPONENT_MIN_INT: Int = -1_800_000_000

/**
 * The minimum 64-bit integer value of a spatial fixed integer.
 * @since 3.0
 */
@JvmField
@JsStatic
val SP_COMPONENT_MIN_INT64: Int64 = Int64(-1_800_000_000)

/**
 * Index of longitude: `0`
 */
internal const val LON = 0

/**
 * Index of latitude: `1`
 */
internal const val LAT = 1

/**
 * Index of Z: `2`
 */
internal const val Z = 2

/**
 * Index of M: `3`
 */
internal const val M = 3

/**
 * Index of west _(left-, min-)_ longitude in 2D and 3D bounding box: `0`
 */
internal const val WEST = 0

/**
 * Index of south _(bottom-, min-)_ latitude in 2D and 3D bounding box: `1`
 */
internal const val SOUTH = 1

/**
 * Index of min-z in 3D bounding box: `2`
 */
internal const val MIN_Z_3D = 2

/**
 * Index of east _(right-, max-)_ longitude in 2D bounding box: `2`
 */
internal const val EAST_2D = 2

/**
 * Index of east _(right-, max-)_ longitude in 3D bounding box: `3`
 */
internal const val EAST_3D = 3

/**
 * Index of north _(top-, max-)_ latitude in 2D bounding box: `3`
 */
internal const val NORTH_2D = 3

/**
 * Index of north _(top-, max-)_ latitude in 3D bounding box: `4`
 */
internal const val NORTH_3D = 4

/**
 * Index of max-z in 3D bounding box: `5`
 */
internal const val MAX_Z_3D = 5

/**
 * The size of an empty bounding box: `0`
 */
internal const val SIZE_EMPTY = 0

/**
 * The size of a 2D bounding box: `4`
 */
internal const val SIZE_2D = 4

/**
 * The size of a 3D bounding box: `6`
 */
internal const val SIZE_3D = 6

/**
 * The string `Feature`.
 */
internal const val FEATURE = "Feature"

/**
 * The string `FeatureCollection`.
 */
internal const val FEATURE_COLLECTION = "FeatureCollection"


/**
 * Convert the given value into a spatial component with 7 decimal digits _(does round)_.
 * @param value The value to convert and round.
 * @return the given `value`, rounded to 7 decimal digits, or `null`, if the value can't be converted.
 * @see rd
 */
fun sp_double(value: Any?): Double? = when(value) {
    is Double -> if (value.isNaN()) null else rd(value)
    is Float -> if (value.isNaN()) null else rd(value.toDouble())
    is Int64 -> if (value < SP_COMPONENT_MIN_INT || value > SP_COMPONENT_MAX_INT) null else rd(value.toDouble())
    is Long -> {
        val i64 = Int64(value)
        if (i64 < SP_COMPONENT_MIN_INT || i64 > SP_COMPONENT_MAX_INT) null else rd(value.toDouble())
    }
    is Number -> rd(value.toDouble())
    is String -> {
        val f64 = value.toDoubleOrNull()
        if (f64 == null || f64.isNaN()) null else rd(f64)
    }
    else -> null
}

/**
 * Round and validate the given component to longitude _(does not round)_.
 * @param component The component to validate.
 * @return the longitude component or `null`, if the given component is out of range.
 * @see rd
 */
fun sp_lon(component: Double?): Double? = if (component == null || component < -180.0 || component > 180.0) null else component

/**
 * Round and validate the given component to latitude _(does not round)_.
 * @param component The component to validate.
 * @return the latitude component or `null`, if the given component is out of range.
 * @see rd
 */
fun sp_lat(component: Double?): Double? = if (component == null || component < -90.0 || component > 90.0) null else component

/**
 * Convert the given spatial component into a spatial fixed integer _(does round)_.
 *
 * - Throws [NakshaError.ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given value is out of range.
 * @param component The spatial component.
 * @return the spatial fixed integer.
 * @see rd
 */
fun sp_double_to_int(component: Double): Int {
    if (component < -180.0 || component > 180.0) throw illegalArg("The component must be between -180 and 180")
    return round(component * SP_COMPONENT_MULTIPLIER_DOUBLE).toInt()
}

/**
 * Convert the given spatial fixed integer into a spatial component _(does round)_.
 *
 * - Throws [NakshaError.ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT] if the given value is out of range.
 * @param fixed The spatial fixed integer.
 * @return the spatial component.
 * @see rd
 */
fun sp_int_to_double(fixed: Int): Double = rd(fixed.toDouble() / SP_COMPONENT_MULTIPLIER_DOUBLE)

/**
 * Tests if the given value is a double and not NaN.
 * @param value the value to test.
 * @return _true_ if the value is a double, not NaN; _false_ otherwise.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun is_double(value: Any?): Boolean = value is Double && !value.isNaN()

/**
 * Returns either the given value as double or `0.0`, if the value is no double or NaN _(does not round)_.
 * @param value The value that should be a valid double.
 * @return the given value or `0.0`, if the value is no double or NaN.
 * @see rd
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun as_double_or_zero(value: Any?): Double = if (value !is Double || value.isNaN()) 0.0 else value

/**
 * Returns the given value as double or `null`, if the value is no double or NaN _(does not round)_.
 * @param value The value that should be a valid double.
 * @return the given value as double or `null`.
 * @see rd
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun as_double_or_null(value: Any?): Double? = if (value !is Double || value.isNaN()) null else value

private val isInitialied = AtomicBool(false)
internal fun initialize() {
    if (isInitialied.compareAndSet(expect = false, update = true)) {
        forKClass(PointCoord::class).initialize()
        forKClass(SpPoint::class).initialize()

        forKClass(MultiPointCoord::class).initialize()
        forKClass(SpMultiPoint::class).initialize()

        forKClass(LineStringCoord::class).initialize()
        forKClass(LinearRingCoord::class).initialize()
        forKClass(SpLineString::class).initialize()

        forKClass(MultiLineStringCoord::class).initialize()
        forKClass(SpMultiLineString::class).initialize()

        forKClass(PolygonCoord::class).initialize()
        forKClass(SpPolygon::class).initialize()

        forKClass(MultiPolygonCoord::class).initialize()
        forKClass(SpMultiPolygon::class).initialize()

        forKClass(BBox::class).initialize()
        forKClass(GeoFeature::class).initialize()
        forKClass(GeoFeatureList::class).initialize()
        forKClass(GeoCollection::class).initialize()
        forKClass(GeoTypeDetector::class).initialize()

        forKClass(HereTile::class).initialize()

        // Replace the default detector with the lib-geo variant.
        Platform.globalDetectors.remove(AnyTypedObjectDetector.defaultDetector)
        Platform.globalDetectors.add(GeoTypeDetector.defaultGeoDetector)
    }
}