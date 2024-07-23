package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPointCoord() : ListProxy<PointCoord>(PointCoord::class), IMultiCoordinates<PointCoord> {

    @JsName("of")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }
}