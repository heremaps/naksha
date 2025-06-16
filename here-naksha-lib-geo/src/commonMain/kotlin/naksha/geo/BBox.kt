package naksha.geo

import naksha.base.*
import naksha.base.Platform.Platform_C.forInstance
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformListApi.PlatformListApi_C.list_get
import naksha.base.PlatformListApi.PlatformListApi_C.list_set
import naksha.base.PlatformListApi.PlatformListApi_C.list_set_length
import naksha.base.PlatformUtil.PlatformUtil_C.round_double
import naksha.base.fn.Fn0
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.math.abs
import kotlin.math.min

/**
 * The [GeoJSON bounding box](https://datatracker.ietf.org/doc/html/rfc7946#section-5) implementation.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class BBox() : ListProxy<Double>(Double_TYPE) {

    @JsName("BBox2D")
    constructor(west: Double, south: Double, east: Double, north: Double) : this() {
        this.west = sp_lon(west) ?: throw illegalArg("Invalid west longitude: $west")
        this.south = sp_lat(south) ?: throw illegalArg("Invalid south latitude: $south")
        this.east = sp_lon(east) ?: throw illegalArg("Invalid east longitude: $east")
        this.north = sp_lat(north) ?: throw illegalArg("Invalid north latitude: $north")
    }

    @JsName("BBox3D")
    constructor(west: Double, south: Double, minZ: Double, east: Double, north: Double, maxZ: Double) : this() {
        this.west = sp_lon(west) ?: throw illegalArg("Invalid west longitude: $west")
        this.south = sp_lat(south) ?: throw illegalArg("Invalid south latitude: $south")
        this.minZ = sp_double(minZ)
        this.east = sp_lon(east) ?: throw illegalArg("Invalid east longitude: $east")
        this.north = sp_lat(north) ?: throw illegalArg("Invalid north latitude: $north")
        this.maxZ = sp_double(maxZ)
    }

    @JsName("BBoxOfSpGeometry")
    constructor(geometry: SpGeometry?) : this() {
        if (geometry != null) addGeometry(geometry)
    }

    @JsName("BBoxOfICoordinates")
    constructor(coordinates: ICoordinates?) : this() {
        if (coordinates != null) addCoordinates(coordinates)
    }

    companion object BBox_C {
        /**
         * The [PlatformType] of [BBox].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(BBox::class).withPackageName(PACKAGE_NAME)

        init {
            initialize()
        }
    }

    override fun createData(): PlatformList = Platform.newArray(6)

    // Ensure that whenever doubles read or written, they are rounded.
    // The setter and getter will bypass this method, using `array_get` direct, so they avoid to pay
    // the cost of rounding and type checking!
    @Suppress("UNCHECKED_CAST")
    override fun <T> box(raw: Any?, type: PlatformType<T>, alternative: T?, init: Fn0<T?>?): T?
        = if (raw != null && type === Double_TYPE) sp_double(raw) as T? else super.box(raw, type, alternative, init)

    /**
     * The most west longitude, the minimal longitude, or left component.
     *
     * Does not cross-check with [east].
     * @since 3.0
     * @see west
     * @see minLongitude
     * @see left
     */
    var west: Double
        get() = as_double_or_zero(getRaw(WEST))
        set(value) {
            val lon = sp_lon(sp_double(value)) ?: throw illegalArg("Illegal value for west longitude: $value")
            if (size < SIZE_2D) to2D()
            setRaw(WEST, lon)
        }

    fun withWest(longitude: Double): BBox {
        west = longitude
        return this
    }

    /**
     * The most west longitude, the minimal longitude, or left component.
     *
     * Does not cross-check with [maxLongitude].
     * @since 3.0
     * @see west
     * @see minLongitude
     * @see left
     */
    var minLongitude: Double
        get() = west
        set(value) { west = value }

    fun withMinLongitude(longitude: Double): BBox {
        west = longitude
        return this
    }

    /**
     * The most west longitude, the minimal longitude, or left component.
     *
     * Does not cross-check with [right].
     * @since 3.0
     * @see west
     * @see minLongitude
     * @see left
     */
    var left: Double
        get() = west
        set(value) { west = value }

    fun withLeft(longitude: Double): BBox {
        west = longitude
        return this
    }

    /**
     * The most south latitude, the minimal latitude, or bottom component.
     *
     * Does not cross-check with [north].
     * @since 3.0
     * @see south
     * @see minLatitude
     * @see bottom
     */
    var south: Double
        get() = as_double_or_zero(getRaw(SOUTH))
        set(value) {
            val lat = sp_lat(sp_double(value)) ?: throw illegalArg("Illegal value for north latitude: $value")
            if (size < SIZE_2D) to2D()
            setRaw(SOUTH, lat)
        }

    fun withSouth(latitude: Double): BBox {
        south = latitude
        return this
    }

    /**
     * The most south latitude, the minimal latitude, or bottom component.
     *
     * Does not cross-check with [maxLatitude].
     * @since 3.0
     * @see south
     * @see minLatitude
     * @see bottom
     */
    var minLatitude: Double
        get() = south
        set(value) { south = value }

    fun withMinLatitude(latitude: Double): BBox {
        south = latitude
        return this
    }

    /**
     * The most south latitude, the minimal latitude, or bottom component.
     *
     * Does not cross-check with [top].
     * @since 3.0
     * @see south
     * @see minLatitude
     * @see bottom
     */
    var bottom: Double
        get() = south
        set(value) { south = value }

    fun withBottom(latitude: Double): BBox {
        south = latitude
        return this
    }

    /**
     * If this is a 3D box, the minimal `z` value.
     *
     * Does not cross-check with [maxZ].
     * @since 3.0
     * @see maxZ
     */
    var minZ: Double?
        get() = if (size == SIZE_3D) as_double_or_zero(getRaw(MIN_Z_3D)) else null
        set(value) {
            val z = sp_double(value)
            if (z == null && value != null) throw illegalArg("Illegal value for minZ: $value")
            if (size == SIZE_2D) {
                if (z != null) to3D(minZ = z)
                return
            }
            if (size == SIZE_3D) {
                if (z == null) to2D() else setRaw(MIN_Z_3D, z)
                return
            }
            if (z != null) to3D(minZ = z)
        }

    fun withMinZ(z: Double): BBox {
        minZ = z
        return this
    }

    /**
     * The most east longitude, the maximal longitude, or right component.
     *
     * Does not cross-check with [west].
     * @since 3.0
     * @see east
     * @see maxLongitude
     * @see right
     */
    var east: Double
        get() = if (size == SIZE_2D) as_double_or_zero(getRaw(EAST_2D))
                else if (size == SIZE_3D) as_double_or_zero(getRaw(EAST_3D))
                else 0.0
        set(value) {
            val lon = sp_lon(sp_double(value)) ?: throw illegalArg("Illegal east longitude: $value")
            if (is2D()) setRaw(EAST_2D, lon)
            else if (is3D()) setRaw(EAST_3D, lon)
            else { // invalid state
                to2D()
                setRaw(EAST_2D, lon)
            }
        }

    fun withEast(longitude: Double): BBox {
        east = longitude
        return this
    }

    /**
     * The most east longitude, the maximal longitude, or right component.
     *
     * Does not cross-check with [minLongitude].
     * @since 3.0
     * @see east
     * @see maxLongitude
     * @see right
     */
    var maxLongitude: Double
        get() = east
        set(value) { east = value }

    fun withMaxLongitude(longitude: Double): BBox {
        east = longitude
        return this
    }

    /**
     * The most east longitude, the maximal longitude, or right component.
     *
     * Does not cross-check with [left].
     * @since 3.0
     * @see east
     * @see maxLongitude
     * @see right
     */
    var right: Double
        get() = east
        set(value) { east = value }

    fun withRight(longitude: Double): BBox {
        east = longitude
        return this
    }

    /**
     * The most north latitude, the maximal latitude, or top component.
     *
     * Does not cross-check with [south].
     * @since 3.0
     * @see north
     * @see maxLatitude
     * @see top
     */
    var north: Double
        get() = if (size == SIZE_2D) as_double_or_zero(getRaw(NORTH_2D))
        else if (size == SIZE_3D) as_double_or_zero(getRaw(NORTH_3D))
        else 0.0
        set(value) {
            val lat = sp_lat(sp_double(value)) ?: throw illegalArg("Illegal south latitude: $value")
            if (is2D()) setRaw(NORTH_2D, lat)
            else if (is3D()) setRaw(NORTH_3D, lat)
            else { // invalid state
                to2D()
                setRaw(NORTH_2D, lat)
            }
        }

    fun withNorth(latitude: Double): BBox {
        north = latitude
        return this
    }

    /**
     * The most north latitude, the maximal latitude, or top component.
     *
     * Does not cross-check with [minLatitude].
     * @since 3.0
     * @see north
     * @see maxLatitude
     * @see top
     */
    var maxLatitude: Double
        get() = north
        set(value) { north = value }

    fun withMaxLatitude(latitude: Double): BBox {
        north = latitude
        return this
    }

    /**
     * The most north latitude, the maximal latitude, or top component.
     *
     * Does not cross-check with [bottom].
     * @since 3.0
     * @see north
     * @see maxLatitude
     * @see top
     */
    var top: Double
        get() = north
        set(value) { north = value }

    fun withTop(latitude: Double): BBox {
        north = latitude
        return this
    }

    /**
     * If this is a 3D box, the maximal `z` value.
     *
     * Does not cross-check with [minZ].
     * @since 3.0
     * @see minZ
     */
    var maxZ: Double?
        get() = if (size == SIZE_3D) as_double_or_zero(getRaw(MAX_Z_3D)) else null
        set(value) {
            val z = sp_double(value)
            if (z == null && value != null) throw illegalArg("Illegal value for north-east-z: $value")
            if (size == SIZE_2D) {
                if (z != null) to3D(maxZ = z)
                return
            }
            if (size == SIZE_3D) {
                if (z == null) to2D() else setRaw(MAX_Z_3D, z)
                return
            }
            if (z != null) to3D(maxZ = z)
        }

    fun withMaxZ(z: Double): BBox {
        maxZ = z
        return this
    }

    /**
     * Returns the center coordinate of the bounding box.
     * @return the center coordinate.
     * @since 3.0
     */
    fun centerCoord(): PointCoord {
        if (is2D()) {
            val lon = round_double(maxLongitude - minLongitude) / 2.0
            val lat = round_double(maxLatitude - minLatitude) / 2.0
            return PointCoord(lon, lat)
        }
        if (is3D()) {
            val lon = round_double(maxLongitude - minLongitude) / 2.0
            val lat = round_double(maxLatitude - minLatitude) / 2.0
            val z = round_double(as_double_or_zero(maxZ) - as_double_or_zero(minZ)) / 2.0
            return PointCoord(lon, lat, z)
        }
        return PointCoord(0.0, 0.0)
    }

    /**
     * Returns the center point of the bounding box.
     * @return the center point of the bounding box.
     * @since 3.0
     */
    fun center(): SpPoint = SpPoint(centerCoord())

    private fun has(value: Double?): Boolean = value != null && !value.isNaN()

    /**
     * Set the bounding box to a rectangle around the given point.
     *
     * The method does not modify the `z` value.
     * @param longitude The longitude of the box center.
     * @param latitude The latitude of the box center.
     * @param margin The amount of degree to add to top/bottom, right/left.
     * @return this.
     */
    fun setLonLatMargin(longitude: Double, latitude: Double, margin: Double): BBox {
        val west = sp_lon( round_double(longitude) - margin) ?: throw illegalArg("Illegal longitude: $longitude")
        val south = sp_lat(round_double(latitude) - margin) ?: throw illegalArg("Illegal latitude: $latitude")
        val east = sp_lon( round_double(longitude) + margin) ?: throw illegalArg("Illegal longitude: $longitude")
        val north = sp_lat(round_double(latitude) + margin) ?: throw illegalArg("Illegal latitude: $latitude")
        val po = platformObject()
        if (is3D()) {
            list_set(po, WEST, west)
            list_set(po, SOUTH, south)
            list_set(po, EAST_3D, east)
            list_set(po, NORTH_3D, north)
            return this
        }
        if (!is2D()) to2D()
        list_set(po, WEST, west)
        list_set(po, SOUTH, south)
        list_set(po, EAST_2D, east)
        list_set(po, NORTH_2D, north)
        return this
    }

    /**
     * Extend the bounding box, so that it covers the given point coordinate.
     *
     * The border of the bounding box will match exactly with the given coordinate, if the bounding box was extended. If the given coordinate is 3D, the `z` value is ignored, except this is as well as 3D bounding box.
     * @param coord The coordinate to add into the bounding box.
     * @return
     */
    fun addPoint(coord: PointCoord): BBox {
        val longitude = coord.longitude
        val latitude = coord.latitude
        if (!is2D() && !is3D()) {
            setLonLatMargin(longitude, latitude, 0.0)
            return this
        }
        // includes antimeridian case
        // TODO still 2 cases where both longitudes are both positive (or both negative)
        // TODO and the bbox still spans across the antimeridian
        // TODO then the bbox should expand the side closest to the added point
        if ((longitude < minLongitude) && isSameSign(longitude, minLongitude)) {
            minLongitude = longitude
        } else if ((longitude > maxLongitude) && isSameSign(longitude, maxLongitude)) {
            maxLongitude = longitude
        } // longitude is within bounds?

        if (latitude < minLatitude) {
            minLatitude = latitude
        } else if (latitude > maxLatitude) {
            maxLatitude = latitude
        } // latitude is within bounds?

        if (is3D() && coord.is3D()) {
            val z = coord.z
            val minZ = as_double_or_zero(this.minZ)
            val maxZ = as_double_or_zero(this.maxZ)
            if (z != null) {
                // Note: Both can happen, when min/max have invalid values.
                if (z < minZ) this.minZ = z
                if (z > maxZ) this.maxZ = z
            }
        }
        return this
    }

    private fun isSameSign(a: Double, b: Double): Boolean {
        return (a>=0 && b>=0) || (a<=0 && b<=0)
    }

    fun addGeometry(geometry: SpGeometry): BBox {
        addCoordinates(geometry.coordinates)
        return this
    }

    fun addCoordinates(coords: ICoordinates): BBox {
        when (coords) {
            is PointCoord -> addPoint(coords)
            is MultiPointCoord -> addMultiPoint(coords)
            is LineStringCoord -> addLineString(coords)
            is MultiLineStringCoord -> addMultiLineString(coords)
            is MultiPolygonCoord -> addMultiPolygon(coords)
            is PolygonCoord -> addPolygon(coords)
            else -> throw illegalArg("Unknown geometry: ${forInstance(coords).name}")
        }
        return this
    }

    fun addMultiPoint(multiPoint: MultiPointCoord): BBox {
        for (point in multiPoint) {
            if (point==null) continue
            addPoint(point)
        }
        return this
    }

    fun addLineString(lineString: LineStringCoord): BBox {
        for (point in lineString) {
            if (point==null) continue
            addPoint(point)
        }
        return this
    }

    fun addMultiLineString(multiLineString: MultiLineStringCoord): BBox {
        for (lineString in multiLineString) {
            if (lineString==null) continue
            addLineString(lineString)
        }
        return this
    }

    fun addPolygon(polygon: PolygonCoord): BBox {
        for (lineString in polygon) {
            if (lineString==null) continue
            addLineString(lineString)
        }
        return this
    }

    fun addMultiPolygon(multiPolygon: MultiPolygonCoord): BBox {
        for (polygon in multiPolygon) {
            if (polygon==null) continue
            addPolygon(polygon)
        }
        return this
    }

    fun addMargin(margin: Double): BBox {
        // TODO: if margin is too big we can get overflows
        //       if margin is too less than zero, we can get overlaps (left/right swap or top/bottom swap)
        minLongitude -= margin
        maxLongitude += margin
        minLatitude -= margin
        maxLatitude += margin
        return this
    }

    fun hasZ(): Boolean = size == SIZE_3D && minZ != null && maxZ != null

    /**
     * Tests if this bounding box is a 2D box.
     */
    fun is2D(): Boolean = size == SIZE_2D

    /**
     * Tests if this bounding box is a 3D box.
     */
    fun is3D(): Boolean = size == SIZE_3D

    /**
     * Convert this bounding box into 2D format, removing `z`.
     * @return this.
     */
    fun to2D(): BBox {
        if (is2D()) return this
        val po = platformObject()
        if (is3D()) {
            // from: [west, north, min_z, east, south, max_z]
            // from: [west, north, east, south]
            list_set(po, EAST_2D, list_get(po, EAST_3D))
            list_set(po, NORTH_2D, list_get(po, NORTH_3D))
            list_set_length(po, SIZE_2D)
            return this
        }
        // We have either an empty box or something totally invalid
        setCapacity(6)
        list_set_length(po, SIZE_2D)
        list_set(po, WEST, 0.0)
        list_set(po, SOUTH, 0.0)
        list_set(po, EAST_2D, 0.0)
        list_set(po, NORTH_2D, 0.0)
        return this
    }

    /**
     * Convert this bounding box into 3D format, if this is already 3D, does nothing.
     *
     * @param minZ If this is no 3D box, the value to which to set min-`z` to.
     * @param maxZ If this is no 3D box, the value to which to set max-`z` to.
     * @return this.
     */
    @JvmOverloads
    fun to3D(minZ: Double = 0.0, maxZ: Double = 0.0): BBox {
        if (is3D()) return this
        // to: [west, north, min_z, east, south, max_z]
        val po = platformObject()
        val west = as_double_or_zero(list_get(po, WEST))
        val north = as_double_or_zero(list_get(po, SOUTH))
        val min_z = round_double(minZ)
        val east: Double
        val south: Double
        if (is2D()) {
            // from: [west, north, east, south]
            east = as_double_or_zero(list_get(po, EAST_2D))
            south = as_double_or_zero(list_get(po, NORTH_2D))
        } else {
            east = 0.0
            south = 0.0
        }
        val max_z = round_double(maxZ)

        // to: [west, north, min_z, east, south, max_z]
        setCapacity(6)
        list_set_length(po, SIZE_3D)
        list_set(po, WEST, west)
        list_set(po, SOUTH, north)
        list_set(po, MIN_Z_3D, min_z)
        list_set(po, EAST_3D, east)
        list_set(po, NORTH_3D, south)
        list_set(po, MAX_Z_3D, max_z)
        return this
    }

    /**
     * Convert this bounding box into a polygon.
     * @return this bounding box as polygon.
     */
    fun toPolygon(): SpPolygon = SpPolygon(this)

    /**
     * Returns the longitude distance in degree.
     *
     * @param shortestDistance If true, then the shortest distance is returned, that means when crossing the date border is shorter than the other way around, this is returned. When false, then the date border is never crossed, what will result in bigger bounding boxes.
     * @return the longitude distance in degree.
     */
    fun widthInDegree(shortestDistance: Boolean): Double {
        if (shortestDistance) {
            // Note: Because the earth is a sphere there are two directions into which we can move, for example:
            //   min: -170° max: +170°
            // The distance here can be either 340° (heading west) or only 20° (heading east and crossing the date border).
            val direct: Double = abs(maxLongitude - minLongitude) // +170 - -170 = +340
            val crossDateBorder = 360 - direct // 360 - 340 = 20
            // In the above example crossing the date border is the shorted distance, and therefore we take it as requested.
            return min(direct, crossDateBorder)
        }
        return (maxLongitude + 180.0) - (minLongitude + 180.0)
    }
}