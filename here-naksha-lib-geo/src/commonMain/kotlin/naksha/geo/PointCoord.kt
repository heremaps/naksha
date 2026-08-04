package naksha.geo

import naksha.base.PTypedArray
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A GeoJSON [Position](https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.1).
 */
@Suppress("OPT_IN_USAGE", "MemberVisibilityCanBePrivate", "unused")
@JsExport
class PointCoord() : PTypedArray<Double>(Double::class), ICoordinates {

    @Suppress("SENSELESS_COMPARISON")
    @JsName("of")
    constructor(longitude: Double, latitude: Double, vararg additional: Double) : this() {
        add(longitude)
        add(latitude)
        if (additional != null && additional.isNotEmpty()) {
            add(additional[0])
            if (additional.size >= 2) add(additional[1])
        }
    }

    private fun has(value: Double?): Boolean = value != null && !value.isNaN()

    fun getLongitude(): Double = get(0) ?: 0.0
    fun setLongitude(longitude: Double): Double = set(0, longitude) ?: 0.0
    fun hasLongitude(): Boolean = has(get(0))

    fun getLatitude(): Double = get(1) ?: 0.0
    fun setLatitude(latitude: Double): Double = set(1, latitude) ?: 0.0
    fun hasLatitude(): Boolean = has(get(1))

    fun getZ(): Double? = get(2)
    fun setZ(value: Double?): Double? = set(2, value)
    override fun hasZ(): Boolean = has(get(2))
    fun removeZ(): Double? = removeAt(2)

    fun getM(): Double = get(3) ?: 0.0
    fun setM(value: Double?): Double? = set(3, value)
    override fun hasM(): Boolean = has(get(3))
    fun removeM(): Double? = removeAt(3)

    fun getAltitude(): Double? = getZ()
    fun setAltitude(value: Double?): Double? = setZ(value)
    fun hasAltitude(): Boolean = hasZ()
    fun getAlt(): Double? = getZ()
    fun setAlt(value: Double?): Double? = setZ(value)
}