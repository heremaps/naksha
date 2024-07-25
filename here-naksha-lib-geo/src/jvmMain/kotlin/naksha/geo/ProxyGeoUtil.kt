package naksha.geo

import org.locationtech.jts.geom.*

@Suppress("MemberVisibilityCanBePrivate")
object ProxyGeoUtil {

    private val factory: GeometryFactory = GeometryFactory(PrecisionModel(), 4326)

    /**
     * Converts JTS [Geometry] into [GeometryProxy]
     *
     * @param jtsGeometry
     * @return [GeometryProxy]
     */
    @JvmStatic
    fun toProxyGeometry(jtsGeometry: Geometry): GeometryProxy {
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
     * Converts JTS [GeometryCollection] into [GeometryCollectionProxy]
     *
     * @param jtsGeometry - JTS [GeometryCollection]
     * @return [GeometryCollectionProxy]
     */
    fun toGeometryCollection(jtsGeometry: GeometryCollection): GeometryCollectionProxy {
        val geometries = GeometriesProxy()
        for (i in 0..<jtsGeometry.numGeometries) {
            val proxyGeometry = toProxyGeometry(jtsGeometry.getGeometryN(i))
            geometries.add(proxyGeometry)
        }
        return GeometryCollectionProxy(geometries)
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
     * Converts JTS [Point] into [PointGeometry]
     *
     * @param jtsPoint - JTS [Point]
     * @return [PointGeometry]
     */
    @JvmStatic
    fun toPoint(jtsPoint: Point): PointGeometry {
        return PointGeometry().withCoordinates(toPointCoord(jtsPoint.coordinate))
    }

    /**
     * Converts JTS [MultiPoint] into [MultiPointGeometry]
     *
     * @param jtsMultiPoint - JTS [MultiPoint]
     * @return [MultiPointGeometry]
     */
    @JvmStatic
    fun toMultiPoint(jtsMultiPoint: MultiPoint): MultiPointGeometry {
        return MultiPointGeometry().withCoordinates(toMultiPointCoord(jtsMultiPoint.coordinates))
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
     * Converts JTS [LineString] into [LineStringGeometry]
     *
     * @param jtsLineString - JTS [LineString]
     * @return [LineStringGeometry]
     */
    @JvmStatic
    fun toLineString(jtsLineString: LineString): LineStringGeometry {
        return LineStringGeometry().withCoordinates(toLineStringCoord(jtsLineString.coordinates))
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
     * Converts JTS [Polygon] into [PolygonGeometry]
     *
     * @param jtsPolygon - JTS [Polygon]
     * @return [PolygonGeometry]
     */
    @JvmStatic
    fun toPolygon(jtsPolygon: Polygon): PolygonGeometry {
        val polygonRings = mutableListOf(jtsPolygon.exteriorRing)
        for (i in 0..<jtsPolygon.numInteriorRing) {
            polygonRings.add(jtsPolygon.getInteriorRingN(i))
        }
        return PolygonGeometry().withCoordinates(toPolygonCoord(polygonRings.toTypedArray()))
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
     * Converts JTS [MultiLineString] into [MultiLineStringGeometry]
     *
     * @param jtsMultiLineString - JTS [MultiLineString]
     * @return [MultiLineStringGeometry]
     */
    @JvmStatic
    fun toMultiLineString(jtsMultiLineString: MultiLineString): MultiLineStringGeometry {
        val lineStrings = Array(jtsMultiLineString.numGeometries) {
            toLineStringCoord(jtsMultiLineString.getGeometryN(it).coordinates)
        }
        return MultiLineStringGeometry().withCoordinates(toMultiLineStringCoord(lineStrings))
    }

    @JvmStatic
    fun toMultiLineStringCoord(jtsCoords: Array<LineStringCoord>): MultiLineStringCoord {
        return MultiLineStringCoord(*jtsCoords)
    }

    /**
     * Converts JTS [MultiPolygon] into [MultiPolygonGeometry]
     *
     * @param jtsMultiPolygon - JTS [MultiPolygon]
     * @return [MultiPolygonGeometry]
     */
    @JvmStatic
    fun toMultiPolygon(jtsMultiPolygon: MultiPolygon): MultiPolygonGeometry {
        val polygons = Array(jtsMultiPolygon.numGeometries) {
            toPolygon(jtsMultiPolygon.getGeometryN(it) as Polygon)
        }
        return MultiPolygonGeometry().withCoordinates(toMultiPolygonCoord(polygons))
    }

    @JvmStatic
    fun toMultiPolygonCoord(polygons: Array<PolygonGeometry>): MultiPolygonCoord {
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
    fun toJtsGeometry(geometry: GeometryProxy): Geometry {
        return when (geometry.type) {
            GeoType.Point.toString() -> toJtsPoint(geometry.asPoint())
            GeoType.MultiPoint.toString() -> toJtsMultiPoint(geometry.asMultiPoint())
            GeoType.LineString.toString() -> toJtsLineString(geometry.asLineString())
            GeoType.MultiLineString.toString() -> toJtsMultiLineString(geometry.asMultiLineString())
            GeoType.Polygon.toString() -> toJtsPolygon(geometry.asPolygon())
            GeoType.MultiPolygon.toString() -> toJtsMultiPolygon(geometry.asMultiPolygon())
            GeoType.GeometryCollection.toString() -> toJtsGeometryCollection(geometry.asGeometryCollection())
            else -> throw IllegalArgumentException("Unknown proxy type ${geometry::class.simpleName}")
        }
    }

    /**
     * Converts [GeometryCollectionProxy] to JTS [GeometryCollection]
     *
     * @param geometryCollection [GeometryCollectionProxy] to convert
     * @return [GeometryCollection]
     */
    private fun toJtsGeometryCollection(geometryCollection: GeometryCollectionProxy): GeometryCollection =
        geometryCollection.geometries
            ?.map { toJtsGeometry(it!!) }
            ?.let { GeometryCollection(it.toTypedArray(), factory) }
            ?: GeometryCollection(emptyArray(), factory)

    /**
     * Converts [PointGeometry] to JTS [Point]
     *
     * @param geometry [PointGeometry] to convert
     * @return [Point]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsPoint(geometry: PointGeometry): Point = toJtsPoint(geometry.getCoordinates())

    /**
     * Converts [PointCoord] to JTS [Point]
     *
     * @param coords [PointCoord] to convert
     * @return [Point]
     */
    @JvmStatic
    fun toJtsPoint(coords: PointCoord): Point = factory.createPoint(toJtsCoordinate(coords))

    /**
     * Converts [MultiPointGeometry] to JTS [MultiPoint]
     *
     * @param geometry [MultiPointGeometry] to convert
     * @return [MultiPoint]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiPoint(geometry: MultiPointGeometry): MultiPoint = toJtsMultiPoint(geometry.getCoordinates())

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
     * Converts [LineStringGeometry] to JTS [LineString]
     *
     * @param geometry [LineStringGeometry] to convert
     * @return [LineString]
     */
    @JvmStatic
    fun toJtsLineString(geometry: LineStringGeometry): LineString = toJtsLineString(geometry.getCoordinates())

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
     * Converts [PolygonGeometry] to JTS [Polygon]
     *
     * @param geometry [PolygonGeometry] to convert
     * @return [Polygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsPolygon(geometry: PolygonGeometry): Polygon = toJtsPolygon(geometry.getCoordinates())

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
     * Converts [MultiLineStringGeometry] to JTS [MultiLineString]
     *
     * @param geometry [MultiLineStringGeometry] to convert
     * @return [MultiLineString]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiLineString(geometry: MultiLineStringGeometry): MultiLineString =
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
     * Converts [MultiPolygonGeometry] to JTS [MultiPolygon]
     *
     * @param geometry [MultiPolygonGeometry] to convert
     * @return [MultiPolygon]
     * @throws [RuntimeException] when proxy has null coordinates
     */
    @JvmStatic
    fun toJtsMultiPolygon(geometry: MultiPolygonGeometry): MultiPolygon = toJtsMultiPolygon(geometry.getCoordinates())

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
