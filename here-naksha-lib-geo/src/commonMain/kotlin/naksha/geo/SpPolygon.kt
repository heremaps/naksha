package naksha.geo

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A GeoJSON [Polygon](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.6).
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
class SpPolygon() : SpGeometry() {

    @JsName("SpPolygonOf")
    constructor(coordinates: PolygonCoord) : this() {
        this.coordinates = coordinates
    }

    @JsName("SpPolygonOfBBox")
    constructor(bbox: BBox) : this() {
        val lineString = LinearRingCoord(
            PointCoord(bbox.west, bbox.south),
            PointCoord(bbox.east, bbox.south),
            PointCoord(bbox.east, bbox.north),
            PointCoord(bbox.west, bbox.north),
            PointCoord(bbox.west, bbox.south)
        )
        val coordinates = PolygonCoord(lineString)
        this.coordinates = coordinates
    }

    companion object SpPolygon_C {
        /**
         * The [PlatformType] of [SpPolygon].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpPolygon::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Polygon")

        init {
            initialize()
        }
    }

    override var coordinates: PolygonCoord
        get() = get_coordinates() as PolygonCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: PolygonCoord): SpPolygon {
        set_coordinates(value)
        return this
    }
}