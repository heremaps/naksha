@file:Suppress("OPT_IN_USAGE")
package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

@JsExport
class NakMultiPoint(vararg args: NakPoint): BaseList<NakPoint>(*args) {

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<NakPoint, NakMultiPoint>() {
            override fun isInstance(o: Any?): Boolean = o is NakMultiPoint

            override fun newInstance(vararg args: Any?): NakMultiPoint = NakMultiPoint()
        }
    }
}