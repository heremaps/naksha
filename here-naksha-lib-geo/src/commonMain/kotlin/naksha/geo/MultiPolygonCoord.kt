package naksha.geo

import naksha.base.AbstractListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPolygonCoord() : AbstractListProxy<PolygonCoord>(PolygonCoord::class), ICoordinates {

    @JsName("of")
    constructor(vararg polygons: PolygonCoord) : this() {
        addAll(polygons)
    }
}