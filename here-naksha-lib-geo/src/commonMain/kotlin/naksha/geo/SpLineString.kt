package naksha.geo

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A GeoJSON [LineString](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.4).
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
class SpLineString() : SpGeometry() {

    @JsName("SpLineStringOf")
    constructor(coordinates: LineStringCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpLineString_C {
        /**
         * The [PlatformType] of [SpLineString].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpLineString::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("LineString")

        init {
            initialize()
        }
    }

    override var coordinates: LineStringCoord
        get() = get_coordinates() as LineStringCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: LineStringCoord): SpLineString {
        set_coordinates(value)
        return this
    }
}