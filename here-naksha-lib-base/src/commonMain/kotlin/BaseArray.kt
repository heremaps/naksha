@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.Base.Companion.unbox
import com.here.naksha.lib.base.Base.Companion.undefined
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * The Naksha type for an object.
 * @param <E> The element type.
 */
@Suppress("MemberVisibilityCanBePrivate")
@JsExport
open class BaseArray<E>(vararg args: E?) : BaseElementType<E>() {
    init {
        @Suppress("SENSELESS_COMPARISON")
        if (args !== null && args !== undefined && args.isNotEmpty()) {
            this.data = Base.newArray(*args)
        }
    }

    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<Any?, BaseArray<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is BaseArray<*>

            override fun newInstance(vararg args: Any?): BaseArray<Any?> = BaseArray()
        }
    }

    override fun getKlass(): BaseKlass<*> = klass

    override fun data(): PArray {
        var data = this.data
        if (data == null) {
            data = Base.newArray()
            this.data = data
        }
        return data as PArray
    }

    protected open operator fun get(i: Int): E? = toElement(data()[i], componentKlass, null)
    protected open operator fun set(i: Int, value: E?): E? {
        val data = data()
        val old = toElement(data[i], componentKlass, null)
        data[i] = unbox(value)
        return old
    }
}