@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.PlatformListApi.Companion.array_get_length
import com.here.naksha.lib.base.PlatformListApi.Companion.array_index_of
import com.here.naksha.lib.base.PlatformListApi.Companion.array_last_index_of
import com.here.naksha.lib.base.PlatformListApi.Companion.array_push
import com.here.naksha.lib.base.PlatformListApi.Companion.array_set_length
import com.here.naksha.lib.base.PlatformListApi.Companion.array_splice
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A multi-platform list that can store _null_ values.
 * @param <E> The not nullable element type.
 * @property elementKlass The class of the element.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
open class P_List<E : Any>(val elementKlass: KClass<out E>) : Proxy(), MutableList<E?> {

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E?): E? = proxy(data()[index], elementKlass, alternative)

    /**
     * Returns the element at the given index. If no such key element exists or the element is not of the specified type,
     * creates a new element, assigns it and returns it.
     * @param index The key to query.
     * @return The element.
     */
    protected open fun getOrCreate(index: Int): E {
        val data = data()
        val raw = data[index]
        var value = proxy(raw, elementKlass, null)
        if (value == null) {
            value = Platform.newInstanceOf(elementKlass)
            data[index] = Platform.unbox(value)
        }
        return value
    }

    override fun createData(): PlatformList = Platform.newArray()
    override fun data(): PlatformList = super.data() as PlatformList

    override fun clear() = array_set_length(data(), 0)

    override fun get(index: Int): E? = proxy(data()[index], elementKlass)

    override fun isEmpty(): Boolean = array_get_length(data()) == 0

    override fun iterator(): MutableIterator<E?> {
        TODO("Not yet implemented")
    }

    override fun listIterator(): MutableListIterator<E?> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<E?> {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): E? {
        val data = data()
        if (index < 0 || index >= array_get_length(data)) return Platform.undefinedOf(elementKlass)
        val removed = data[index]
        data.delete(index)
        return proxy(removed, elementKlass)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E?> {
        TODO("Not yet implemented")
    }

    override fun set(index: Int, element: E?): E? {
        val data = data()
        val old = data[index]
        data[index] = element
        return proxy(old, elementKlass)
    }

    override fun retainAll(elements: Collection<E?>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<E?>): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(element: E?): Boolean {
        val data = data()
        val i = array_index_of(data, element)
        if (i >= 0) {
            data.delete(i)
            return true
        }
        return false
    }

    override fun lastIndexOf(element: E?): Int = array_last_index_of(data(), element)

    override fun indexOf(element: E?): Int = array_index_of(data(), element)

    override fun containsAll(elements: Collection<E?>): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(element: E?): Boolean = indexOf(element) >= 0

    override fun addAll(elements: Collection<E?>): Boolean {
        val data = data()
        if (elements.isNotEmpty()) {
            for (e in elements) array_push(data, Platform.unbox(e))
            return true
        }
        return false
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val data = data()
        if (elements.isNotEmpty()) {
            val array = arrayOfNulls<Any?>(elements.size)
            var i = 0
            for (e in elements) array[i++] = Platform.unbox(e)
            array_splice(data, index, 0, *array)
            return true
        }
        return false
    }

    override fun add(index: Int, element: E) {
        TODO("Not yet implemented")
    }

    override fun add(element: E): Boolean {
        TODO("Not yet implemented")
    }
}