@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.N.Companion.unbox
import com.here.naksha.lib.base.N.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for a flexible array.
 * @param <E> The element type.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
open class OldBaseArray<E>(vararg args: E?) : OldBaseElementType<E>() {
    init {
        @Suppress("SENSELESS_COMPARISON")
        if (args !== null && args !== undefined && args.isNotEmpty()) {
            this.data = N.newArray(*args)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : OldBaseArrayKlass<Any?, OldBaseArray<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is OldBaseArray<*>

            override fun newInstance(vararg args: Any?): OldBaseArray<Any?> = OldBaseArray()
        }
    }

    override fun klass(): OldBaseKlass<*> = klass

    override fun data(): N_Array {
        var data = this.data
        if (data == null) {
            data = N.newArray()
            this.data = data
        }
        return data as N_Array
    }

    protected open operator fun get(i: Int): E? = toElement(data()[i], componentKlass, null)
    protected open operator fun set(i: Int, value: E?): E? {
        val data = data()
        val old = toElement(data[i], componentKlass, null)
        data[i] = unbox(value)
        return old
    }
    protected open fun size(): Int = N.length(data as N_Array)
}