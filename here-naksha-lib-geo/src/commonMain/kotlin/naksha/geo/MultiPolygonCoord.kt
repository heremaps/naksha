package naksha.geo

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A [GeoJSON MultiPolygon Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.7).
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
class MultiPolygonCoord() : ListProxy<PolygonCoord>(PolygonCoord.TYPE), ICoordinates {

    @JsName("MultiPolygonCoordOf")
    constructor(vararg polygons: PolygonCoord) : this() {
        addAll(polygons)
    }

    companion object MultiPolygonCoordCompanion {
        /**
         * The [PlatformType] of [MultiPolygonCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MultiPolygonCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiPolygon")

        init {
            initialize()
        }
    }

    override fun fix(): MultiPolygonCoord {
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