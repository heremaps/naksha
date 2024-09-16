package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.math.abs
import kotlin.math.min

@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class SpBoundingBox() : ListProxy<Double>(Double::class) {

    @JsName("of2D")
    constructor(west: Double, south: Double, east: Double, north: Double) : this() {
        addAll(arrayOf(west, south, east, north))
    }

    @JsName("of3D")
    constructor(west: Double, south: Double, southWestAlt: Double, east: Double, north: Double, northEastAlt: Double) : this() {
        addAll(arrayOf(west, south, southWestAlt, east, north, northEastAlt))
    }

    @JsName("fromGeometry")
    constructor(geometry: SpGeometry?) : this() {
        when (val coord = geometry?.getCoordinates()) {
            is PointCoord -> setToPoint(coord.getLongitude(),coord.getLatitude())
            is MultiPointCoord -> addMultiPoint(coord)
            is LineStringCoord -> addLineString(coord)
            is MultiLineStringCoord -> addMultiLineString(coord)
            is MultiPolygonCoord -> addMultiPolygon(coord)
            is PolygonCoord -> addPolygon(coord)
        }
    }

    @JsName("fromCoord")
    constructor(coord: ICoordinates?) : this() {
        when (coord) {
            is PointCoord -> setToPoint(coord.getLongitude(),coord.getLatitude())
            is MultiPointCoord -> addMultiPoint(coord)
            is LineStringCoord -> addLineString(coord)
            is MultiLineStringCoord -> addMultiLineString(coord)
            is MultiPolygonCoord -> addMultiPolygon(coord)
            is PolygonCoord -> addPolygon(coord)
        }
    }

    /**
     * Returns the center of the bounding box.
     * @return the center of the bounding box.
     */
    fun center(): SpPoint // TODO: Improve this implementation, add 3D!
        = SpPoint(
            PointCoord(
                (getMaxLongitude() - getMinLongitude()) / 2.0,
                (getMaxLatitude() - getMinLatitude()) / 2.0
            )
        )

    private fun has(value: Double?): Boolean = value != null && !value.isNaN()

    fun setToPoint(longitude: Double, latitude: Double, margin: Double = 0.0): SpBoundingBox {
        withMinLatitude(latitude-margin)
        withMaxLatitude(latitude+margin)
        withMinLongitude(longitude-margin)
        withMaxLongitude(longitude+margin)
        return this
    }

    fun addPoint(pointCoord: PointCoord): SpBoundingBox {
        val longitude = pointCoord.getLongitude()
        val latitude = pointCoord.getLatitude()
        if (this.isEmpty()) setToPoint(longitude,latitude)
        else {
            // includes antimeridian case
            // TODO still 2 cases where both longitudes are both positive (or both negative)
            // TODO and the bbox still spans across the antimeridian
            // TODO then the bbox should expand the side closest to the added point
            if ((longitude < getMinLongitude()) && isSameSign(longitude,getMinLongitude())) withMinLongitude(longitude)
            else if ((longitude > getMaxLongitude()) && isSameSign(longitude,getMaxLongitude())) withMaxLongitude(longitude)
            if (latitude < getMinLatitude()) withMinLatitude(latitude)
            else if (latitude > getMaxLatitude()) withMaxLatitude(latitude)
        }
        return this
    }

    private fun isSameSign(a: Double, b: Double): Boolean {
        return (a>=0 && b>=0) || (a<=0 && b<=0)
    }

    fun addMultiPoint(multiPoint: MultiPointCoord): SpBoundingBox {
        for (point in multiPoint) {
            if (point==null) continue
            addPoint(point)
        }
        return this
    }

    fun addLineString(lineString: LineStringCoord): SpBoundingBox {
        for (point in lineString) {
            if (point==null) continue
            addPoint(point)
        }
        return this
    }

    fun addMultiLineString(multiLineString: MultiLineStringCoord): SpBoundingBox {
        for (lineString in multiLineString) {
            if (lineString==null) continue
            addLineString(lineString)
        }
        return this
    }

    fun addPolygon(polygon: PolygonCoord): SpBoundingBox {
        for (lineString in polygon) {
            if (lineString==null) continue
            addLineString(lineString)
        }
        return this
    }

    fun addMultiPolygon(multiPolygon: MultiPolygonCoord): SpBoundingBox {
        for (polygon in multiPolygon) {
            if (polygon==null) continue
            addPolygon(polygon)
        }
        return this
    }

    fun addMargin(margin: Double): SpBoundingBox {
        return this
            .withMinLongitude(getMinLongitude() - margin)
            .withMaxLongitude(getMaxLongitude() + margin)
            .withMinLatitude(getMinLatitude() - margin)
            .withMaxLatitude(getMaxLatitude() + margin)
    }

    fun minLonIndex(): Int = 0
    fun minLatIndex(): Int = 1
    fun minAltIndex(): Int? = if (size == 6) 2 else null
    fun maxLonIndex(): Int = if (size == 6) 3 else 2
    fun maxLatIndex(): Int = if (size == 6) 4 else 3
    fun maxAltIndex(): Int? = if (size == 6) 5 else null
    fun hasAltitude(): Boolean = size == 6

    /**
     * Tests if this bounding box is a 2D box.
     */
    fun is2D(): Boolean = size == 4

    /**
     * Tests if this bounding box is a 3D box.
     */
    fun is3D(): Boolean = size == 6

    /**
     * Convert this bounding box into 2D format, removing altitudes.
     */
    fun to2D(): SpBoundingBox {
        if (!is2D()) {
            val minLon = getMinLongitude()
            val minLat = getMinLatitude()
            val maxLon = getMaxLongitude()
            val maxLat = getMaxLatitude()
            clear()
            add(minLon)
            add(minLat)
            add(maxLon)
            add(maxLat)
        }
        return this
    }

    /**
     * Convert this bounding box into 3D format, if this is already 3D, does nothing. Ensures that altitudes is not _null_ or [Double.NaN].
     */
    fun to3D(): SpBoundingBox {
        if (!is3D()) {
            val minLon = getMinLongitude()
            val minLat = getMinLatitude()
            val minAlt = getMinAltitude() ?: 0.0
            val maxLon = getMaxLongitude()
            val maxLat = getMaxLatitude()
            val maxAlt = getMaxAltitude() ?: 0.0
            clear()
            add(minLon)
            add(minLat)
            add(if (minAlt.isNaN()) 0.0 else minAlt)
            add(maxLon)
            add(maxLat)
            add(if (maxAlt.isNaN()) 0.0 else maxAlt)
        }
        return this
    }

    /**
     * Convert this bounding box into a polygon.
     * @return this bounding box as polygon.
     */
    fun toPolygon(): SpPolygon = SpPolygon(this)

    fun getMinLongitude(): Double = get(minLonIndex()) ?: 0.0
    fun getWestLongitude(): Double = getMinLongitude()
    fun getMinLatitude(): Double = get(minLatIndex()) ?: 0.0
    fun getSouthLatitude(): Double = getMinLatitude()
    fun getMinAltitude(): Double? {
        val i = minAltIndex()
        return if (i != null) get(i) else null
    }
    fun getSouthWestAltitude(): Double? = getMinAltitude()

    fun getMaxLongitude(): Double = get(maxLonIndex()) ?: 0.0
    fun getEastLongitude(): Double = getMaxLongitude()
    fun getMaxLatitude(): Double = get(maxLatIndex()) ?: 0.0
    fun getNorthLatitude(): Double = getMaxLatitude()
    fun getMaxAltitude(): Double? {
        val i = maxAltIndex()
        return if (i != null) get(i) else null
    }
    fun getNorthEastAltitude(): Double? = getMinAltitude()

    fun withMinLongitude(longitude: Double): SpBoundingBox {
        set(minLonIndex(), longitude)
        return this
    }
    fun withWestLongitude(longitude: Double): SpBoundingBox = withMinLongitude(longitude)
    fun withMinLatitude(latitude: Double): SpBoundingBox {
        set(minLatIndex(), latitude)
        return this
    }
    fun withSouthLatitude(latitude: Double): SpBoundingBox = withMinLatitude(latitude)
    fun withMinAltitude(altitude: Double): SpBoundingBox {
        to3D()
        set(minAltIndex()!!, altitude)
        return this
    }
    fun withSouthWestAltitude(altitude: Double): SpBoundingBox = withMinAltitude(altitude)

    fun withMaxLongitude(longitude: Double): SpBoundingBox {
        set(maxLonIndex(), longitude)
        return this
    }
    fun withEastLongitude(longitude: Double): SpBoundingBox = withMaxLongitude(longitude)
    fun withMaxLatitude(latitude: Double): SpBoundingBox {
        set(maxLatIndex(), latitude)
        return this
    }
    fun withNorthLatitude(latitude: Double): SpBoundingBox = withMaxLatitude(latitude)
    fun withMaxAltitude(altitude: Double): SpBoundingBox {
        to3D()
        set(maxAltIndex()!!, altitude)
        return this
    }
    fun withNorthEastAltitude(altitude: Double): SpBoundingBox = withMaxAltitude(altitude)

    /**
     * Returns the longitude distance in degree.
     *
     * @param shortestDistance If true, then the shortest distance is returned, that means when
     *     crossing the date border is shorter than the other way around, this is returned. When
     *     false, then the date border is never crossed, what will result in bigger bounding boxes.
     * @return the longitude distance in degree.
     */
    fun widthInDegree(shortestDistance: Boolean): Double {
        if (shortestDistance) {
            // Note: Because the earth is a sphere there are two directions into which we can move, for
            // example:
            // min: -170° max: +170°
            // The distance here can be either 340° (heading west) or only 20° (heading east and crossing
            // the date border).
            val direct: Double = abs(getMaxLongitude() - getMinLongitude()) // +170 - -170 = +340
            val crossDateBorder = 360 - direct // 360 - 340 = 20
            // In the above example crossing the date border is the shorted distance and therefore we take
            // it as requested.
            return min(direct, crossDateBorder)
        }
        return (getMaxLongitude() + 180.0) - (getMinLongitude() + 180.0)
    }
}