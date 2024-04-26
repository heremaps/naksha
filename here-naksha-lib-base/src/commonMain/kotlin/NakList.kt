@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list is just a [NakArray], but with all getters and setters being public.
 * @param <E> The element type.
 */
@JsExport
open class NakList<E> : NakArray<E>() {
    companion object {
        @JvmStatic
        val klass = object : NakArrayKlass<Any?, NakList<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is NakArray<*>

            override fun newInstance(vararg args: Any?): NakList<Any?> = NakList()
        }
    }

    override fun getKlass(): NakKlass<*> = klass
    override operator fun get(i: Int): E? = super.get(i)
    override operator fun set(i: Int, value: E?): E? = super.set(i, value)
}