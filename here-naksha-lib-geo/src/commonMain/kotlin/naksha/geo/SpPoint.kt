package naksha.geo

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON [Point](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class SpPoint() : SpGeometry() {

    @JsName("of")
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

    var latitude: Double
        get() = useCoordinates().getLatitude()
        set(value) {
            useCoordinates().setLatitude(value)
        }

    var longitude: Double
        get() = useCoordinates().getLongitude()
        set(value) {
            useCoordinates().setLatitude(value)
        }

    var altitude: Double?
        get() = useCoordinates().getAltitude()
        set(value) {
            useCoordinates().setAltitude(value)
        }

}