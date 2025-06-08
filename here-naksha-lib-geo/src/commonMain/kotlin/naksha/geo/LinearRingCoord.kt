@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A linear ring is the same as a [GeoJSON LineString](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.4), except that the first and last position must be exactly the same.
 *
 * The only difference between [LinearRingCoord] and [LineStringCoord] lies in the implementation of the [fix] method. For a linear ring, it will copy the first position into the last, when this is yet the case.
 *
 * ### Coordinates
 * [ICoordinates], [PointCoord], [MultiPointCoord], [LineStringCoord], [LinearRingCoord], [MultiLineStringCoord], [PolygonCoord], [MultiPolygonCoord].
 *
 * ### Geometries
 * [SpGeometry], [SpPoint], [SpMultiPoint], [SpLineString], [SpMultiLineString], [SpPolygon], [SpMultiPolygon]
 *
 * @since 3.0
 */
@JsExport
class LinearRingCoord() : LineStringCoord() {

    @JsName("LinearRingCoordOf")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }

    companion object LinearRingCoordCompanion {
        /**
         * The [PlatformType] of [LinearRingCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(LinearRingCoord::class).withPackageName(PACKAGE_NAME)

        init {
            initialize()
        }
    }

    override fun fix(): LinearRingCoord {
        super.fix()
        if (size < 2) throw illegalState("LinearRingCoord must have at least 2 points")
        val first = this[0]!!
        val last = this[size - 1]!!
        if (first != last) this[size] = first
        return this
    }
    override fun hasZ(): Boolean = this.any { hasZ() }
    override fun hasM(): Boolean = this.any { hasM() }
}