@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class PointsArrayCoordsProxy() : CoordinatesProxy<PointProxy>(PointProxy::class) {

    @JsName("of")
    constructor(vararg coords: PointProxy) : this() {
        addAll(coords)
    }
}