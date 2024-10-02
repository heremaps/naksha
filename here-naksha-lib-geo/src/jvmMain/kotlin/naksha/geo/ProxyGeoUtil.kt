package naksha.geo

import org.locationtech.jts.geom.*

@Suppress("MemberVisibilityCanBePrivate")
object ProxyGeoUtil {

    private val factory: GeometryFactory = GeometryFactory(PrecisionModel(), 4326)

    /**
     * Converts JTS [Geometry] into [SpGeometry]
     *
     * @param jtsGeometry
     * @return [SpGeometry]
     */
    @JvmStatic
    fun toProxyGeometry(jtsGeometry: Geometry): SpGeometry {
        return when (jtsGeometry) {
            is Point -> toPoint(jtsGeometry)
            is MultiPoint -> toMultiPoint(jtsGeometry)
            is LineString -> toLineString(jtsGeometry)
            is Polygon -> toPolygon(jtsGeometry)
            is MultiPolygon -> toMultiPolygon(jtsGeometry)
            is MultiLineString -> toMultiLineString(jtsGeometry)
            is GeometryCollection -> toGeometryCollection(jtsGeometry)
            else -> throw IllegalArgumentException("Unsupported geometry ${jtsGeometry.geometryType}")
        }
    }

    /**
     * Converts JTS [GeometryCollection] into [SpGeometryCollection]
     *
     * @param jtsGeometry - JTS [GeometryCollection]
     * @return [SpGeometryCollection]
     */
    fun toGeometryCollection(jtsGeometry: GeometryCollection): SpGeometryCollection {
        val geometries = SpGeometryList()
        for (i in 0..<jtsGeometry.numGeometries) {
            val proxyGeometry = toProxyGeometry(jtsGeometry.getGeometryN(i))
            geometries.add(proxyGeometry)
        }
        return SpGeometryCollection(geometries)
    }

    /**
     * Converts JTS [Coordinate] into [PointCoord]
     *
     * @param coords - JTS [Coordinate]
     * @return [PointCoord]
     */
    @JvmStatic
    fun toPointCoord(coords: Coordinate): PointCoord {
        return if (!coords.m.isNaN()) {
            PointCoord(coords.x, coords.y, coords.z, coords.m)
        } else if (!coords.z.isNaN()) {
            PointCoord(coords.x, coords.y, coords.z)
        } else {
            PointCoord(coords.x, coords.y)
        }
    }

    /**
     * Converts JTS [Point] into [SpPoint]
     *
     * @param jtsPoint - JTS [Point]
     * @return [SpPoint]
     */
    @JvmStatic
    fun toPoint(jtsPoint: Point): SpPoint {
        return SpPoint().withCoordinates(toPointCoord(jtsPoint.coordinate))
    }

    /**
     * Converts JTS [MultiPoint] into [SpMultiPoint]
     *
     * @param jtsMultiPoint - JTS [MultiPoint]
     * @return [SpMultiPoint]
     */
    @JvmStatic
    fun toMultiPoint(jtsMultiPoint: MultiPoint): SpMultiPoint {
        return SpMultiPoint().withCoordinates(toMultiPointCoord(jtsMultiPoint.coordinates))
    }

    /**
     * Converts JTS [Array<Coordinate>] into [MultiPointCoord]
     *
     * @param jtsCoords - JTS [Array<Coordinate>]
     * @return [MultiPointCoord]
     */
    @JvmStatic
    fun toMultiPointCoord(jtsCoords: Array<Coordinate>): MultiPointCoord {
        return MultiPointCoord(*jtsCoords.map(::toPointCoord).toTypedArray())
    }

    /**
     * Converts JTS [LineString] into [SpLineString]
     *
     * @param jtsLineString - JTS [LineString]
     * @return [SpLineString]
     */
    @JvmStatic
    fun toLineString(jtsLineString: LineString): SpLineString {
        return SpLineString().withCoordinates(toLineStringCoord(jtsLineString.coordinates))
    }

    /**
     * Converts JTS [Array<Coordinate>] into [LineStringCoord]
     *
     * @param jtsCoords - JTS [Array<Coordinate>]
     * @return [LineStringCoord]
     */
    @JvmStatic
    fun toLineStringCoord(jtsCoords: Array<Coordinate>): LineStringCoord {
        return LineStringCoord(*jtsCoords.map(::toPointCoord).toTypedArray())
    }

