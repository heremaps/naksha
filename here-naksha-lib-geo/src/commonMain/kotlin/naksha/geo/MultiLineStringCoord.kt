package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiLineStringCoord() : ListProxy<LineStringCoord>(LineStringCoord::class), ICoordinates {

    @JsName("of")
    constructor(vararg lineStrings: LineStringCoord) : this() {
        addAll(lineStrings)
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