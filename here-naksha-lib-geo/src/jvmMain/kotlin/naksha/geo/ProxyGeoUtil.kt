package naksha.geo

import naksha.base.P_AnyList
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
    ): GeometryProxy {
        val geometryProxy = GeometryProxy()
        geometryProxy.coordinates = PolygonCoordsProxy(
            LineStringCoordsProxy(
                PointCoordsProxy(west, south),
                PointCoordsProxy(east, south),
                PointCoordsProxy(east, north),
                PointCoordsProxy(west, north),
                PointCoordsProxy(west, south)
            )
        ).proxy(P_AnyList::class)
        return geometryProxy
    }

    fun pointProxy(longitude: Double, latitude: Double, altitude: Double? = null): GeometryProxy {
        val geometryProxy = GeometryProxy()
        geometryProxy.coordinates = PointCoordsProxy(longitude, latitude, altitude).proxy(P_AnyList::class)
        return geometryProxy
    }

    fun lineStringProxy(vararg points: PointCoordsProxy): GeometryProxy {
        val geometryProxy = GeometryProxy()
        geometryProxy.coordinates = LineStringCoordsProxy(*points).proxy(P_AnyList::class)
        return geometryProxy
    }
}