package naksha.geo

import naksha.geo.cords.LineStringCoordsProxy
import naksha.geo.cords.PointCoordsProxy
import naksha.geo.cords.PolygonCoordsProxy
import org.locationtech.jts.geom.Geometry

object ProxyGeoUtil {

    fun toJtsGeometry(proxy: GeometryProxy): Geometry {
        TODO()
    }

    /**
     * Helper function that returns Geometry representing BoundingBox for the co-ordinates
     * supplied as arguments.
     *
     * @param west west co-ordinate
     * @param south south co-ordinate
     * @param east east co-ordinate
     * @param north north co-ordinate
     * @return Geometry representing BBox envelope
     */
    fun createBBoxEnvelope(
        west: Double, south: Double, east: Double, north: Double
    ): PolygonProxy {
        val polygonProxy = PolygonProxy()
        polygonProxy.coordinates = PolygonCoordsProxy(
            LineStringCoordsProxy(
                PointCoordsProxy(west, south),
                PointCoordsProxy(east, south),
                PointCoordsProxy(east, north),
                PointCoordsProxy(west, north),
                PointCoordsProxy(west, south)
            )
        )
        return polygonProxy
    }
}