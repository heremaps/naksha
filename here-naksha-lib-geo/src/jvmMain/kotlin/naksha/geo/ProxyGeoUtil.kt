package naksha.geo

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.MultiPoint

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
    ): GeometryProxy {
        return PolygonProxy(
            PointProxy(west, south),
            PointProxy(east, south),
            PointProxy(east, north),
            PointProxy(west, north),
            PointProxy(west, south)
        )
    }
}