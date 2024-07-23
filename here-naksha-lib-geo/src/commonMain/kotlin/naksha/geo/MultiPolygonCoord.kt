package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPolygonCoord() : ListProxy<PolygonCoord>(PolygonCoord::class), IMultiCoordinates<PolygonCoord> {

    @JsName("of")
    constructor(vararg polygons: PolygonCoord) : this() {
        addAll(polygons)
    }
}