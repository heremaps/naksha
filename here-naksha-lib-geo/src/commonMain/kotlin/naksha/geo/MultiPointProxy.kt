@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiPointProxy() : GeometryProxy(PointCoordsProxy(), "MultiPoint") {

    @JsName("of")
    constructor(vararg points: PointProxy) : this() {
        coordinates = PointsArrayCoordsProxy(*points)
    }

}