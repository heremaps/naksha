package naksha.geo

import naksha.base.AbstractListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class LineStringCoord() : AbstractListProxy<PointCoord>(PointCoord::class), ICoordinates {

    @JsName("of")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }
}