@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 */
@JsExport
open class BaseObject(vararg args: Any?) : BasePairs<Any?>(*args) {
    companion object {
        @JvmStatic
        val klass = object : BaseObjectKlass<BaseObject>() {
            override fun isInstance(o: Any?): Boolean = o is BaseObject

            override fun newInstance(vararg args: Any?): BaseObject = BaseObject()
        }
    }

    override fun getKlass(): BaseKlass<*> = klass
}