    /**
     * Converts JTS [Array<LinearRing>] into [LineStringCoord]
     *
     * @param linearRings - JTS [Array<LinearRing>]
     * @return [LineStringCoord]
     */
    @JvmStatic
    fun toLinearRingCoord(linearRings: Array<LinearRing>): Array<LineStringCoord> {
        return linearRings.map { toLineStringCoord(it.coordinates) }.toTypedArray()
    }

    /**
     * Converts JTS [Polygon] into [SpPolygon]
     *
     * @param jtsPolygon - JTS [Polygon]
     * @return [SpPolygon]
     */
    @JvmStatic
    fun toPolygon(jtsPolygon: Polygon): SpPolygon {
        val polygonRings = mutableListOf(jtsPolygon.exteriorRing)
        for (i in 0..<jtsPolygon.numInteriorRing) {
            polygonRings.add(jtsPolygon.getInteriorRingN(i))
        }
        return SpPolygon().withCoordinates(toPolygonCoord(polygonRings.toTypedArray()))
    }

    /**
     * Converts JTS [Array<LinearRing>] into [PolygonCoord]
     *
     * @param jtsCoords - JTS [Array<Coordinate>]
     * @return [PolygonCoord]
     */
    @JvmStatic
    fun toPolygonCoord(jtsCoords: Array<LinearRing>): PolygonCoord {
        return PolygonCoord(*toLinearRingCoord(jtsCoords))
    }

    /**
     * Converts JTS [MultiLineString] into [SpMultiLineString]
     *
     * @param jtsMultiLineString - JTS [MultiLineString]
     * @return [SpMultiLineString]
     */
    @JvmStatic
    fun toMultiLineString(jtsMultiLineString: MultiLineString): SpMultiLineString {
        val lineStrings = Array(jtsMultiLineString.numGeometries) {
            toLineStringCoord(jtsMultiLineString.getGeometryN(it).coordinates)
        }
        return SpMultiLineString().withCoordinates(toMultiLineStringCoord(lineStrings))
    }

    @JvmStatic
    fun toMultiLineStringCoord(jtsCoords: Array<LineStringCoord>): MultiLineStringCoord {
        return MultiLineStringCoord(*jtsCoords)
    }

    /**
     * Converts JTS [MultiPolygon] into [SpMultiPolygon]
     *
     * @param jtsMultiPolygon - JTS [MultiPolygon]
     * @return [SpMultiPolygon]
     */
    @JvmStatic
    fun toMultiPolygon(jtsMultiPolygon: MultiPolygon): SpMultiPolygon {
        val polygons = Array(jtsMultiPolygon.numGeometries) {
            toPolygon(jtsMultiPolygon.getGeometryN(it) as Polygon)
        }
        return SpMultiPolygon().withCoordinates(toMultiPolygonCoord(polygons))
    }

    @JvmStatic
    fun toMultiPolygonCoord(polygons: Array<SpPolygon>): MultiPolygonCoord {
        return MultiPolygonCoord(*polygons.map { it.getCoordinates() }.toTypedArray())
    }

    /**
     * Converts [PointCoord] to JTS [Coordinate] with or without altitude.
     *
     * @param coords [PointCoord] to convert
     * @return [Coordinate]
     */
    @JvmStatic
    fun toJtsCoordinate(coords: PointCoord): Coordinate =
        if (coords.hasAltitude())
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
    @JvmStatic
    fun toJtsGeometry(geometry: SpGeometry): Geometry {
        return when (geometry.type) {
            Point.TYPENAME_POINT -> toJtsPoint(geometry.asPoint())
            Point.TYPENAME_MULTIPOINT -> toJtsMultiPoint(geometry.asMultiPoint())
            Point.TYPENAME_LINESTRING -> toJtsLineString(geometry.asLineString())
            Point.TYPENAME_MULTILINESTRING -> toJtsMultiLineString(geometry.asMultiLineString())
            Point.TYPENAME_POLYGON -> toJtsPolygon(geometry.asPolygon())
            Point.TYPENAME_MULTIPOLYGON -> toJtsMultiPolygon(geometry.asMultiPolygon())
            Point.TYPENAME_GEOMETRYCOLLECTION -> toJtsGeometryCollection(geometry.asGeometryCollection())
            else -> throw IllegalArgumentException("Unknown proxy type ${geometry::class.simpleName}")
        }
    }

