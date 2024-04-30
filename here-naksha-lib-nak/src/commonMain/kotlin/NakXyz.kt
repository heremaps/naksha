@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The XYZ namespace stored in the `@ns:com:here:xyz` property of the [NakFeature].
 */
@JsExport
class NakXyz(vararg args: Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakXyz>() {
            override fun isInstance(o: Any?): Boolean = o is NakXyz

            override fun newInstance(vararg args: Any?): NakXyz = NakXyz()

        }

        @JvmStatic
        val ACTION = Base.intern("action")

        @JvmStatic
        val CREATE = Base.intern("CREATE")

        @JvmStatic
        val UPDATE = Base.intern("UPDATE")

        @JvmStatic
        val DELETE = Base.intern("DELETE")
    }

    override fun getKlass(): BaseKlass<*> = klass

    fun getAction(): String = getOr(ACTION, Klass.stringKlass, CREATE)
    fun setAction(action: String?) = set(ACTION, action)

}