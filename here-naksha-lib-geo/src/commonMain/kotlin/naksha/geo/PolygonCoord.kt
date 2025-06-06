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
 * A [GeoJSON Polygon Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.6).
 * @since 3.0
 * @see ICoordinates
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PolygonCoord(): ListProxy<LinearRingCoord>(LinearRingCoord.TYPE), ICoordinates {

    @JsName("PolygonCoordOf")
    constructor(exteriorRing: LinearRingCoord, vararg interiorRings: LinearRingCoord) : this() {
        val size = 1 + interiorRings.size
        setCapacity(size)
        add(exteriorRing)
        if (interiorRings.isNotEmpty()) for (ir in interiorRings) add(ir)
    }

    companion object PolygonCoordCompanion {
        /**
         * The [PlatformType] of [PolygonCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<PolygonCoord> = forKClass(PolygonCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("Polygon")
    }

    override fun fix(): PolygonCoord {
        var end = 0
        for (i in 0 until size) {
            val p = this[i] ?: continue
            p.fix()
            if (end != i) this[end] = p
            end++
        }
        if (size > end) size = end
        if (size < 1) throw illegalState("PolygonCoord must have at least an exterior ring")
        return this
    }
    override fun hasZ(): Boolean = this.any { hasZ() }
    override fun hasM(): Boolean = this.any { hasM() }

    /**
     * The exterior ring of the polygon.
     * @since 3.0
     */
    var exteriorRing: LinearRingCoord
        get() = this[0] ?: throw illegalState("PolygonCoord must have at least the exterior ring")
        set(value) {
            this[0] = value
        }
}