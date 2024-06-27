@file:Suppress("OPT_IN_USAGE")

package naksha.geo.cords

import naksha.base.AbstractListProxy
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
class PointCoordsProxy() : AbstractListProxy<Double>(Double::class) {

    @JsName("of")
    constructor(vararg coords: Double?) : this() {
        addAll(coords)
    }

    fun getLongitude(): Double? = get(0)
    fun setLongitude(value: Double?): Double? = set(0, value)
    fun getLatitude(): Double? = get(1)
    fun setLatitude(value: Double?): Double? = set(1, value)
    fun getAltitude(): Double? = get(2)
    fun setAltitude(value: Double?): Double? = set(2, value)
}