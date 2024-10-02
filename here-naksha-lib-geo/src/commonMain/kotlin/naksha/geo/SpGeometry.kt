@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.PlatformList
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
open class SpGeometry() : AnyObject() {

    @JsName("of")
    constructor(coordinates: ICoordinates) : this() {
        setCoordinates(coordinates)
    }

    companion object GeometryProxyCompanion {
        private val TYPE = NotNullProperty<SpGeometry, String>(String::class) { self, name ->
            when (self) {
                is SpPoint -> SpType.Point
                is SpMultiPoint -> SpType.MultiPoint
                is SpLineString -> SpType.LineString
                is SpMultiLineString -> SpType.MultiLineString
                is SpPolygon -> SpType.Polygon
                is SpMultiPolygon -> SpType.MultiPolygon
                is SpGeometryCollection -> SpType.GeometryCollection
                else -> throw IllegalArgumentException("Unknown proxy type ${self::class.simpleName}")
            }.toString()
        }
    }

    /**
     * The GeoJSON type.
     */
    open var type by TYPE

    /**
     * Returns the coordinates of the geometry.
     * @return the coordinates of the geometry.
     */
    open fun getCoordinatesOrNull(): ICoordinates? {
        val type = this.type ?: return null
        val coordinates = getRaw("coordinates") ?: return null
        if (coordinates !is PlatformList) return null
        return when (type) {
            SpType.Point.toString() -> coordinates.proxy(PointCoord::class)
            SpType.MultiPoint.toString() -> coordinates.proxy(MultiPointCoord::class)
            SpType.LineString.toString() -> coordinates.proxy(LineStringCoord::class)
            SpType.MultiLineString.toString() -> coordinates.proxy(MultiLineStringCoord::class)
            SpType.Polygon.toString() -> coordinates.proxy(PolygonCoord::class)
            SpType.MultiPolygon.toString() -> coordinates.proxy(MultiPolygonCoord::class)
            else -> null
        }
    }

    /**
     * Returns the coordinates of the geometry.
     * @return the coordinates of the geometry.
     */
    open fun getCoordinates(): ICoordinates {
        val type = this.type ?: throw IllegalStateException("Missing 'type' in geometry")
        val coordinates = getRaw("coordinates") ?: throw IllegalStateException("Missing 'coordinates' in geometry")
        if (coordinates !is PlatformList) throw IllegalStateException("Invalid 'coordinates' in geometry, expect to be an array")
        return when (type) {
            SpType.Point.toString() -> coordinates.proxy(PointCoord::class)
            SpType.MultiPoint.toString() -> coordinates.proxy(MultiPointCoord::class)
            SpType.LineString.toString() -> coordinates.proxy(LineStringCoord::class)
            SpType.MultiLineString.toString() -> coordinates.proxy(MultiLineStringCoord::class)
            SpType.Polygon.toString() -> coordinates.proxy(PolygonCoord::class)
            SpType.MultiPolygon.toString() -> coordinates.proxy(MultiPolygonCoord::class)
            else -> throw IllegalStateException("The 'coordinates' in geometry are of an unknown type: $type")
        }
    }

    /**
     * Replace the coordinates with the given ones, may change the type of this geometry.
     * @param coordinates the coordinates to set.
     */
    fun setCoordinates(coordinates: ICoordinates) {
        val type = when (coordinates) {
            is PointCoord -> SpType.Point
            is MultiPointCoord -> SpType.MultiPoint
            is LineStringCoord -> SpType.LineString
            is MultiLineStringCoord -> SpType.MultiLineString
            is PolygonCoord -> SpType.Polygon
            is MultiPolygonCoord -> SpType.MultiPolygon
            else -> null
        }
        require(type != null) { "The given coordinate are of an unknown type: $type" }
        this.type = type.toString()
        set("coordinates", coordinates)
    }

    /**
     * Computes a point which is the geometric center of mass of a geometry, basically the same thing that [ST_Centroid](https://postgis.net/docs/ST_Centroid.html) does.
     * @return the centroid (center of mass) of the geometry.
     */
    fun calculateCentroid(): SpPoint // TODO: Improve this implementation!
            = SpBoundingBox(getCoordinates()).center()

    fun asPoint(): SpPoint = proxy(SpPoint::class)
    fun asMultiPoint(): SpMultiPoint = proxy(SpMultiPoint::class)
    fun asLineString(): SpLineString = proxy(SpLineString::class)
    fun asMultiLineString(): SpMultiLineString = proxy(SpMultiLineString::class)
    fun asPolygon(): SpPolygon = proxy(SpPolygon::class)
    fun asMultiPolygon(): SpMultiPolygon = proxy(SpMultiPolygon::class)
    fun asGeometryCollection(): SpGeometryCollection = proxy(SpGeometryCollection::class)
}