@file:Suppress("OPT_IN_USAGE")

package com.here.naksha.lib.base

import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A list.
 * @param <E> The element type.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class P_List<E : Any>(val elementKlass: KClass<out E>) : Proxy(), MutableList<E> {

    /**
     * Convert the given value into an element.
     * @param value The value to convert.
     * @param alt The alternative to return when the value can't be cast to the element.
     * @return The given value as element.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun toElement(value: Any?, alt: E? = null): E? {
        if (elementKlass.isInstance(value)) return value as E
        val data = N.unbox(value)
        if (N.isNil(data)) return alt
        if (elementKlass.isInstance(value)) return value as E
        if (N.isProxyKlass(elementKlass)) return N.proxy(value, elementKlass as KClass<Proxy>) as E
        return alt
    }

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E): E = toElement(data()[index], alternative)!!

    /**
     * Returns the element at the given index. If no such key element exists or the element is not of the specified type,
     * creates a new element, assigns it and returns it.
     * @param index The key to query.
     * @return The element.
     */
    protected open fun getOrCreate(index: Int): E {
        val data = data()
        val raw = data[index]
        var value = toElement(raw, null)
        if (value == null) {
            value = N.newInstanceOf(elementKlass)
            data[index] = N.unbox(value)
        }
        return value
    }

    override fun createData(): N_Array = N.newArray()
    override fun data(): N_Array = super.data() as N_Array

    override val size: Int
        get() = TODO("Not yet implemented")

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): E {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<E> {
        TODO("Not yet implemented")
    }

    override fun listIterator(): MutableListIterator<E> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): E {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        TODO("Not yet implemented")
    }

    override fun set(index: Int, element: E): E {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(element: E): Boolean {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: E): Int {
        TODO("Not yet implemented")
    }

    override fun indexOf(element: E): Int {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(element: E): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun add(index: Int, element: E) {
        TODO("Not yet implemented")
    }

    override fun add(element: E): Boolean {
        TODO("Not yet implemented")
    }
}