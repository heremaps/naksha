@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class P_NakshaDelta(vararg args:Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<P_NakshaDelta>() {
            override fun isInstance(o: Any?): Boolean = o is P_NakshaDelta

            override fun newInstance(vararg args: Any?): P_NakshaDelta = P_NakshaDelta()

        }

        @JvmStatic
        val REVIEW_STATE = Base.intern("reviewState")

        @JvmStatic
        val CHANGE_STATE = Base.intern("changeState")
    }

    override fun klass(): BaseKlass<*> = klass

    // TODO implement review state
}