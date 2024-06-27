package naksha.geo

import naksha.geo.cords.*
import org.locationtech.jts.geom.*

object ProxyGeoUtil {

    private val factory: GeometryFactory = GeometryFactory(PrecisionModel(), 4326)

    /**
     * Converts proxy model to JTS [Geometry] using [factory] with default SRID: 4326
     *
     * @param proxy - proxy geometry to convert
     * @return JTS Geometry
     * @throws [IllegalArgumentException] when proxy type is not supported
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsGeometry(proxy: GeometryProxy): Geometry? {
        return when (proxy) {
            is PointProxy -> toJtsPoint(proxy)
            is MultiPointProxy -> toJtsMultiPoint(proxy)
            is LineStringProxy -> toJtsLineString(proxy)
            is MultiLineStringProxy -> toJtsMultiLineString(proxy)
            is PolygonProxy -> toJtsPolygon(proxy)
            is MultiPolygonProxy -> toJtsMultiPolygon(proxy)
            else -> throw IllegalArgumentException("Unknown proxy type ${proxy.javaClass}")
        }
    }

    /**
     * Converts [PointProxy] to JTS [Point]
     *
     * @param proxy [PointProxy] to convert
     * @return [Point]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsPoint(proxy: PointProxy): Point {
        require(proxy.coordinates != null)
        return toJtsPoint(proxy.coordinates!!)
    }

    /**
     * Converts [PointCoordsProxy] to JTS [Point]
     *
     * @param coords [PointCoordsProxy] to convert
     * @return [Point]
     */
    fun toJtsPoint(coords: PointCoordsProxy): Point {
        return if (coords.getAltitude() == null) {
            factory.createPoint(Coordinate(coords.getLongitude()!!, coords.getLatitude()!!))
        } else {
            factory.createPoint(Coordinate(coords.getLongitude()!!, coords.getLatitude()!!, coords.getAltitude()!!))
        }
    }

    /**
     * Converts [MultiPointProxy] to JTS [MultiPoint]
     *
     * @param proxy [MultiPointProxy] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPoint(proxy: MultiPointProxy): MultiPoint {
        require(proxy.coordinates != null)
        return toJtsMultiPoint(proxy.coordinates!!)
    }

    /**
     * Converts [MultiPointCoordsProxy] to JTS [MultiPoint]
     *
     * @param coords [MultiPointCoordsProxy] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPoint(coords: MultiPointCoordsProxy): MultiPoint {
        val points = coords.map { toJtsPoint(it!!) }.toTypedArray()
        return factory.createMultiPoint(points)
    }

    /**
     * Converts [LineStringCoordsProxy] to JTS [LineString]
     *
     * @param coords [LineStringCoordsProxy] to convert
     * @return [LineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsLineString(coords: LineStringCoordsProxy): LineString {
        val points = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLineString(points)
    }

    /**
     * Converts [LineStringProxy] to JTS [LineString]
     *
     * @param coords [LineStringProxy] to convert
     * @return [LineString]
     */
    fun toJtsLineString(proxy: LineStringProxy): LineString {
        require(proxy.coordinates != null)
        return toJtsLineString(proxy.coordinates!!)
    }

    /**
     * Converts [PointCoordsProxy] to JTS [Coordinate] with or without altitude.
     *
     * @param coords [PointCoordsProxy] to convert
     * @return [MultiPoint]
     */
    fun toJtsCoordinate(coords: PointCoordsProxy): Coordinate {
        return if (coords.getAltitude() == null) {
            Coordinate(coords.getLongitude()!!, coords.getLatitude()!!)
        } else {
            Coordinate(coords.getLongitude()!!, coords.getLatitude()!!, coords.getAltitude()!!)
        }
    }

    /**
     * Converts [PolygonCoordsProxy] to JTS [Polygon]
     *
     * @param coords [PolygonCoordsProxy] to convert
     * @return [Polygon]
     */
    fun toJtsPolygon(coords: PolygonCoordsProxy): Polygon {
        if (coords.size == 0) {
            return factory.createPolygon()
        }

        val outerRing = coords[0]!!
        val jtsOuterRing = toJtsLinearRing(outerRing)

        if (coords.size > 1) {
            val jtsHoles = mutableListOf<LinearRing>()
            for (i in 1 until coords.size) {
                val jtsHole = toJtsLinearRing(coords[i]!!)
                jtsHoles.add(jtsHole)
            }
            return factory.createPolygon(jtsOuterRing, jtsHoles.toTypedArray())
        } else {
            return factory.createPolygon(jtsOuterRing)
        }
    }

    /**
     * Converts [PolygonProxy] to JTS [Polygon]
     *
     * @param proxy [PolygonProxy] to convert
     * @return [Polygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsPolygon(proxy: PolygonProxy): Polygon {
        require(proxy.coordinates != null)
        return toJtsPolygon(proxy.coordinates!!)
    }

    /**
     * Converts [PolygonCoordsProxy] to JTS [MultiLineString]
     *
     * @param coords [PolygonCoordsProxy] to convert
     * @return [MultiLineString]
     */
    fun toJtsMultiLineString(coords: MultiLineStringCoordsProxy): MultiLineString {
        if (coords.size == 0) {
            return factory.createMultiLineString()
        }

        val jtsLineStringArray = coords.map { toJtsLineString(it!!) }.toTypedArray()
        return factory.createMultiLineString(jtsLineStringArray)
    }

    /**
     * Converts [MultiLineStringProxy] to JTS [MultiLineString]
     *
     * @param proxy [MultiLineStringProxy] to convert
     * @return [MultiLineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiLineString(proxy: MultiLineStringProxy): MultiLineString {
        require(proxy.coordinates != null)
        return toJtsMultiLineString(proxy.coordinates!!)
    }

    /**
     * Converts [MultiPolygonCoordsProxy] to JTS [MultiPolygon]
     *
     * @param coords [MultiPolygonCoordsProxy] to convert
     * @return [MultiPolygon]
     */
    fun toJtsMultiPolygon(coords: MultiPolygonCoordsProxy): MultiPolygon {
        if (coords.size == 0) {
            return factory.createMultiPolygon()
        }

        val jtsPolygonArray = coords.map { toJtsPolygon(it!!) }.toTypedArray()
        return factory.createMultiPolygon(jtsPolygonArray)
    }

    /**
     * Converts [MultiPolygonProxy] to JTS [MultiPolygon]
     *
     * @param proxy [MultiPolygonProxy] to convert
     * @return [MultiPolygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPolygon(proxy: MultiPolygonProxy): MultiPolygon {
        require(proxy.coordinates != null)
        return toJtsMultiPolygon(proxy.coordinates!!)
    }

    /**
     * Converts [LineStringCoordsProxy] to JTS [LinearRing]
     *
     * @param coords [LineStringCoordsProxy] to convert
     * @return [LinearRing]
     */
    fun toJtsLinearRing(coords: LineStringCoordsProxy): LinearRing {
        if (coords.isEmpty()) {
            return factory.createLinearRing()
        }
        val jtsCoordinateArray = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLinearRing(jtsCoordinateArray)
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