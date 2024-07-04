package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON [Point](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class PointGeometry() : GeometryProxy(), ICoordinates {

    @JsName("of")
    constructor(coordinates: PointCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): PointCoord = super.getCoordinates() as PointCoord
    fun withCoordinates(coordinates: PointCoord): PointGeometry {
        setCoordinates(coordinates)
        return this
    }
}