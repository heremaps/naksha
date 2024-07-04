package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class LineStringGeometry() : GeometryProxy() {

    @JsName("of")
    constructor(coordinates: LineStringCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): LineStringCoord = super.getCoordinates() as LineStringCoord
    fun withCoordinates(coordinates: LineStringCoord): LineStringGeometry {
        setCoordinates(coordinates)
        return this
    }
}