@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class P_NakshaProperties(vararg args: Any?) : P_Object(*args) {
    companion object {

        @JvmStatic
        val XYZ = Platform.intern("@ns:com:here:xyz")
    }

    open fun getXyz(): P_Xyz? = getOrNull(XYZ, P_Xyz::class)
    open fun setXyz(value: P_Xyz?) = set(XYZ, value)
}