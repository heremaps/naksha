package naksha.geo

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A GeoJSON [MultiPolygon](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.7).
 *
 * ### Coordinates
 * [ICoordinates], [PointCoord], [MultiPointCoord], [LineStringCoord], [LinearRingCoord], [MultiLineStringCoord], [PolygonCoord], [MultiPolygonCoord].
 *
 * ### Geometries
 * [SpGeometry], [SpPoint], [SpMultiPoint], [SpLineString], [SpMultiLineString], [SpPolygon], [SpMultiPolygon]
 *
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpMultiPolygon() : SpGeometry() {

    @JsName("SpMultiPolygonOf")
    constructor(coordinates: MultiPolygonCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpMultiPolygon_C {
        /**
         * The [PlatformType] of [SpMultiPolygon].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpMultiPolygon::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiPolygon")

        init {
            initialize()
        }
    }

    override var coordinates: MultiPolygonCoord
        get() = get_coordinates() as MultiPolygonCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: MultiPolygonCoord): SpMultiPolygon {
        set_coordinates(value)
        return this
    }
}