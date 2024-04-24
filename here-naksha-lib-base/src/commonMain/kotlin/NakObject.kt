@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 */
@JsExport
open class NakObject(data: PObject) : NakType<PObject>(data) {
    companion object {
        @JvmStatic
        val klass = object : NakClass<PObject, NakObject>() {
            override fun symbol(): PSymbol = Nak.NAK_SYM

            override fun canCast(o: Any?): Boolean = true

            override fun isInstance(o: Any?): Boolean = o is NakObject

            override fun create(o: PObject): NakObject = NakObject(o)
        }
    }

    override fun nakClass(): NakClass<PObject, *> = klass
}