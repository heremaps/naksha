package naksha.geo

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPolygonCoord() : PTypedArray<PolygonCoord>(PolygonCoord::class), ICoordinates {

    @JsName("of")
    constructor(vararg polygons: PolygonCoord) : this() {
        addAll(polygons)
    }

    override fun hasZ(): Boolean {
        for (p in this) if (p != null && p.hasZ()) return true
        return false
    }

    override fun hasM(): Boolean {
        for (p in this) if (p != null && p.hasM()) return true
        return false
    }
}
