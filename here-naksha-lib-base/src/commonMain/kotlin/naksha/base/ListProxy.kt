@file:Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")

package naksha.base

import naksha.base.Platform.Platform_C.forInstance
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformListApi.PlatformListApi_C.list_delete
import naksha.base.PlatformListApi.PlatformListApi_C.list_entries
import naksha.base.PlatformListApi.PlatformListApi_C.list_get
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_capacity
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_length
import naksha.base.PlatformListApi.PlatformListApi_C.list_index_of
import naksha.base.PlatformListApi.PlatformListApi_C.list_last_index_of
import naksha.base.PlatformListApi.PlatformListApi_C.list_push
import naksha.base.PlatformListApi.PlatformListApi_C.list_retain_all
import naksha.base.PlatformListApi.PlatformListApi_C.list_set
import naksha.base.PlatformListApi.PlatformListApi_C.list_set_capacity
import naksha.base.PlatformListApi.PlatformListApi_C.list_set_length
import naksha.base.PlatformListApi.PlatformListApi_C.list_splice
import naksha.base.fn.Fn2
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.math.max
import kotlin.reflect.KClass

/**
 * A multi-platform list that can store _null_ values.
 * @param <E> The not nullable element type.
 * @property _elementType The class of the element.
 */
@JsExport
open class ListProxy<E>(private var _elementType: PlatformType<E>) : Proxy(), MutableList<E?> {
    companion object ListProxy_C {
        /**
         * The [PlatformType] of [ListProxy].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(ListProxy::class).withPackageName(PACKAGE_NAME)

        init { initialize() }
    }

    /**
     * Returns the [PlatformType] of the list elements.
     * @since 3.0
     */
    val elementType: PlatformType<E>
        get() = _elementType

    override fun createData(): PlatformList = Platform.newList()
    override fun platformObject(): PlatformList = super.platformObject() as PlatformList

    @Suppress("UNCHECKED_CAST")
    override fun platformType(): PlatformType<ListProxy<E>> = super.platformType() as PlatformType<ListProxy<E>>

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformList)
        super.bind(data, symbol)
    }

    /**
     * Returns the current capacity of the underlying platform object.
     * @return the current capacity of the platform list.
     */
    fun getCapacity() : Int = list_get_capacity(platformObject())

    /**
     * Sets the capacity to the given value, if possible.
     *
     * There is no guarantee that this method has any real effect. The capacity can never be changed below the current size, any call like this will be ignored.
     * @param capacity the wished minimum capacity.
     */
    fun setCapacity(capacity:Int) = list_set_capacity(platformObject(), capacity)

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E): E?
        = Platform.box(list_get(platformObject(), index), elementType, alternative)

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new value is created, stored
     * with the key and returned.
     * @param index the key to query.
     * @param type the [KClass] of the expected value type.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return the value.
     */
    fun <T : Any, SELF: ListProxy<E>> getOrInit(index: Int, type: PlatformType<out T>, init: Fn2<out T, in SELF, in Int>): T {
        val data = platformObject()
        val i = if (index < 0) max(0, list_get_length(data) + index) else index
        var value: T? = null
        if (i < list_get_length(data)) {
            val raw = list_get(data, i)
            value = Platform.box(raw, type)
        }
        if (value == null) {
            @Suppress("UNCHECKED_CAST")
            value = init.call(this as SELF, i)
            list_set(data, i, unbox(value))
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new
     * value is created, stored with the key and returned.
     * @param index the key to query.
     * @param type the [KClass] of the expected value.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return The value.
     */
    fun <T : Any, SELF: ListProxy<E>> getOrCreate(index: Int, type: PlatformType<out T>, init: Fn2<out T?, in SELF, in Int>? = null): T {
        val data = platformObject()
        val i = if (index < 0) max(0, list_get_length(data) + index) else index
        var value: T? = null
        if (i < list_get_length(data)) {
            val raw = list_get(data, i)
            value = box(raw, type)
        }
        if (value == null) {
            if (init != null) {
                @Suppress("UNCHECKED_CAST")
                value = init.call(this as SELF, i)
                if (value != null) {
                    list_set(data, i, unbox(value))
                    return value
                }
            }
            value = type.newInstance()
            list_set(data, i, unbox(value))
        }
        return value
    }

    /**
     * The size of the list.
     *
     * Can be modified to change the size, additional values will become `null`. Reducing the size will simply remove elements from the end of the list.
     * @since 3.0
     */
    override var size: Int
        get() = list_get_length(platformObject())
        set(newLength) = list_set_length(platformObject(), newLength)

    override fun clear() = list_set_length(platformObject(), 0)

    override fun get(index: Int): E? = box(list_get(platformObject(), index), elementType)

    /**
     * Returns the raw value stored in the platform list.
     * @param index The index to read.
     * @return the raw value stored in the platform list; `null` if the index is out of bounds.
     */
    fun getRaw(index: Int): Any? = list_get(platformObject(), index)

    override fun isEmpty(): Boolean = list_get_length(platformObject()) == 0

    override fun iterator(): MutableIterator<E?> = ListProxyMutableIterator(this, -1)

    override fun listIterator(): MutableListIterator<E?> = ListProxyMutableIterator(this, -1)

    override fun listIterator(index: Int): MutableListIterator<E?> = ListProxyMutableIterator(this, -1)

    override fun removeAt(index: Int): E? {
        val data = platformObject()
        if (index < 0 || index >= list_get_length(data)) return null
        return box(list_delete(data, index), elementType)
    }

    override fun subList(fromIndex: Int, toIndex: Int): ListProxy<E> {
        val list = platformType().allocate()
        list._elementType = elementType
        list.setCapacity(max(toIndex - fromIndex, 16))
        var i = fromIndex
        while (i < toIndex) list.add(get(i++))
        return list
    }

    override fun set(index: Int, element: E?): E? {
        val data = platformObject()
        return box(list_set(data, index, unbox(element)), elementType)
    }

    /**
     * Sets the raw value stored in the platform list.
     *
     * # Warning
     * Do only use this method if you are totally clear what you want, because it has no security or type checks!
     * @param index The index to write.
     * @param value The value to write.
     */
    protected fun setRaw(index: Int, value: Any?) { list_set(platformObject(), index, value) }

    override fun retainAll(elements: Collection<E?>): Boolean {
        val unboxed: Array<Any?> = elements.map { unbox(it) }.toTypedArray()
        return list_retain_all(platformObject(), *unboxed)
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
        val i = list_index_of(data, element, 0)
        if (i >= 0) {
            list_delete(data, i)
            return true
        }
        return false
    }

    override fun lastIndexOf(element: E?): Int = list_last_index_of(platformObject(), element)

    override fun indexOf(element: E?): Int = list_index_of(platformObject(), element)

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
            for (e in elements) list_push(data, unbox(e))
            return true
        }
        return false
    }

    fun addAll(elements: Array<out E>): Boolean {
        if (elements.isEmpty()) return false
        setCapacity(size + elements.size)
        for (element in elements) add(element)
        return true
    }

    override fun addAll(index: Int, elements: Collection<E?>): Boolean {
        val data = platformObject()
        if (elements.isNotEmpty()) {
            val array = arrayOfNulls<Any?>(elements.size)
            var i = 0
            for (e in elements) array[i++] = unbox(e)
            list_splice(data, index, 0, *array)
            return true
        }
        return false
    }

    override fun add(index: Int, element: E?) {
        if(index < 0) throw IndexOutOfBoundsException(index.toString())
        list_splice(platformObject(), index, 0, unbox(element))
    }

    override fun add(element: E?): Boolean {
        list_push(platformObject(), unbox(element))
        return true
    }

    private fun toMutableList(platformList: PlatformList): MutableList<E?> {
        val iterator = list_entries(platformList)
        val mutableList: MutableList<E?> = mutableListOf()
        var next = iterator.next()
        while (!next.done) {
            mutableList.add(box(next.value, elementType))
            next = iterator.next()
        }
        return mutableList
    }

    /**
     * A small helper to fix casting system in some cases.
     *
     * @return this cast to List<E>.
     */
    fun asList(): List<E?> = this

    /**
     * Turn this list into an array and return it _(for Java, this is an `Object[]`)_.
     * @return this list as array.
     * @see [toArrayNotNull]
     */
    fun toArray(): Array<Any?> {
        val array = arrayOfNulls<Any?>(this.size)
        for (i in array.indices) array[i] = this[i]
        return array
    }

    /**
     * Turn this list into an array, removing `null` values, and return it _(for Java, this is an `Object[]`)_.
     * @return this list as array with `null` values being eliminated.
     * @see [toArray]
     */
    fun toArrayNotNull(): Array<Any> {
        val array = arrayOfNulls<Any>(this.size)
        var a = 0
        for (i in 0 until this.size) {
            val v = this[i]
            if (v != null) array[a++] = v
        }
        // Note: At this point we know that array does not contain nulls up until (excluding) `a`!
        @Suppress("UNCHECKED_CAST")
        return (if (a == array.size) array else array.copyOf(a)) as Array<Any>
    }

    override fun hashCode(): Int = Platform.identityHashCode(this)

    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        val thisType = forInstance(this)
        val otherType = forInstance(other)
        if (thisType !== otherType) return false
        val o = other as ListProxy<E>
        if (this.size != o.size) return false
        for (i in o.size - 1 downTo 0) {
            val thisVal = this[i]
            val otherVal = o[i]
            if (thisVal != otherVal) return false
        }
        return true
    }
}
