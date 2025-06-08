package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A GeoJSON [Point](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 *
 * ### Coordinates
 * [ICoordinates], [PointCoord], [MultiPointCoord], [LineStringCoord], [LinearRingCoord], [MultiLineStringCoord], [PolygonCoord], [MultiPolygonCoord].
 *
 * ### Geometries
 * [SpGeometry], [SpPoint], [SpMultiPoint], [SpLineString], [SpMultiLineString], [SpPolygon], [SpMultiPolygon]
 *
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpPoint() : SpGeometry() {

    @JsName("SpPointOf")
    constructor(coordinates: PointCoord) : this() {
        this.coordinates = coordinates
    }

    /**
     * Create an initialized point.
     * @param longitude The longitude.
     * @param latitude The latitude.
     * @param z If not `null`, then the `z` value, being elevation or altitude.
     * @param m If not `null`, then`z` must not be `null` either.
     * @since 3.0
     */
    @JvmOverloads
    @JsName("fromLonLatZM")
    constructor(longitude: Double, latitude: Double, z: Double? = null, m: Double? = null) : this() {
        this.coordinates = PointCoord(longitude, latitude, z, m)
    }

    companion object SpPointCompanion {
        /**
         * The [PlatformType] of [SpPoint].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpPoint> = forKClass(SpPoint::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Point")

        init {
            initialize()
        }
    }

    override var coordinates: PointCoord
        get() = get_coordinates() as PointCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: PointCoord): SpPoint {
        set_coordinates(value)
        return this
    }


}