    /**
     * Converts [SpGeometryCollection] to JTS [GeometryCollection]
     *
     * @param geometryCollection [SpGeometryCollection] to convert
     * @return [GeometryCollection]
     */
    private fun toJtsGeometryCollection(geometryCollection: SpGeometryCollection): GeometryCollection =
        geometryCollection.geometries
            ?.map { toJtsGeometry(it!!) }
            ?.let { GeometryCollection(it.toTypedArray(), factory) }
            ?: GeometryCollection(emptyArray(), factory)

    /**
     * Converts [SpPoint] to JTS [Point]
     *
     * @param geometry [SpPoint] to convert
     * @return [Point]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsPoint(geometry: SpPoint): Point = toJtsPoint(geometry.getCoordinates())

    /**
     * Converts [PointCoord] to JTS [Point]
     *
     * @param coords [PointCoord] to convert
     * @return [Point]
     */
    @JvmStatic
    fun toJtsPoint(coords: PointCoord): Point = factory.createPoint(toJtsCoordinate(coords))

    /**
     * Converts [SpMultiPoint] to JTS [MultiPoint]
     *
     * @param geometry [SpMultiPoint] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiPoint(geometry: SpMultiPoint): MultiPoint = toJtsMultiPoint(geometry.getCoordinates())

    /**
     * Converts [MultiPointCoord] to JTS [MultiPoint]
     *
     * @param coords [MultiPointCoord] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiPoint(coords: MultiPointCoord): MultiPoint {
        val points = coords.map { toJtsPoint(it!!) }.toTypedArray()
        return factory.createMultiPoint(points)
    }

    /**
     * Converts [SpLineString] to JTS [LineString]
     *
     * @param geometry [SpLineString] to convert
     * @return [LineString]
     */
    @JvmStatic
    fun toJtsLineString(geometry: SpLineString): LineString = toJtsLineString(geometry.getCoordinates())

    /**
     * Converts [LineStringCoord] to JTS [LineString]
     *
     * @param coords [LineStringCoord] to convert
     * @return [LineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsLineString(coords: LineStringCoord): LineString {
        val points = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLineString(points)
    }

    /**
     * Converts [SpPolygon] to JTS [Polygon]
     *
     * @param geometry [SpPolygon] to convert
     * @return [Polygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsPolygon(geometry: SpPolygon): Polygon = toJtsPolygon(geometry.getCoordinates())

    /**
     * Converts [PolygonCoord] to JTS [Polygon]
     *
     * @param coords [PolygonCoord] to convert
     * @return [Polygon]
     */
    @JvmStatic
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
     * Converts [SpMultiLineString] to JTS [MultiLineString]
     *
     * @param geometry [SpMultiLineString] to convert
     * @return [MultiLineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiLineString(geometry: SpMultiLineString): MultiLineString =
        toJtsMultiLineString(geometry.getCoordinates())

    /**
     * Converts [MultiLineStringCoord] to JTS [MultiLineString]
     *
     * @param coords [MultiLineStringCoord] to convert
     * @return [MultiLineString]
     */
    @JvmStatic
    fun toJtsMultiLineString(coords: MultiLineStringCoord): MultiLineString {
        if (coords.size == 0) {
            return factory.createMultiLineString()
        }

        val jtsLineStringArray = coords.map { toJtsLineString(it!!) }.toTypedArray()
        return factory.createMultiLineString(jtsLineStringArray)
    }

    /**
     * Converts [SpMultiPolygon] to JTS [MultiPolygon]
     *
     * @param geometry [SpMultiPolygon] to convert
     * @return [MultiPolygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiPolygon(geometry: SpMultiPolygon): MultiPolygon = toJtsMultiPolygon(geometry.getCoordinates())

    /**
     * Converts [MultiPolygonCoord] to JTS [MultiPolygon]
     *
     * @param coords [MultiPolygonCoord] to convert
     * @return [MultiPolygon]
     */
    @JvmStatic
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
    @JvmStatic
    fun toJtsLinearRing(coords: LineStringCoord): LinearRing {
        if (coords.isEmpty()) {
            return factory.createLinearRing()
        }
        val jtsCoordinateArray = coords.map { toJtsCoordinate(it!!) }.toTypedArray()
        return factory.createLinearRing(jtsCoordinateArray)
    }
}
