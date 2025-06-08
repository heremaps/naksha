package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A GeoJSON [MultiLineString](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.5).
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
class SpMultiLineString() : SpGeometry() {

    @JsName("SpMultiLineStringOf")
    constructor(coordinates: MultiLineStringCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpMultiLineStringCompanion {
        /**
         * The [PlatformType] of [SpLineString].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpMultiLineString::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiLineString")

        init {
            initialize()
        }
    }

    override var coordinates: MultiLineStringCoord
        get() = get_coordinates() as MultiLineStringCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: MultiLineStringCoord): SpMultiLineString {
        set_coordinates(value)
        return this
    }
}