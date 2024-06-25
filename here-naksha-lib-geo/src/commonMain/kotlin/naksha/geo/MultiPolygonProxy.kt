@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiPolygonProxy() : GeometryProxy(MultiPointsArrayCoordsProxy(), "MultiPolygonString") {

    @JsName("of")
    constructor(vararg points: MultiPointProxy) : this() {
        coordinates = MultiPointsArrayCoordsProxy(*points)
    }

    fun getCoords() = coordinates?.proxy(MultiPointsArrayCoordsProxy::class)

}