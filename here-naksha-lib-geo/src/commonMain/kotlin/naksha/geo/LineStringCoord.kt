@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A [GeoJSON LineString Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.4).
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
open class LineStringCoord() : ListProxy<PointCoord>(PointCoord.TYPE), ICoordinates {

    @JsName("LineStringCoordOf")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }

    companion object LineStringCoordCompanion {
        /**
         * The [PlatformType] of [LineStringCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<LineStringCoord> = forKClass(LineStringCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("LineString")
    }

    override fun fix(): LineStringCoord {
        var end = 0
        for (i in 0 until size) {
            val p = this[i] ?: continue
            p.fix()
            if (end != i) this[end] = p
            end++
        }
        if (size > end) size = end
        return this
    }
    override fun hasZ(): Boolean = this.any { hasZ() }
    override fun hasM(): Boolean = this.any { hasM() }
}