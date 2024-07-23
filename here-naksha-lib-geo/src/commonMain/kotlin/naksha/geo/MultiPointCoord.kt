package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE")
@JsExport
class MultiPointCoord() : ListProxy<PointCoord>(PointCoord::class), IMultiCoordinates<PointCoord> {

    @JsName("of")
    constructor(vararg points: PointCoord) : this() {
        addAll(points)
    }

    override fun calculateBBox(): BoundingBoxProxy {
        var west = 0.0
        var south = 0.0
        var east = 0.0
        var north = 0.0
        val iterator = iterator()
        while (iterator.hasNext()) {
            val firstPoint = iterator.next() ?: continue
            val bBox = firstPoint.calculateBBox()
            west = bBox.getWestLongitude()
            south = bBox.getSouthLatitude()
            east = bBox.getEastLongitude()
            north = bBox.getNorthLatitude()
        }
        while (iterator.hasNext()) {
            val next = iterator.next() ?: continue
            val bBox = next.calculateBBox()
            if (bBox.getWestLongitude() < west) west = bBox.getWestLongitude()
            if (bBox.getSouthLatitude() < south) south = bBox.getSouthLatitude()
            if (bBox.getEastLongitude() > east) east = bBox.getEastLongitude()
            if (bBox.getNorthLatitude() > north) north = bBox.getNorthLatitude()
        }
        return BoundingBoxProxy(west, south, east, north)
    }
}