@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.asPlatformObject
import naksha.base.Platform.PlatformCompanion.forFirstJsonType
import naksha.base.Platform.PlatformCompanion.forInstance
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.Platform.PlatformCompanion.isPlatformObject
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * The base [GeoJSON geometry object](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1).
 *
 * ### Coordinates
 * [ICoordinates], [PointCoord], [MultiPointCoord], [LineStringCoord], [LinearRingCoord], [MultiLineStringCoord], [PolygonCoord], [MultiPolygonCoord].
 *
 * ### Geometries
 * [SpGeometry], [SpPoint], [SpMultiPoint], [SpLineString], [SpMultiLineString], [SpPolygon], [SpMultiPolygon]
 *
 * @since 3.0
 */
@JsExport
open class SpGeometry() : AnyObject() {

    @JsName("SpGeometryOf")
    constructor(coordinates: ICoordinates) : this() {
        val coordType = forInstance(coordinates)
        val jsonType = coordType.jsonType ?: throw illegalArg("The given coordinates have no valid GeoJSON type")
        set("type", jsonType)
        set("coordinates", coordinates)
    }

    companion object SpGeometryCompanion {
        /**
         * The [PlatformType] of [SpGeometry].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SpGeometry::class).withPackageName(PACKAGE_NAME)

        /**
         * Returns the correct concrete type for the given geometry.
         *
         * - Throws [NakshaError.ILLEGAL_ARGUMENT], if `any` is no valid geometry.
         * @param value The geometry value.
         * @return the concrete correct geometry.
         */
        @JvmStatic
        @JsStatic
        fun forValue(value: Any?): SpGeometry {
            val unboxed = Platform.unbox(value) ?: throw illegalArg("THe given geometry is null")
            if (!isPlatformObject(unboxed)) throw illegalArg("The given geometry is no platform map")
            val po = asPlatformObject(unboxed)
            if (po !is PlatformMap) throw illegalArg("The given geometry is no platform map")
            val raw_type = map_get(po, "type")
            if (raw_type !is String) throw illegalArg("The given geometry has an invalid type: '$raw_type'")
            val geo_type = forFirstJsonType(raw_type, TYPE) ?: throw illegalArg("The given geometry has an unknown type: '$raw_type'")
            return geo_type.proxy(po)
        }

        private val TYPE_MEMBER = NotNullProperty<SpGeometry, String>(String_TYPE) { self, _ -> forInstance(self).jsonType }

        init {
            initialize()
        }
    }

    /**
     * The GeoJSON type.
     */
    open var type by TYPE_MEMBER

    /**
     * The coordinates of the GeoJSON object.
     * @since 3.0
     * @see PointCoord
     * @see MultiPointCoord
     * @see LineStringCoord
     * @see LinearRingCoord
     * @see MultiLineStringCoord
     * @see PolygonCoord
     * @see MultiPolygonCoord
     */
    open val coordinates: ICoordinates
        get() = get_coordinates()

    protected fun get_coordinates(): ICoordinates {
        val value = getRaw("coordinates") ?: throw illegalState("coordinates must not be null")
        if (value !is PlatformList) throw illegalState("coordinates stores invalid type: ${forInstance(value).name}")
        val coordType = forFirstJsonType(type, ICoordinates_TYPE) ?:
        throw illegalState("Coordinates type ($type) is unknown for ${forInstance(this).name}")
        return coordType.proxy(value)
    }

    open fun set_coordinates(value: ICoordinates) {
        @Suppress("SENSELESS_COMPARISON")
        if (value == null) throw illegalArg("coordinates must not be null")
        val coordType = forFirstJsonType(type, ICoordinates_TYPE) ?:
            throw illegalState("Coordinates type ($type) is unknown for ${forInstance(this).name}")
        if (!coordType.isInstance(value))
            throw illegalArg("coordinates type must be ${coordType.simpleName}, but ${forInstance( value).name} given")
        set("coordinates", value)
    }

    /**
     * Computes a point which is the geometric center of mass of a geometry, basically the same thing that [ST_Centroid](https://postgis.net/docs/ST_Centroid.html) does.
     * @return the centroid (center of mass) of the geometry.
     */
    fun calculateCentroid(): SpPoint // TODO: Improve this implementation!
        = BBox(coordinates).center()

    fun isPoint(): Boolean = SpPoint.TYPE.jsonType == type
    fun asPoint(): SpPoint = SpPoint.TYPE.proxy(this)

    fun isMultiPoint(): Boolean = SpMultiPoint.TYPE.jsonType == type
    fun asMultiPoint(): SpMultiPoint = SpMultiPoint.TYPE.proxy(this)

    fun isLineString(): Boolean = SpLineString.TYPE.jsonType == type
    fun asLineString(): SpLineString = SpLineString.TYPE.proxy(this)

    fun isMultiLineString(): Boolean = SpMultiLineString.TYPE.jsonType == type
    fun asMultiLineString(): SpMultiLineString = SpMultiLineString.TYPE.proxy(this)

    fun isPolygon(): Boolean = SpPolygon.TYPE.jsonType == type
    fun asPolygon(): SpPolygon = SpPolygon.TYPE.proxy(this)

    fun isMultiPolygon(): Boolean = SpMultiPolygon.TYPE.jsonType == type
    fun asMultiPolygon(): SpMultiPolygon = SpMultiPolygon.TYPE.proxy(this)

    fun isGeometryCollection(): Boolean = SpGeometryCollection.TYPE.jsonType == type
    fun asGeometryCollection(): SpGeometryCollection = SpGeometryCollection.TYPE.proxy(this)
}