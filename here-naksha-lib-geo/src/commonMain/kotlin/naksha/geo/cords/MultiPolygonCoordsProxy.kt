@file:Suppress("OPT_IN_USAGE")

package naksha.geo.cords

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiPolygonCoordsProxy() : CoordinatesProxy<PolygonCoordsProxy>(PolygonCoordsProxy::class) {

    @JsName("of")
    constructor(vararg coords: PolygonCoordsProxy) : this() {
        addAll(coords)
    }
}