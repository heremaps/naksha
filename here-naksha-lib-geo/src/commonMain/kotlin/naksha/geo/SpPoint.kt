package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * A GeoJSON [Point](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpPoint() : SpGeometry() {

    @JvmOverloads
    @JsName("of")
    constructor(longitude: Double, latitude: Double, z: Double? = null, m: Double? = null) : this() {
        this.latitude = latitude
        this.longitude = longitude
        this.z = z
        this.m = m
    }

    @JsName("ofCoords")
    constructor(coordinates: PointCoord) : this() {
        setCoordinates(coordinates)
    }

    override fun getCoordinates(): PointCoord = super.getCoordinates() as PointCoord
    fun useCoordinates(): PointCoord {
        var coordinates = getCoordinatesOrNull()
        if (coordinates !is PointCoord) {
            coordinates = PointCoord(0.0, 0.0, 0.0)
            setCoordinates(coordinates)
        }
        return coordinates
    }
    fun withCoordinates(coordinates: PointCoord): SpPoint {
        setCoordinates(coordinates)
        return this
    }

    /**
     * The [WGS84](https://en.wikipedia.org/wiki/World_Geodetic_System#WGS_84) `latitude` (mathematical `Y`) in decimal degree, so a value between `-90` and `+90` with 7 decimal digits' precision.
     * @since 3.0
     */
    var latitude: Double
        get() = useCoordinates().getLatitude()
        set(value) {
            useCoordinates().setLatitude(value)
        }

    /**
     * The [WGS84](https://en.wikipedia.org/wiki/World_Geodetic_System#WGS_84) `longitude` (mathematical `X`) in decimal degree, so a value between `-180` and `+180` with 7 decimal digits' precision.
     * @since 3.0
     */
    var longitude: Double
        get() = useCoordinates().getLongitude()
        set(value) {
            useCoordinates().setLongitude(value)
        }

    /**
     * The `Z`-ordinate, a value with 2 decimal digits' precision.
     * @since 3.0
     */
    var z: Double?
        get() = useCoordinates().getZ()
        set(value) {
            useCoordinates().setZ(value)
        }

    /**
     * The `M`-ordinate, a value with 2 decimal digits' precision.
     * @since 3.0
     */
    var m: Double?
        get() = useCoordinates().getM()
        set(value) {
            useCoordinates().setM(value)
        }
}