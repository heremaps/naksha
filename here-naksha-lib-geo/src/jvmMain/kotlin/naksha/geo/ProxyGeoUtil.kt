package naksha.geo

import org.locationtech.jts.geom.*

@Deprecated(message = "Replaced by GeoUtil", replaceWith = ReplaceWith("GeoUtil"), level = DeprecationLevel.ERROR)
object ProxyGeoUtil {

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toProxyGeometry(jtsGeometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toProxyGeometry(jtsGeometry: Geometry): SpGeometry = GeoUtil.toProxyGeometry(jtsGeometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toGeometryCollection(jtsGeometry)"),
        level = DeprecationLevel.ERROR
    )
    fun toGeometryCollection(jtsGeometry: GeometryCollection): SpGeometryCollection = GeoUtil.toGeometryCollection(jtsGeometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toPointCoord(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toPointCoord(coords: Coordinate): PointCoord = GeoUtil.toPointCoord(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toPoint(jtsPoint)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toPoint(jtsPoint: Point): SpPoint = GeoUtil.toPoint(jtsPoint)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiPoint(jtsMultiPoint)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiPoint(jtsMultiPoint: MultiPoint): SpMultiPoint = GeoUtil.toMultiPoint(jtsMultiPoint)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiPointCoord(jtsCoords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiPointCoord(jtsCoords: Array<Coordinate>): MultiPointCoord = GeoUtil.toMultiPointCoord(jtsCoords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toLineString(jtsLineString)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toLineString(jtsLineString: LineString): SpLineString = GeoUtil.toLineString(jtsLineString)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toLineStringCoord(jtsCoords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toLineStringCoord(jtsCoords: Array<Coordinate>): LineStringCoord = GeoUtil.toLineStringCoord(jtsCoords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toLinearRingCoord(linearRings)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toLinearRingCoord(linearRings: Array<LinearRing>): Array<LineStringCoord> = GeoUtil.toLinearRingCoord(linearRings)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toPolygon(jtsPolygon)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toPolygon(jtsPolygon: Polygon): SpPolygon = GeoUtil.toPolygon(jtsPolygon)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toPolygonCoord(jtsCoords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toPolygonCoord(jtsCoords: Array<LinearRing>): PolygonCoord = GeoUtil.toPolygonCoord(jtsCoords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiLineString(jtsMultiLineString)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiLineString(jtsMultiLineString: MultiLineString): SpMultiLineString = GeoUtil.toMultiLineString(jtsMultiLineString)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiLineStringCoord(jtsCoords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiLineStringCoord(jtsCoords: Array<LineStringCoord>): MultiLineStringCoord = GeoUtil.toMultiLineStringCoord(jtsCoords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiPolygon(jtsMultiPolygon)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiPolygon(jtsMultiPolygon: MultiPolygon): SpMultiPolygon = GeoUtil.toMultiPolygon(jtsMultiPolygon)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toMultiPolygonCoord(polygons)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toMultiPolygonCoord(polygons: Array<SpPolygon>): MultiPolygonCoord = GeoUtil.toMultiPolygonCoord(polygons)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsCoordinate(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsCoordinate(coords: PointCoord): Coordinate = GeoUtil.toJtsCoordinate(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsGeometry(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsGeometry(geometry: SpGeometry): Geometry = GeoUtil.toJtsGeometry(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsPoint(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsPoint(geometry: SpPoint): Point = GeoUtil.toJtsPoint(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsPoint(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsPoint(coords: PointCoord): Point = GeoUtil.toJtsPoint(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiPoint(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiPoint(geometry: SpMultiPoint): MultiPoint = GeoUtil.toJtsMultiPoint(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiPoint(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiPoint(coords: MultiPointCoord): MultiPoint = GeoUtil.toJtsMultiPoint(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsLineString(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsLineString(geometry: SpLineString): LineString = GeoUtil.toJtsLineString(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsLineString(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsLineString(coords: LineStringCoord): LineString = GeoUtil.toJtsLineString(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsPolygon(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsPolygon(geometry: SpPolygon): Polygon = GeoUtil.toJtsPolygon(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsPolygon(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsPolygon(coords: PolygonCoord): Polygon = GeoUtil.toJtsPolygon(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiLineString(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiLineString(geometry: SpMultiLineString): MultiLineString = GeoUtil.toJtsMultiLineString(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiLineString(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiLineString(coords: MultiLineStringCoord): MultiLineString = GeoUtil.toJtsMultiLineString(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiPolygon(geometry)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiPolygon(geometry: SpMultiPolygon): MultiPolygon = GeoUtil.toJtsMultiPolygon(geometry)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsMultiPolygon(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsMultiPolygon(coords: MultiPolygonCoord): MultiPolygon = GeoUtil.toJtsMultiPolygon(coords)

    @Deprecated(
        message = "Replaced by GeoUtil",
        replaceWith = ReplaceWith("GeoUtil.toJtsLinearRing(coords)"),
        level = DeprecationLevel.ERROR
    )
    @JvmStatic
    fun toJtsLinearRing(coords: LineStringCoord): LinearRing = GeoUtil.toJtsLinearRing(coords)
}
