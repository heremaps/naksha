@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakTags(vararg args: Any?) : BaseMap<Any?>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseMapKlass<Any?, NakTags>() {
            override fun isInstance(o: Any?): Boolean = o is NakTags

            override fun newInstance(vararg args: Any?): NakTags = NakTags()

        }

    }

    // TODO properties
}