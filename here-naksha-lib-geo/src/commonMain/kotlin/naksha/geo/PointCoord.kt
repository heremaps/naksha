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

/**
 * A [GeoJSON Point Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.2).
 *
 * @since 3.0
 * @see ICoordinates
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class PointCoord() : ListProxy<Double>(Double_TYPE), ICoordinates {

    @Suppress("SENSELESS_COMPARISON")
    @JsName("fromLonLat")
    constructor(longitude: Double, latitude: Double, vararg additional: Double) : this() {
        this.longitude = longitude
        this.latitude = latitude
        if (additional != null && additional.isNotEmpty()) {
            this.z = additional[0]
            if (additional.size >= 2) this.m = additional[1]
        }
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
        array_set(po, LON, sp_lon(sp_double(this[LON])) ?: throw illegalState("Longitude has an invalid value: ${this[LON]}"))
        array_set(po, LAT,  sp_lat(sp_double(this[LAT])) ?: throw illegalState("Latitude has an invalid value: ${this[LAT]}"))
        val z = array_get(po, Z)
        if (Z < size && z != null) {
            array_set(po, Z, sp_double(z) ?: throw illegalState("Z has an invalid value: $z"))
        }
        val m = array_get(po, M)
        if (M < size && m != null) {
            array_set(po, M, sp_double(m) ?: throw illegalState("M has an invalid value: $m"))
        }
        // Truncate, if possible.
        if (m == null) {
            size = if (z != null) 3 else 2
        }
        return this
    }

    /**
     * The WGG'84 longitude of the point.
     * @since 3.0
     */
    var longitude: Double
        get() = as_double_or_zero(getRaw(LON))
        set(value) {
            val lon = sp_lon(sp_double(value)) ?: throw illegalArg("Longitude has an invalid value: $value")
            array_set(platformObject(), LON, lon)
        }
    fun hasLongitude(): Boolean = is_double(getRaw(LON))

    /**
     * The WGS'84 latitude of the point.
     * @since 3.0
     */
    var latitude: Double
        get() = as_double_or_zero(getRaw(LAT))
        set(value) {
            val lat = sp_lat(sp_double(value)) ?: throw illegalArg("Latitude has an invalid value: $value")
            array_set(platformObject(), LAT, lat)
        }
    fun hasLatitude(): Boolean = is_double(getRaw(LAT))

    /**
     * The `z` value of the coordinate, normally used for [elevation or altitude](https://en.wikipedia.org/wiki/Elevation).
     * @since 3.0
     */
    var z: Double?
        get() = as_double_or_null(getRaw(Z))
        set(value) {
            val z = sp_double(value)
            if (z == null && value != null) throw illegalArg("Z has an invalid value: $value")
            array_set(platformObject(), Z, z)
        }
    override fun hasZ(): Boolean = is_double(getRaw(Z))
    fun removeZ(): Double? = as_double_or_null(removeAt(Z))

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
            if (m == null && value != null) throw illegalArg("M has an invalid value: $value")
            array_set(platformObject(), M, m)
        }
    override fun hasM(): Boolean = is_double(getRaw(M))
    fun removeM(): Double? = as_double_or_null(removeAt(M))
}