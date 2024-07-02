package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class PolygonGeometry() : GeometryProxy() {

    @JsName("of")
    constructor(coordinates: PolygonCoord) : this() {
        setCoordinates(coordinates)
    }

    @JsName("ofBoundingBox")
    constructor(bbox: BoundingBoxProxy) : this() {
        val lineString = LineStringCoord(
            PointCoord(bbox.getWestLongitude(), bbox.getSouthLatitude()),
            PointCoord(bbox.getEastLongitude(), bbox.getSouthLatitude()),
            PointCoord(bbox.getEastLongitude(), bbox.getNorthLatitude()),
            PointCoord(bbox.getWestLongitude(), bbox.getNorthLatitude()),
            PointCoord(bbox.getWestLongitude(), bbox.getSouthLatitude())
        )
        val coordinates = PolygonCoord(lineString)
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): PolygonCoord = super.getCoordinates() as PolygonCoord
    fun withCoordinates(coordinates: PolygonCoord): PolygonGeometry {
        setCoordinates(coordinates)
        return this
    }
}