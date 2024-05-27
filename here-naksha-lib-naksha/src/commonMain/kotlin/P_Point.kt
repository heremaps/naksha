@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class P_Point(vararg args: Double?) : BaseArray<Double>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<Double, P_Point>() {
            override fun isInstance(o: Any?): Boolean = o is P_Point

            override fun newInstance(vararg args: Any?): P_Point = P_Point()
        }

    }

    fun getLongitude(): Double? = get(0)
    fun setLongitude(value: Double?): Double? = set(0, value)
    fun getLatitude(): Double? = get(1)
    fun setLatitude(value: Double?): Double? = set(1, value)
    fun getAltitude(): Double? = get(2)
    fun setAltitude(value: Double?): Double? = set(2, value)
}