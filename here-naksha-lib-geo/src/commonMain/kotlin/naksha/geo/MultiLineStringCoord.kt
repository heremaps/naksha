package naksha.geo

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.illegalState
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A [GeoJSON MultiLineString Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.5).
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
class MultiLineStringCoord() : ListProxy<LineStringCoord>(LineStringCoord.TYPE), ICoordinates {

    @JsName("MultiLineStringCoordOf")
    constructor(vararg lineStrings: LineStringCoord) : this() {
        addAll(lineStrings)
    }

    companion object MultiLineStringCoord_C {
        /**
         * The [PlatformType] of [MultiLineStringCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MultiLineStringCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiLineString")

        init {
            initialize()
        }
    }

    override fun fix(): MultiLineStringCoord {
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
    override fun hasZ(): Boolean = this.any { it?.hasZ() ?: false }
    override fun hasM(): Boolean = this.any { it?.hasM() ?: false }
}