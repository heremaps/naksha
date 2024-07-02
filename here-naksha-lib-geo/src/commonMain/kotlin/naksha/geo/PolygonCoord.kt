package naksha.geo

import naksha.base.AbstractListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class PolygonCoord(): AbstractListProxy<LineStringCoord>(LineStringCoord::class), ICoordinates {

    @JsName("of")
    constructor(vararg lineStrings: LineStringCoord) : this() {
        addAll(lineStrings)
    }

}