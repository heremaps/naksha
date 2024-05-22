@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakPoint(vararg args: Double?) : BaseArray<Double>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<Double, NakPoint>() {
            override fun isInstance(o: Any?): Boolean = o is NakPoint

            override fun newInstance(vararg args: Any?): NakPoint = NakPoint()
        }

    }

    fun getLongitude(): Double? = get(0)
    fun setLongitude(value: Double?): Double? = set(0, value)
    fun getLatitude(): Double? = get(1)
    fun setLatitude(value: Double?): Double? = set(1, value)
    fun getAltitude(): Double? = get(2)
    fun setAltitude(value: Double?): Double? = set(2, value)
}