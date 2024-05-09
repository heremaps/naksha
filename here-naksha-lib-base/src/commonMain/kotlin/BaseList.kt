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

        /**
         * Create a new list with the given component-type.
         * @param <E> The component-type.
         * @param componentKlass The [Klass] of the component-type.
         * @return An empty list with the given component-type being set.
         */
        @JvmStatic
        fun <E> of(componentKlass: Klass<E>) : BaseList<E> {
            val array = BaseList<E>()
            array.componentKlass = componentKlass
            return array
        }
    }

    override fun klass(): BaseKlass<*> = klass
    public override operator fun get(i: Int): E? = super.get(i)
    public override operator fun set(i: Int, value: E?): E? = super.set(i, value)
    public override fun size(): Int = super.size()
    @Suppress("NON_EXPORTABLE_TYPE")
    operator fun iterator(): Iterator<RawPair<Int, E>> = KtIterator(Base.arrayIterator(data()) as PIterator<Int,E>)
}