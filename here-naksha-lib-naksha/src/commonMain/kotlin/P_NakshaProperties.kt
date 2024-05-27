@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class P_NakshaProperties(vararg args: Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_NakshaProperties>() {
            override fun isInstance(o: Any?): Boolean = o is P_NakshaProperties

            override fun newInstance(vararg args: Any?): P_NakshaProperties = P_NakshaProperties()
        }

        @JvmStatic
        val XYZ = Base.intern("@ns:com:here:xyz")
    }

    override fun klass(): BaseKlass<*> = klass

    open fun getXyz(): P_Xyz? = getOrNull(XYZ, P_Xyz.klass)
    open fun setXyz(value: P_Xyz?) = set(XYZ, value)
}