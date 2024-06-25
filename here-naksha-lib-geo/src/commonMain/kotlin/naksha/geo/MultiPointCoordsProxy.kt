@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiPointCoordsProxy() : CoordinatesProxy<PointCoordsProxy>(PointCoordsProxy::class) {

    @JsName("of")
    constructor(vararg coords: PointCoordsProxy) : this() {
        addAll(coords)
    }
}