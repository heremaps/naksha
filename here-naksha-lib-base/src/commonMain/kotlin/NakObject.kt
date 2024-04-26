@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 */
@JsExport
open class NakObject(vararg args: Any?) : NakMap<Any?>(*args) {
    companion object {
        @JvmStatic
        val klass = object : NakObjectKlass<NakObject>() {
            override fun isInstance(o: Any?): Boolean = o is NakObject

            override fun newInstance(vararg args: Any?): NakObject = NakObject()
        }
    }

    override fun getKlass(): NakKlass<*> = klass
}