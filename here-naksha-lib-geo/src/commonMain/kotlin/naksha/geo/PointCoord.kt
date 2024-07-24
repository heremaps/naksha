package naksha.geo

import naksha.base.ListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON [Position](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class PointCoord() : ListProxy<Double>(Double::class), ICoordinates {

    @JsName("of")
    constructor(longitude: Double, latitude: Double, vararg altitude: Double) : this() {
        add(longitude)
        add(latitude)
        if (altitude.isNotEmpty()) add(altitude[0])
    }

    private fun has(value: Double?): Boolean = value != null && !value.isNaN()

    fun getLongitude(): Double = get(0) ?: 0.0
    fun setLongitude(longitude: Double): Double = set(0, longitude) ?: 0.0
    fun hasLongitude(): Boolean = has(get(0))
    fun getMin(): Double = getLongitude()
    fun setMin(longitude: Double): Double = setLongitude(longitude)

    fun getLatitude(): Double = get(1) ?: 0.0
    fun setLatitude(latitude: Double): Double = set(1, latitude) ?: 0.0
    fun hasLatitude(): Boolean = has(get(1))
    fun getMax(): Double = getLatitude()
    fun setMax(latitude: Double): Double = setLatitude(latitude)

    fun getAltitude(): Double = get(2) ?: 0.0
    fun setAltitude(value: Double?): Double = set(2, value) ?: 0.0
    fun hasAltitude(): Boolean = has(get(2))
    fun getAlt(): Double = getAltitude()
    fun setAlt(value: Double?): Double = setAltitude(value)
}