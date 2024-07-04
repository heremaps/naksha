package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPolygonGeometry() : GeometryProxy() {

    @JsName("of")
    constructor(coordinates: MultiPolygonCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): MultiPolygonCoord = super.getCoordinates() as MultiPolygonCoord
    fun withCoordinates(coordinates: MultiPolygonCoord): MultiPolygonGeometry {
        setCoordinates(coordinates)
        return this
    }
}