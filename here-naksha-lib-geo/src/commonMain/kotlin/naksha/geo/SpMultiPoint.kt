package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A GeoJSON [MultiPoint](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.3).
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
class SpMultiPoint() : SpGeometry() {

    @JsName("SpMultiPointOf")
    constructor(coordinates: MultiPointCoord) : this() {
        this.coordinates = coordinates
    }

    companion object SpMultiPointCompanion {
        /**
         * The [PlatformType] of [SpMultiPoint].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpMultiPoint> = forKClass(SpMultiPoint::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiPoint")

        init {
            initialize()
        }
    }

    override var coordinates: MultiPointCoord
        get() = get_coordinates() as MultiPointCoord
        set(value) { set_coordinates(value) }

    fun withCoordinates(value: MultiPointCoord): SpMultiPoint {
        set_coordinates(value)
        return this
    }
}