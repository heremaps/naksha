@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiLineStringProxy() : GeometryProxy(MultiPointsArrayCoordsProxy(), "MultiLineString") {

    @JsName("of")
    constructor(vararg points: MultiPointProxy) : this() {
        coordinates = MultiPointsArrayCoordsProxy(*points)
    }

}