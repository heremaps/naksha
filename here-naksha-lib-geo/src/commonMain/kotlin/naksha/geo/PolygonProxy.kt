@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class PolygonProxy() : GeometryProxy(PointsArrayCoordsProxy(), "Polygon") {

    @JsName("of")
    constructor(vararg coords: PointProxy) : this() {
        coordinates = PointsArrayCoordsProxy(*coords)
    }

    fun getCoords() = coordinates?.proxy(PointsArrayCoordsProxy::class)

}