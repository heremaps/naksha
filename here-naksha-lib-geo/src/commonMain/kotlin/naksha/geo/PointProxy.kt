@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class PointProxy() : GeometryProxy(PointCoordsProxy(), "Point") {

    @JsName("of")
    constructor(vararg coords: Double?) : this() {
        coordinates = PointCoordsProxy(*coords)
    }

    fun getCoordinates() = coordinates?.proxy(PointCoordsProxy::class)
}