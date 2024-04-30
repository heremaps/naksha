@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A list is just a [BaseArray], but with all getters and setters being public.
 * @param <E> The element type.
 */
@JsExport
open class BaseList<E> : BaseArray<E>() {
    companion object {
        @JvmStatic
        val klass = object : BaseArrayKlass<Any?, BaseList<Any?>>() {
            override fun isInstance(o: Any?): Boolean = o is BaseArray<*>

            override fun newInstance(vararg args: Any?): BaseList<Any?> = BaseList()
        }
    }

    override fun klass(): BaseKlass<*> = klass
    override operator fun get(i: Int): E? = super.get(i)
    override operator fun set(i: Int, value: E?): E? = super.set(i, value)
}