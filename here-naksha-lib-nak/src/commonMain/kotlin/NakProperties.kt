@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
open class NakProperties(vararg args: Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakProperties>() {
            override fun isInstance(o: Any?): Boolean = o is NakProperties

            override fun newInstance(vararg args: Any?): NakProperties = NakProperties()
        }

        @JvmStatic
        val XYZ_NS = Base.intern("@ns:com:here:xyz")

        @JvmStatic
        val META_NS = Base.intern("@ns:com:here:meta")

        @JvmStatic
        val DELTA_NS = Base.intern("@ns:com:here:delta")
    }

    override fun klass(): BaseKlass<*> = klass

    open fun getXyz(): NakXyz? = getOrNull(XYZ_NS, NakXyz.klass)
    open fun setXyz(value: NakXyz?) = set(XYZ_NS, value)
    open fun getDelta(): NakDelta? = getOrNull(DELTA_NS, NakDelta.klass)
    open fun setDelta(value: NakDelta?) = set(DELTA_NS, value)
}