@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.P_List
import kotlin.js.JsExport

@JsExport
class PointProxy: P_List<Double>(Double::class) {

    fun getLongitude(): Double? = get(0)
    fun setLongitude(value: Double?): Double? = set(0, value)
    fun getLatitude(): Double? = get(1)
    fun setLatitude(value: Double?): Double? = set(1, value)
    fun getAltitude(): Double? = get(2)
    fun setAltitude(value: Double?): Double? = set(2, value)
}