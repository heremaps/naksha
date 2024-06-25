@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiPointsArrayCoordsProxy() : CoordinatesProxy<MultiPointProxy>(MultiPointProxy::class) {

    @JsName("of")
    constructor(vararg coords: MultiPointProxy) : this() {
        addAll(coords)
    }
}