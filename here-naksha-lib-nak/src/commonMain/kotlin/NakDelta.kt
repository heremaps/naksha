@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakDelta(vararg args:Any?) : BaseObject(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<NakDelta>() {
            override fun isInstance(o: Any?): Boolean = o is NakDelta

            override fun newInstance(vararg args: Any?): NakDelta = NakDelta()

        }

        @JvmStatic
        val REVIEW_STATE = Base.intern("reviewState")

        @JvmStatic
        val CHANGE_STATE = Base.intern("changeState")
    }

    override fun getKlass(): BaseKlass<*> = klass
}