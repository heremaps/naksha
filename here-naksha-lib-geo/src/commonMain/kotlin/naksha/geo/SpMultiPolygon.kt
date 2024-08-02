package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class SpMultiPolygon() : SpGeometry() {

    @JsName("of")
    constructor(coordinates: MultiPolygonCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): MultiPolygonCoord = super.getCoordinates() as MultiPolygonCoord
    fun withCoordinates(coordinates: MultiPolygonCoord): SpMultiPolygon {
        setCoordinates(coordinates)
        return this
    }
}