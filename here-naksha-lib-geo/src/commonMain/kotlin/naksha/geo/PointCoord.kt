package naksha.geo

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set
import naksha.base.fn.Fn0
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A [GeoJSON Point Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.2), which is basically one [Position](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 *
 * The [GeoJSON Position](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1) is merged with the [GeoJSON Point coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.2) as this is basically the same.
 *
 * ## Note
 * If `z` is null, this coordinate is truncated to a size of 2, so `m` is automatically removed. In other words, a coordinate can only have an `m` value, if it has as well a valid `z` value.
 *
 * ### Coordinates
 * [ICoordinates], [PointCoord], [MultiPointCoord], [LineStringCoord], [LinearRingCoord], [MultiLineStringCoord], [PolygonCoord], [MultiPolygonCoord].
 *
 * ### Geometries
 * [SpGeometry], [SpPoint], [SpMultiPoint], [SpLineString], [SpMultiLineString], [SpPolygon], [SpMultiPolygon]
 *
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class PointCoord() : ListProxy<Double>(Double_TYPE), ICoordinates {

    /**
     * Create an initialized coordinate.
     * @param longitude The longitude.
     * @param latitude The latitude.
     * @param z If not `null`, then the `z` value, being elevation or altitude.
     * @param m If not `null`, then`z` must not be `null` either.
     * @since 3.0
     */
    @JsName("PointCoordOf")
    @JvmOverloads
    constructor(longitude: Double, latitude: Double, z: Double? = null, m: Double? = null) : this() {
        this.longitude = longitude
        this.latitude = latitude
        if (z != null) {
            this.z = z
            if (m != null) this.m = m
        } else if (m != null) throw illegalArg("If m is given (4D), z must be given too (3D)")
    }

    companion object PointCoordCompanion {
        /**
         * The [PlatformType] of [PointCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<PointCoord> = forKClass(PointCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Point")
    }

    override fun createData(): PlatformList = Platform.newArray(4)

    // Ensure that whenever doubles read or written, they are rounded.
    // The setter and getter will bypass this method, using `array_get` direct, so they avoid to pay
    // the cost of rounding and type checking!
    @Suppress("UNCHECKED_CAST")
    override fun <T> box(raw: Any?, type: PlatformType<T>, alternative: T?, init: Fn0<T?>?): T?
        = if (raw != null && type === Double_TYPE) sp_double(raw) as T? else super.box(raw, type, alternative, init)

    override fun fix(): PointCoord {
        val po = platformObject()
        array_set(po, LON, sp_lon(sp_double(array_get(po, LON))) ?:
            throw illegalState("Longitude has an invalid value: ${this[LON]}"))
        array_set(po, LAT,sp_lat(sp_double(array_get(po, LAT))) ?:
            throw illegalState("Latitude has an invalid value: ${this[LAT]}"))
        val z = sp_double(array_get(po, Z))
        if (z == null) {
            size = 2
            return this
        }
        val m = sp_double(array_get(po, M))
        if (m == null) size = 3
        return this
    }

    /**
     * The WGG'84 longitude of the point.
     * @since 3.0
     */
    var longitude: Double
        get() = as_double_or_zero(getRaw(LON))
        set(value) {
            val lon = sp_lon(sp_double(value)) ?: throw illegalArg("Illegal value for longitude: $value")
            setRaw(LON, lon)
        }

    fun withLongitude(longitude: Double): PointCoord {
        this.longitude = longitude
        return this
    }

    /**
     * The WGS'84 latitude of the point.
     * @since 3.0
     */
    var latitude: Double
        get() = as_double_or_zero(getRaw(LAT))
        set(value) {
            val lat = sp_lat(sp_double(value)) ?: throw illegalArg("Illegal value for latitude: $value")
            setRaw(LAT, lat)
        }

    fun withLatitude(latitude: Double): PointCoord {
        this.latitude = latitude
        return this
    }

    /**
     * The `z` value of the coordinate, normally used for [elevation or altitude](https://en.wikipedia.org/wiki/Elevation).
     * @since 3.0
     */
    var z: Double?
        get() = as_double_or_null(getRaw(Z))
        set(value) {
            val z = sp_double(value)
            if (z == null && value != null) throw illegalArg("Illegal value for Z: $value")
            if (z == null) {
                if (size >= 3) size = 2
            } else {
                setRaw(Z, z)
            }
        }
    override fun hasZ(): Boolean = is_double(getRaw(Z))
    fun removeZ(): Double? = as_double_or_null(removeAt(Z))
    fun withZ(z: Double?): PointCoord {
        this.z = z
        return this
    }

    /**
     * The `m` value of the coordinate.
     *
     * It is not recommended to be used by [GeoJSON specification](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1), quote: _Implementations SHOULD NOT extend positions beyond three elements_, but it is supported by Naksha.
     * @since 3.0
     */
    var m: Double?
        get() = as_double_or_null(getRaw(M))
        set(value) {
            val m = sp_double(value)
            if (m == null && value != null) throw illegalArg("Illegal value for M: $value")
            if (m == null) {
                if (size >= 4) size = 3
            } else {
                setRaw(M, m)
            }
        }
    override fun hasM(): Boolean = is_double(getRaw(M))
    fun removeM(): Double? = as_double_or_null(removeAt(M))
    fun withM(m: Double?): PointCoord {
        this.m = m
        return this
    }

    /**
     * Tests if this coordinate is 2D.
     * @since 3.0
     */
    fun is2D(): Boolean = size == 2

    /**
     * Tests if this coordinate is 3D.
     * @since 3.0
     */
    fun is3D(): Boolean = size == 3

    /**
     * Tests if this coordinate is 3D.
     * @since 3.0
     */
    fun is4D(): Boolean = size == 4

    /**
     * Ensure that this is a 2D coordinate.
     * @since 3.0
     */
    fun to2D(): PointCoord {
        val po = platformObject()
        setCapacity(4)
        array_set(po, LON, sp_lon(sp_double(array_get(po, LON))) ?: 0.0)
        array_set(po, LAT, sp_lat(sp_double(array_get(po, LAT))) ?: 0.0)
        size = 2
        return this
    }

    /**
     * Ensure that this is a 3D coordinate.
     * @since 3.0
     */
    fun to3D(): PointCoord {
        val po = platformObject()
        setCapacity(4)
        array_set(po, LON, sp_lon(sp_double(array_get(po, LON))) ?: 0.0)
        array_set(po, LAT, sp_lat(sp_double(array_get(po, LAT))) ?: 0.0)
        array_set(po, Z, sp_double(array_get(po, Z)) ?: 0.0)
        size = 3
        return this
    }

    /**
     * Ensure that this is a 4D coordinate.
     * @since 3.0
     */
    fun to4D(): PointCoord {
        val po = platformObject()
        setCapacity(4)
        array_set(po, LON, sp_lon(sp_double(array_get(po, LON))) ?: 0.0)
        array_set(po, LAT, sp_lat(sp_double(array_get(po, LAT))) ?: 0.0)
        array_set(po, Z, sp_double(array_get(po, Z)) ?: 0.0)
        array_set(po, M, sp_double(array_get(po, M)) ?: 0.0)
        size = 4
        return this
    }
}