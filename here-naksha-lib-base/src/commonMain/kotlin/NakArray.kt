@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.nak

import com.here.naksha.lib.nak.Nak.Companion.unbox
import com.here.naksha.lib.nak.Nak.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 * @param <E> The element type.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
open class NakArray<E>(vararg args: E?) : NakElementType<E>() {
    init {
        @Suppress("SENSELESS_COMPARISON")
        if (args !== null && args !== undefined && args.isNotEmpty()) {
            this.data = Nak.newArray(*args)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : NakArrayKlass<Any?, NakArray<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is NakArray<*>

            override fun newInstance(vararg args: Any?): NakArray<Any?> = NakArray()
        }
    }

    override fun getKlass(): NakKlass<*> = klass

    override fun data(): PArray {
        var data = this.data
        if (data == null) {
            data = Nak.newArray()
            this.data = data
        }
        return data as PArray
    }

    protected open operator fun get(i: Int): E? = toElement(data()[i])
    protected open operator fun set(i: Int, value: E?): E? {
        val data = data()
        val old = toElement(data[i], false)
        data[i] = unbox(value)
        return old
    }
}