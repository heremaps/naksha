@file:Suppress("OPT_IN_USAGE")

package naksha.geo.cords

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class MultiLineStringCoordsProxy() : CoordinatesProxy<LineStringCoordsProxy>(LineStringCoordsProxy::class) {

    @JsName("of")
    constructor(vararg coords: LineStringCoordsProxy) : this() {
        addAll(coords)
    }
}