@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 */
@JsExport
open class NakArray(data: PArray) : NakType<PArray>(data) {
    companion object {
        @JvmStatic
        val klass = object : NakClass<PArray, NakArray>() {
            override fun symbol(): PSymbol = Nak.NAK_SYM

            override fun canCast(o: Any?): Boolean = true

            override fun isInstance(o: Any?): Boolean = o is NakArray

            override fun create(o: PArray): NakArray = NakArray(o)
        }
    }

    override fun nakClass(): NakClass<PArray, *> = klass
}