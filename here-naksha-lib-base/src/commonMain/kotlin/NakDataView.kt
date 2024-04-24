@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 */
@JsExport
open class NakDataView(data: PDataView) : NakType<PDataView>(data) {
    companion object {
        @JvmStatic
        val klass = object : NakClass<PDataView, NakDataView>() {
            override fun symbol(): PSymbol = Nak.NAK_SYM

            override fun canCast(o: Any?): Boolean = true

            override fun isInstance(o: Any?): Boolean = o is NakDataView

            override fun create(o: PDataView): NakDataView = NakDataView(o)
        }
    }

    override fun nakClass(): NakClass<PDataView, *> = klass
}