package naksha.geo

import org.locationtech.jts.geom.*

@Suppress("MemberVisibilityCanBePrivate")
object ProxyGeoUtil {

    private val factory: GeometryFactory = GeometryFactory(PrecisionModel(), 4326)

    /**
     * Converts [PointCoord] to JTS [Coordinate] with or without altitude.
     *
     * @param coords [PointCoord] to convert
     * @return [Coordinate]
     */
    fun toJtsCoordinate(coords: PointCoord): Coordinate =
        if(coords.hasAltitude())
            Coordinate(coords.getLongitude(), coords.getLatitude())
        else
            Coordinate(coords.getLongitude(), coords.getLatitude(), coords.getAltitude())

    /**
     * Converts proxy model to JTS [Geometry] using [factory] with default SRID: 4326
     *
     * @param geometry - proxy geometry to convert
     * @return JTS Geometry
     * @throws [IllegalArgumentException] when proxy type is not supported
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsGeometry(geometry: GeometryProxy): Geometry {
        return when (geometry.type) {
            GeoType.Point.toString() -> toJtsPoint(geometry.asPoint())
            GeoType.MultiPoint.toString() -> toJtsMultiPoint(geometry.asMultiPoint())
            GeoType.LineString.toString() -> toJtsLineString(geometry.asLineString())
            GeoType.MultiLineString.toString() -> toJtsMultiLineString(geometry.asMultiLineString())
            GeoType.Polygon.toString() -> toJtsPolygon(geometry.asPolygon())
            GeoType.MultiPolygon.toString() -> toJtsMultiPolygon(geometry.asMultiPolygon())
            else -> throw IllegalArgumentException("Unknown proxy type ${geometry::class.simpleName}")
        }
    }

    /**
     * Converts [PointGeometry] to JTS [Point]
     *
     * @param geometry [PointGeometry] to convert
     * @return [Point]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsPoint(geometry: PointGeometry): Point = toJtsPoint(geometry.getCoordinates())

    /**
     * Converts [PointCoord] to JTS [Point]
     *
     * @param coords [PointCoord] to convert
     * @return [Point]
     */
    fun toJtsPoint(coords: PointCoord): Point = factory.createPoint(toJtsCoordinate(coords))

    /**
     * Converts [MultiPointGeometry] to JTS [MultiPoint]
     *
     * @param geometry [MultiPointGeometry] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPoint(geometry: MultiPointGeometry): MultiPoint = toJtsMultiPoint(geometry.getCoordinates())

    /**
     * Converts [MultiPointCoord] to JTS [MultiPoint]
     *
     * @param coords [MultiPointCoord] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPoint(coords: MultiPointCoord): MultiPoint {
        val points = coords.map { toJtsPoint(it!!) }.toTypedArray()
        return factory.createMultiPoint(points)
    }

    /**
     * Converts [LineStringGeometry] to JTS [LineString]
     *
     * @param geometry [LineStringGeometry] to convert
     * @return [LineString]
     */
    fun toJtsLineString(geometry: LineStringGeometry): LineString = toJtsLineString(geometry.getCoordinates())

    /**
     * Converts [LineStringCoord] to JTS [LineString]
     *
     * @param coords [LineStringCoord] to convert
     * @return [LineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsLineString(coords: LineStringCoord): LineString {
        val points = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLineString(points)
    }

    /**
     * Converts [PolygonGeometry] to JTS [Polygon]
     *
     * @param geometry [PolygonGeometry] to convert
     * @return [Polygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsPolygon(geometry: PolygonGeometry): Polygon = toJtsPolygon(geometry.getCoordinates())

    /**
     * Converts [PolygonCoord] to JTS [Polygon]
     *
     * @param coords [PolygonCoord] to convert
     * @return [Polygon]
     */
    fun toJtsPolygon(coords: PolygonCoord): Polygon {
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
     * Converts [MultiLineStringGeometry] to JTS [MultiLineString]
     *
     * @param geometry [MultiLineStringGeometry] to convert
     * @return [MultiLineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiLineString(geometry: MultiLineStringGeometry): MultiLineString = toJtsMultiLineString(geometry.getCoordinates())

    /**
     * Converts [MultiLineStringCoord] to JTS [MultiLineString]
     *
     * @param coords [MultiLineStringCoord] to convert
     * @return [MultiLineString]
     */
    fun toJtsMultiLineString(coords: MultiLineStringCoord): MultiLineString {
        if (coords.size == 0) {
            return factory.createMultiLineString()
        }

        val jtsLineStringArray = coords.map { toJtsLineString(it!!) }.toTypedArray()
        return factory.createMultiLineString(jtsLineStringArray)
    }

    /**
     * Converts [MultiPolygonGeometry] to JTS [MultiPolygon]
     *
     * @param geometry [MultiPolygonGeometry] to convert
     * @return [MultiPolygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    fun toJtsMultiPolygon(geometry: MultiPolygonGeometry): MultiPolygon = toJtsMultiPolygon(geometry.getCoordinates())

    /**
     * Converts [MultiPolygonCoord] to JTS [MultiPolygon]
     *
     * @param coords [MultiPolygonCoord] to convert
     * @return [MultiPolygon]
     */
    fun toJtsMultiPolygon(coords: MultiPolygonCoord): MultiPolygon {
        if (coords.size == 0) {
            return factory.createMultiPolygon()
        }

        val jtsPolygonArray = coords.map { toJtsPolygon(it!!) }.toTypedArray()
        return factory.createMultiPolygon(jtsPolygonArray)
    }

    /**
     * Converts [LineStringCoord] to JTS [LinearRing]
     *
     * @param coords [LineStringCoord] to convert
     * @return [LinearRing]
     */
    fun toJtsLinearRing(coords: LineStringCoord): LinearRing {
        if (coords.isEmpty()) {
            return factory.createLinearRing()
        }
        val jtsCoordinateArray = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLinearRing(jtsCoordinateArray)
    }
}
