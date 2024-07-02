package naksha.geo

import naksha.base.AbstractListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class BoundingBoxProxy() : AbstractListProxy<Double>(Double::class) {

    @JsName("of2D")
    constructor(west: Double, south: Double, east: Double, north: Double) : this() {
        addAll(arrayOf(west, south, east, north))
    }

    @JsName("of3D")
    constructor(west: Double, south: Double, southWestAlt: Double, east: Double, north: Double, northEastAlt: Double) : this() {
        addAll(arrayOf(west, south, southWestAlt, east, north, northEastAlt))
    }

    private fun has(value: Double?): Boolean = value != null && !value.isNaN()

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
    fun to2D(): BoundingBoxProxy {
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
    fun to3D(): BoundingBoxProxy {
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

    fun withMinLongitude(longitude: Double): BoundingBoxProxy {
        set(minLonIndex(), longitude)
        return this
    }
    fun withWestLongitude(longitude: Double): BoundingBoxProxy = withMinLongitude(longitude)
    fun withMinLatitude(latitude: Double): BoundingBoxProxy {
        set(minLatIndex(), latitude)
        return this
    }
    fun withSouthLatitude(latitude: Double): BoundingBoxProxy = withMinLatitude(latitude)
    fun withMinAltitude(altitude: Double): BoundingBoxProxy {
        to3D()
        set(minAltIndex()!!, altitude)
        return this
    }
    fun withSouthWestAltitude(altitude: Double): BoundingBoxProxy = withMinAltitude(altitude)

    fun withMaxLongitude(longitude: Double): BoundingBoxProxy {
        set(maxLonIndex(), longitude)
        return this
    }
    fun withEastLongitude(longitude: Double): BoundingBoxProxy = withMaxLongitude(longitude)
    fun withMaxLatitude(latitude: Double): BoundingBoxProxy {
        set(maxLatIndex(), latitude)
        return this
    }
    fun withNorthLatitude(latitude: Double): BoundingBoxProxy = withMaxLatitude(latitude)
    fun withMaxAltitude(altitude: Double): BoundingBoxProxy {
        to3D()
        set(maxAltIndex()!!, altitude)
        return this
    }
    fun withNorthEastAltitude(altitude: Double): BoundingBoxProxy = withMaxAltitude(altitude)

}