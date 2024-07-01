@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.PlatformListApi.Companion.array_delete
import naksha.base.PlatformListApi.Companion.array_entries
import naksha.base.PlatformListApi.Companion.array_get
import naksha.base.PlatformListApi.Companion.array_get_length
import naksha.base.PlatformListApi.Companion.array_index_of
import naksha.base.PlatformListApi.Companion.array_last_index_of
import naksha.base.PlatformListApi.Companion.array_push
import naksha.base.PlatformListApi.Companion.array_retain_all
import naksha.base.PlatformListApi.Companion.array_set
import naksha.base.PlatformListApi.Companion.array_set_length
import naksha.base.PlatformListApi.Companion.array_splice
import kotlin.js.JsExport
import kotlin.reflect.KClass

/**
 * A multi-platform list that can store _null_ values.
 * @param <E> The not nullable element type.
 * @property elementKlass The class of the element.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
abstract class AbstractListProxy<E : Any>(val elementKlass: KClass<out E>) : Proxy(), MutableList<E?> {

    override fun createData(): PlatformList = Platform.newList()
    override fun platformObject(): PlatformList = super.platformObject() as PlatformList

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformList)
        super.bind(data, symbol)
    }

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E?): E? = box(array_get(platformObject(), index), elementKlass, alternative)

    /**
     * Returns the element at the given index. If no such key element exists or the element is not of the specified type,
     * creates a new element, assigns it and returns it.
     * @param index The key to query.
     * @return The element.
     */
    protected open fun getOrCreate(index: Int): E {
        val data = platformObject()
        val raw = array_get(data, index)
        var value = box(raw, elementKlass, null)
        if (value == null) {
            value = Platform.newInstanceOf(elementKlass)
            array_set(data, index, Platform.valueOf(value))
        }
        return value
    }

    override var size: Int
        get() = array_get_length(platformObject())
        set(newLength) = array_set_length(platformObject(), newLength)

    override fun clear() = array_set_length(platformObject(), 0)

    override fun get(index: Int): E? = box(array_get(platformObject(), index), elementKlass)

    override fun isEmpty(): Boolean = array_get_length(platformObject()) == 0

    override fun iterator(): MutableIterator<E?> {
        return toMutableList(platformObject()).listIterator()
    }

    override fun listIterator(): MutableListIterator<E?> {
        return toMutableList(platformObject()).listIterator()
    }

    override fun listIterator(index: Int): MutableListIterator<E?> {
        return toMutableList(platformObject()).listIterator(index)
    }

    override fun removeAt(index: Int): E? {
        val data = platformObject()
        if (index < 0 || index >= array_get_length(data)) return null
        return box(array_delete(data, index), elementKlass)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E?> {
        val mutableList: MutableList<E?> = toMutableList(platformObject())
        return mutableList.subList(fromIndex, toIndex)
    }

    override fun set(index: Int, element: E?): E? {
        val data = platformObject()
        return box(array_set(data, index, unbox(element)), elementKlass)
    }

    override fun retainAll(elements: Collection<E?>): Boolean {
        val unboxed: Array<Any?> = elements.map { Platform.valueOf(it) }.toTypedArray()
        return array_retain_all(platformObject(), *unboxed)
    }

    override fun removeAll(elements: Collection<E?>): Boolean {
        var dataModified = false
        for (element in elements) {
            dataModified = remove(element) || dataModified
        }
        return dataModified
    }

    override fun remove(element: E?): Boolean {
        val data = platformObject()
        val i = array_index_of(data, element, 0)
        if (i >= 0) {
            array_delete(data, i)
            return true
        }
        return false
    }

    override fun lastIndexOf(element: E?): Int = array_last_index_of(platformObject(), element)

    override fun indexOf(element: E?): Int = array_index_of(platformObject(), element)

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
        val data = platformObject()
        if (elements.isNotEmpty()) {
            for (e in elements) array_push(data, Platform.valueOf(e))
            return true
        }
        return false
    }

    override fun addAll(index: Int, elements: Collection<E?>): Boolean {
        val data = platformObject()
        if (elements.isNotEmpty()) {
            val array = arrayOfNulls<Any?>(elements.size)
            var i = 0
            for (e in elements) array[i++] = Platform.valueOf(e)
            array_splice(data, index, 0, *array)
            return true
        }
        return false
    }

    override fun add(index: Int, element: E?) {
        if(index < 0) throw IndexOutOfBoundsException(index.toString())
        array_splice(platformObject(), index, 0, Platform.valueOf(element))
    }

    override fun add(element: E?): Boolean {
        array_push(platformObject(), Platform.valueOf(element))
        return true
    }

    private fun toMutableList(platformList: PlatformList): MutableList<E?> {
        val iterator = array_entries(platformList)
        val mutableList: MutableList<E?> = mutableListOf()
        var next = iterator.next()
        while (!next.done) {
            mutableList.add(box(next.value, elementKlass))
            next = iterator.next()
        }
        return mutableList
    }
}
