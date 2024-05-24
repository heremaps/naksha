@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakLineString(vararg args: NakPoint): BaseList<NakPoint>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<NakPoint, NakLineString>() {
            override fun isInstance(o: Any?): Boolean = o is NakLineString

            override fun newInstance(vararg args: Any?): NakLineString = NakLineString()
        }
    }
}