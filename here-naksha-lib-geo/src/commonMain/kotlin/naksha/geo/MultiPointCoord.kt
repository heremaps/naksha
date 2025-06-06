@file:Suppress("OPT_IN_USAGE")

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
 * A [GeoJSON Multi-Point Coordinates](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.3).
 * @since 3.0
 * @see ICoordinates
 */
@JsExport
class MultiPointCoord() : ListProxy<PointCoord>(PointCoord.TYPE), ICoordinates {

    @JsName("MultiPointCoordOf")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }

    companion object MultiPointCoordCompanion {
        /**
         * The [PlatformType] of [MultiPointCoord].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<MultiPointCoord> = forKClass(MultiPointCoord::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("MultiPoint")
    }

    override fun fix(): MultiPointCoord {
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