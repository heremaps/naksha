@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
open class GeometryProxy() : ObjectProxy() {

    @JsName("of")
    constructor(coordinates: ICoordinates) : this() {
        setCoordinates(coordinates)
    }

    companion object GeometryProxyCompanion {
        private val TYPE = NullableProperty<Any, GeometryProxy, String>(String::class)
    }

    /**
     * The GeoJSON type.
     */
    open var type by TYPE

    /**
     * Returns the coordinates of the geometry.
     * @return the coordinates of the geometry.
     */
    open fun getCoordinates(): ICoordinates {
        val type = this.type ?: throw IllegalStateException("Missing 'type' in geometry")
        val coordinates = getRaw("coordinates") ?: throw IllegalStateException("Missing 'coordinates' in geometry")
        if (coordinates !is PlatformList) throw IllegalStateException("Invalid 'coordinates' in geometry, expect to be an array")
        return when (type) {
            GeoType.Point.toString() -> coordinates.proxy(PointCoord::class)
            GeoType.MultiPoint.toString() -> coordinates.proxy(MultiPointCoord::class)
            GeoType.LineString.toString() -> coordinates.proxy(LineStringCoord::class)
            GeoType.MultiLineString.toString() -> coordinates.proxy(MultiLineStringCoord::class)
            GeoType.Polygon.toString() -> coordinates.proxy(PolygonCoord::class)
            GeoType.MultiPolygon.toString() -> coordinates.proxy(MultiPolygonCoord::class)
            else -> throw IllegalStateException("The 'coordinates' in geometry are of an unknown type: $type")
        }
    }

    /**
     * Replace the coordinates with the given ones, may change the type of this geometry.
     * @param coordinates the coordinates to set.
     */
    fun setCoordinates(coordinates: ICoordinates) {
        val type = when(coordinates) {
            is PointCoord -> GeoType.Point
            is MultiPointCoord -> GeoType.MultiPoint
            is LineStringCoord -> GeoType.LineString
            is MultiLineStringCoord -> GeoType.MultiLineString
            is PolygonCoord -> GeoType.Polygon
            is MultiPolygonCoord -> GeoType.MultiPolygon
            else -> null
        }
        require(type != null) { "The given coordinate are of an unknown type: $type" }
        this.type = type.toString()
        set("coordinates", coordinates)
    }

    fun asPoint(): PointGeometry = proxy(PointGeometry::class)
    fun asMultiPoint(): MultiPointGeometry = proxy(MultiPointGeometry::class)
    fun asLineString(): LineStringGeometry = proxy(LineStringGeometry::class)
    fun asMultiLineString(): MultiLineStringGeometry = proxy(MultiLineStringGeometry::class)
    fun asPolygon(): PolygonGeometry = proxy(PolygonGeometry::class)
    fun asMultiPolygon(): MultiPolygonGeometry = proxy(MultiPolygonGeometry::class)
    fun calculateBBox(): BoundingBoxProxy = getCoordinates().calculateBBox()
}