package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPointGeometry() : GeometryProxy() {

    @JsName("of")
    constructor(coordinates: MultiPointCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): MultiPointCoord = super.getCoordinates() as MultiPointCoord
    fun withCoordinates(coordinates: MultiPointCoord): MultiPointGeometry {
        setCoordinates(coordinates)
        return this
    }
}