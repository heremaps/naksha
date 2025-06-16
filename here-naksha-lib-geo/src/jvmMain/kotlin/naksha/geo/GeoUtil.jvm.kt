package naksha.geo

import naksha.base.illegalArg
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.ByteOrderValues
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import org.locationtech.jts.io.twkb.TWKBReader
import org.locationtech.jts.io.twkb.TWKBWriter

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class GeoUtil private actual constructor() {
    actual companion object GeoUtil_C {

        // ----------------------------------< JAVA only >----------------------------------------

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
        @JvmStatic
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
         * Converts JTS [Array<Coordinate>] into [LinearRingCoord]
         *
         * @param jtsCoords - JTS [Array<Coordinate>]
         * @return [LinearRingCoord]
         */
        @JvmStatic
        fun toLinearRingCoord(jtsCoords: Array<Coordinate>): LinearRingCoord {
            return LinearRingCoord(*jtsCoords.map(::toPointCoord).toTypedArray())
        }

        /**
         * Converts JTS [Array<LinearRing>] into [LinearRingCoord]
         *
         * @param linearRings - JTS [Array<LinearRing>]
         * @return [LinearRingCoord]
         */
        @JvmStatic
        fun toLinearRingCoordArray(linearRings: Array<LinearRing>): Array<LinearRingCoord> {
            return linearRings.map { toLinearRingCoord(it.coordinates) }.toTypedArray()
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
            if (jtsCoords.isEmpty()) throw illegalArg("Empty polygon")
            return PolygonCoord(*toLinearRingCoordArray(jtsCoords))
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
            return MultiPolygonCoord(*polygons.map { it.coordinates }.toTypedArray())
        }

        /**
         * Converts [PointCoord] to JTS [Coordinate] with or without altitude.
         *
         * @param point [PointCoord] to convert
         * @return [Coordinate]
         */
        @JvmStatic
        fun toJtsCoordinate(point: PointCoord): Coordinate {
            val lon = point.longitude
            val lat = point.latitude
            val z = point.z
            return if (z != null) Coordinate(lon, lat, z) else CoordinateXY(lon, lat)
        }

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
            if (geometry.isPoint()) return toJtsPoint(geometry.asPoint())
            if (geometry.isMultiPoint()) return toJtsMultiPoint(geometry.asMultiPoint())
            if (geometry.isLineString()) return toJtsLineString(geometry.asLineString())
            if (geometry.isMultiLineString()) return toJtsMultiLineString(geometry.asMultiLineString())
            if (geometry.isPolygon()) return toJtsPolygon(geometry.asPolygon())
            if (geometry.isMultiPolygon()) return toJtsMultiPolygon(geometry.asMultiPolygon())
            if (geometry.isGeometryCollection()) return toJtsGeometryCollection(geometry.asGeometryCollection())
            throw IllegalArgumentException("Unknown geometry type ${geometry.type}")
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
        fun toJtsPoint(geometry: SpPoint): Point = toJtsPoint(geometry.coordinates)

        /**
         * Converts [PointCoord] to JTS [Point]
         *
         * @param coords [PointCoord] to convert
         * @return [Point]
         */
        @JvmStatic
        fun toJtsPoint(coords: PointCoord): Point {
            val jtsCoord = toJtsCoordinate(coords)
            return factory.createPoint(jtsCoord)
        }

        /**
         * Converts [SpMultiPoint] to JTS [MultiPoint]
         *
         * @param geometry [SpMultiPoint] to convert
         * @return [MultiPoint]
         * @throws [RuntimeException] when proxy has null coordinates
         */
        @JvmStatic
        fun toJtsMultiPoint(geometry: SpMultiPoint): MultiPoint = toJtsMultiPoint(geometry.coordinates)

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
        fun toJtsLineString(geometry: SpLineString): LineString = toJtsLineString(geometry.coordinates)

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
        fun toJtsPolygon(geometry: SpPolygon): Polygon = toJtsPolygon(geometry.coordinates)

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
            toJtsMultiLineString(geometry.coordinates)

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
        fun toJtsMultiPolygon(geometry: SpMultiPolygon): MultiPolygon = toJtsMultiPolygon(geometry.coordinates)

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

        // ----------------------------------< ACTUAL >-------------------------------------------
        /**
         * Decode a TWKB GeoJSON geometry from encoded bytes.
         * @param raw the TWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun fromTWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            val reader = TWKBReader(GeometryFactory(PrecisionModel(), 4326))
            val jtsGeometry = reader.read(raw)
            return toProxyGeometry(jtsGeometry)
        }

        /**
         * Decode a EWKB GeoJSON geometry from encoded bytes.
         * @param raw the EWKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun fromEWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            val reader = WKBReader(GeometryFactory(PrecisionModel(), 4326))
            val jtsGeometry = reader.read(raw)
            return toProxyGeometry(jtsGeometry)
        }

        /**
         * Decode a WKB GeoJSON geometry from encoded bytes.
         * @param raw the WKB.
         * @return the GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun fromWKB(raw: ByteArray?): SpGeometry? {
            if (raw == null) return null
            val reader = WKBReader(GeometryFactory(PrecisionModel(), 4326))
            val jtsGeometry = reader.read(raw)
            return toProxyGeometry(jtsGeometry)
        }

        /**
         * Encodes the given GeoJSON geometry into TWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun toTWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            val writer = TWKBWriter()
            writer.setXYPrecision(7)
            val coordinates = geometry.coordinates
            if (coordinates.hasZ()) {
                writer.setEncodeZ(true)
                writer.setZPrecision(7)
            } else {
                writer.setEncodeZ(false)
            }
            if (coordinates.hasM()) {
                writer.setEncodeM(true)
                writer.setMPrecision(7)
            } else {
                writer.setEncodeM(false)
            }
            val jtsGeometry = toJtsGeometry(geometry)
            return writer.write(jtsGeometry)
        }

        /**
         * Encodes the given GeoJSON geometry into EWKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun toEWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            val writer = WKBWriter(3, ByteOrderValues.BIG_ENDIAN, true)
            return writer.write(toJtsGeometry(geometry))
        }

        /**
         * Encodes the given GeoJSON geometry into WKB bytes.
         * @param geometry the geometry to encode.
         * @return the encoded GeoJSON geometry.
         * @since 3.0.0
         */
        @JvmStatic
        actual fun toWKB(geometry: SpGeometry?): ByteArray? {
            if (geometry == null) return null
            val writer = WKBWriter(3, ByteOrderValues.BIG_ENDIAN, true)
            return writer.write(toJtsGeometry(geometry))
        }
    }
}