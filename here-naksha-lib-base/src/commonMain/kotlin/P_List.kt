@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import com.here.naksha.lib.base.PlatformListApi.Companion.array_delete
import com.here.naksha.lib.base.PlatformListApi.Companion.array_entries
import com.here.naksha.lib.base.PlatformListApi.Companion.array_get
import com.here.naksha.lib.base.PlatformListApi.Companion.array_get_length
import com.here.naksha.lib.base.PlatformListApi.Companion.array_index_of
import com.here.naksha.lib.base.PlatformListApi.Companion.array_last_index_of
import com.here.naksha.lib.base.PlatformListApi.Companion.array_push
import com.here.naksha.lib.base.PlatformListApi.Companion.array_set
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
     * Create a proxy or return the existing proxy. If a proxy of a not compatible type exists already and [doNotOverride]
     * is _true_, the method will throw an _IllegalStateException_; otherwise the current type is simply overridden.
     * @param <T> The type to proxy, must extend [Proxy].
     * @param klass The proxy class.
     * @param elementKlass The element class, can be _null_, if the proxy type has a fixed element.
     * @param doNotOverride If _true_ and the symbol is already
     * @return The proxy instance.
     */
    fun <V : Any, T : P_List<V>> proxy(
        klass: KClass<out T>,
        elementKlass: KClass<out V>? = null,
        doNotOverride: Boolean = false
    ): T = data().proxy(klass, elementKlass, doNotOverride)

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E?): E? = box(array_get(data(), index), elementKlass, alternative)

    /**
     * Returns the element at the given index. If no such key element exists or the element is not of the specified type,
     * creates a new element, assigns it and returns it.
     * @param index The key to query.
     * @return The element.
     */
    protected open fun getOrCreate(index: Int): E {
        val data = data()
        val raw = array_get(data, index)
        var value = box(raw, elementKlass, null)
        if (value == null) {
            value = Platform.newInstanceOf(elementKlass)
            PlatformListApi.array_set(data, index, Platform.unbox(value))
        }
        return value
    }

    override fun createData(): PlatformList = Platform.newList()
    override fun data(): PlatformList = super.data() as PlatformList
    override val size: Int
        get() = array_get_length(data())

    override fun clear() = array_set_length(data(), 0)

    override fun get(index: Int): E? = box(array_get(data(), index), elementKlass)

    override fun isEmpty(): Boolean = array_get_length(data()) == 0

    override fun iterator(): MutableIterator<E?> {
        return toMutableList(data()).listIterator()
    }

    override fun listIterator(): MutableListIterator<E?> {
        return toMutableList(data()).listIterator()
    }

    override fun listIterator(index: Int): MutableListIterator<E?> {
        return toMutableList(data()).listIterator(index)
    }

    override fun removeAt(index: Int): E? {
        val data = data()
        if (index < 0 || index >= array_get_length(data)) return Platform.undefinedOf(elementKlass)
        return box(array_delete(data, index), elementKlass)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E?> {
        val platformList = array_splice(data(), fromIndex, toIndex)
        return toMutableList(platformList)
    }

    override fun set(index: Int, element: E?): E? {
        val data = data()
        return box(array_set(data, index, unbox(element)), elementKlass)
    }

    override fun retainAll(elements: Collection<E?>): Boolean {
        var dataModified = false
        val iterator = array_entries(data())
        do {
            val next = iterator.next()
            if (!elements.contains(box(next.value, elementKlass))) {
                dataModified = dataModified || remove(next.value)
            }
        } while (!next.done)
        return dataModified
    }

    override fun removeAll(elements: Collection<E?>): Boolean {
        var dataModified = false
        for (element in elements) {
            dataModified = dataModified || remove(element)
        }
        return dataModified
    }

    override fun remove(element: E?): Boolean {
        val data = data()
        val i = array_index_of(data, element)
        if (i >= 0) {
            array_delete(data(), i)
            return true
        }
        return false
    }

    override fun lastIndexOf(element: E?): Int = array_last_index_of(data(), element)

    override fun indexOf(element: E?): Int = array_index_of(data(), element)

    override fun containsAll(elements: Collection<E?>): Boolean {
        for (element in elements) {
            if (!contains(element)) {
                return false
            }
        }
        return true
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

    override fun addAll(index: Int, elements: Collection<E?>): Boolean {
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

    override fun add(index: Int, element: E?) {
        array_set(data(), index, Platform.unbox(element))
    }

    override fun add(element: E?): Boolean {
        array_push(Platform.unbox(element))
        return true
    }

    private fun toMutableList(platformList: PlatformList): MutableList<E?> {
        val iterator = array_entries(platformList)
        val mutableList: MutableList<E?> = mutableListOf()
        do {
            val next = iterator.next()
            mutableList.add(box(next.value, elementKlass))
        } while (!next.done)
        return mutableList
    }
}