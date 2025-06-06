@file:Suppress("OPT_IN_USAGE")

package naksha.geo

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.asPlatformObject
import naksha.base.Platform.PlatformCompanion.forFirstJsonType
import naksha.base.Platform.PlatformCompanion.forInstance
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.Platform.PlatformCompanion.isPlatformObject
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The base [GeoJSON geometry object](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1).
 * @since 3.0
 * @see SpPoint
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
        val TYPE: PlatformType<SpGeometry> = forKClass(SpGeometry::class).withPackageName(PACKAGE_NAME)

        private val TYPE_MEMBER = NotNullProperty<SpGeometry, String>(String_TYPE) { self, _ -> forInstance(self).jsonType }
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
    var coordinates: ICoordinates
        get() {
            val value = getRaw("coordinates") ?: throw illegalState("coordinates must not be null")
            if (!isPlatformObject(value)) throw illegalState("coordinates stores invalid type: ${forInstance(value).name}")
            val coordType = forFirstJsonType(type, ICoordinates_TYPE) ?:
                throw illegalState("Coordinates type ($type) is unknown for ${forInstance(this).name}")
            return coordType.proxy(asPlatformObject(value))
        }
        set(value) {
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
        = GeoBoundingBox(coordinates).center()

    fun asPoint(): SpPoint = SpPoint.TYPE.proxy(this)
    fun asMultiPoint(): SpMultiPoint = SpMultiPoint.TYPE.proxy(this)
    fun asLineString(): SpLineString = if (this is SpLineString) this else proxy(SpLineString::class)
    fun asMultiLineString(): SpMultiLineString = if (this is SpMultiLineString) this else proxy(SpMultiLineString::class)
    fun asPolygon(): SpPolygon = if (this is SpPolygon) this else proxy(SpPolygon::class)
    fun asMultiPolygon(): SpMultiPolygon = if (this is SpMultiPolygon) this else proxy(SpMultiPolygon::class)
    fun asGeometryCollection(): SpGeometryCollection = if (this is SpGeometryCollection) this else proxy(SpGeometryCollection::class)
}