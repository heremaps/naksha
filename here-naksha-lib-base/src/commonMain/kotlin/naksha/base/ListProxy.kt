@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.toJSON
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_delete
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_entries
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get_capacity
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_get_length
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_index_of
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_last_index_of
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_push
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_retain_all
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set_capacity
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_set_length
import naksha.base.PlatformListApi.PlatformListApiCompanion.array_splice
import naksha.base.fn.Fn2
import kotlin.js.JsExport
import kotlin.math.max
import kotlin.reflect.KClass

/**
 * A multi-platform list that can store _null_ values.
 * @param <E> The not nullable element type.
 * @property _elementKlass The class of the element.
 */
@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
open class ListProxy<E : Any>(private var _elementKlass: KClass<out E>) : Proxy(), MutableList<E?> {

    /**
     * Returns the element class of the proxy.
     */
    val elementKlass: KClass<out E>
        get() = _elementKlass

    override fun createData(): PlatformList = Platform.newList()
    override fun platformObject(): PlatformList = super.platformObject() as PlatformList

    override fun bind(data: PlatformObject, symbol: Symbol) {
        require(data is PlatformList)
        super.bind(data, symbol)
    }

    /**
     * Returns the current capacity of the underlying platform object.
     * @return the current capacity of the platform list.
     */
    fun getCapacity() : Int = array_get_capacity(platformObject())

    /**
     * Sets the capacity to the given value, if possible, there is no guarantee that this method has any real effect.
     *
     * Note, the capacity can never be changed below the current size, any call like this will be ignored.
     * @param capacity the wished minimum capacity.
     */
    fun setCapacity(capacity:Int) = array_set_capacity(platformObject(), capacity)

    /**
     * Returns the element at the given index. If no such index exists or the element is not of the specified type,
     * returns the given alternative.
     * @param index The index to query.
     * @param alternative The alternative to return, when the element is not of the specified type.
     * @return The element.
     */
    protected open fun getOr(index: Int, alternative: E): E? = box(array_get(platformObject(), index), _elementKlass, alternative)

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new value is created, stored
     * with the key and returned.
     * @param index the key to query.
     * @param klass the [KClass] of the expected value type.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return the value.
     */
    fun <T : Any, SELF: ListProxy<E>> getOrInit(index: Int, klass: KClass<out T>, init: Fn2<out T, in SELF, in Int>): T {
        val data = platformObject()
        val i = if (index < 0) max(0, array_get_length(data) + index) else index
        var value: T? = null
        if (i < array_get_length(data)) {
            val raw = array_get(data, i)
            value = box(raw, klass)
        }
        if (value == null) {
            @Suppress("UNCHECKED_CAST")
            value = init.call(this as SELF, i)
            array_set(data, i, unbox(value))
        }
        return value
    }

    /**
     * Helper to return the value of the key, if the key does not exist or is not of the expected type, a new
     * value is created, stored with the key and returned.
     * @param index the key to query.
     * @param klass the [KClass] of the expected value.
     * @param init the initialize method to invoke, when the value is not of the expected type.
     * @return The value.
     */
    fun <T : Any, SELF: ListProxy<E>> getOrCreate(index: Int, klass: KClass<out T>, init: Fn2<out T?, in SELF, in Int>? = null): T {
        val data = platformObject()
        val i = if (index < 0) max(0, array_get_length(data) + index) else index
        var value: T? = null
        if (i < array_get_length(data)) {
            val raw = array_get(data, i)
            value = box(raw, klass)
        }
        if (value == null) {
            if (init != null) {
                @Suppress("UNCHECKED_CAST")
                value = init.call(this as SELF, i)
                if (value != null) {
                    array_set(data, i, unbox(value))
                    return value
                }
            }
            value = Platform.newInstanceOf(klass)
            array_set(data, i, unbox(value))
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
        get() = array_get_length(platformObject())
        set(newLength) = array_set_length(platformObject(), newLength)

    override fun clear() = array_set_length(platformObject(), 0)

    override fun get(index: Int): E? = box(array_get(platformObject(), index), _elementKlass)

    override fun isEmpty(): Boolean = array_get_length(platformObject()) == 0

    override fun iterator(): MutableIterator<E?> {
        return ListProxyIterator(toMutableList(platformObject()).listIterator(), this)
    }

    override fun listIterator(): MutableListIterator<E?> {
        return ListProxyIterator(toMutableList(platformObject()).listIterator(), this)
    }

    override fun listIterator(index: Int): MutableListIterator<E?> {
        return ListProxyIterator(toMutableList(platformObject()).listIterator(index), this)
    }

    override fun removeAt(index: Int): E? {
        val data = platformObject()
        if (index < 0 || index >= array_get_length(data)) return null
        return box(array_delete(data, index), _elementKlass)
    }

    override fun subList(fromIndex: Int, toIndex: Int): ListProxy<E> {
        val list = Platform.allocateInstance(this::class)
        list._elementKlass = _elementKlass
        list.setCapacity(max(toIndex - fromIndex, 16))
        var i = fromIndex
        while (i < toIndex) list.add(get(i++))
        return list
    }

    override fun set(index: Int, element: E?): E? {
        val data = platformObject()
        return box(array_set(data, index, unbox(element)), _elementKlass)
    }

    override fun retainAll(elements: Collection<E?>): Boolean {
        val unboxed: Array<Any?> = elements.map { Platform.unbox(it) }.toTypedArray()
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
            for (e in elements) array_push(data, Platform.unbox(e))
            return true
        }
        return false
    }

    override fun addAll(index: Int, elements: Collection<E?>): Boolean {
        val data = platformObject()
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
        if(index < 0) throw IndexOutOfBoundsException(index.toString())
        array_splice(platformObject(), index, 0, Platform.unbox(element))
    }

    override fun add(element: E?): Boolean {
        array_push(platformObject(), Platform.unbox(element))
        return true
    }

    private fun toMutableList(platformList: PlatformList): MutableList<E?> {
        val iterator = array_entries(platformList)
        val mutableList: MutableList<E?> = mutableListOf()
        var next = iterator.next()
        while (!next.done) {
            mutableList.add(box(next.value, _elementKlass))
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


    /**
     * Convert this list into an integer-array.
     * @param convertNonBoolean if _true_, then all elements not being [Boolean] are converted into boolean; otherwise an exception is thrown.
     * @return this list as [BooleanArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `convertNonBoolean` is _false_ and an element is no boolean.
     * @since 3.0
     */
    fun toBooleanArray(convertNonBoolean: Boolean): BooleanArray {
        val array = BooleanArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Boolean) {
                array[end++] = item
            } else if (convertNonBoolean) {
                array[end++] = when (item) {
                    is String -> "true".equals(item, ignoreCase = true)
                    is Float -> item != 0.0f
                    is Double -> item != 0.0
                    is Number -> item.toLong() != 0L
                    else -> false
                }
            } else throw illegalState("The element at index $i is no boolean")
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a short-array.
     * @param ignoreIllegalValues if _true_, then invalid elements, `null` elements, or elements not being in the valid short range are ignored; otherwise an exception is thrown.
     * @return this list as [ShortArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no 16-bit integer, this includes integers being out of the valid 16-bit range _(so being too small or large)_.
     * @since 3.0
     */
    fun toShortArray(ignoreIllegalValues: Boolean): ShortArray {
        val array = ShortArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                val value = item.toLong()
                if (value in Short.MIN_VALUE.toLong() ..  Short.MAX_VALUE.toLong()) {
                    array[end++] = value.toShort()
                    continue
                }
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid short")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an integer-array.
     * @param ignoreIllegalValues if _true_, then invalid elements, `null` elements, or elements not being in the valid integer range are ignored; otherwise an exception is thrown.
     * @return this list as [IntArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no 32-bit integer, this includes integers being out of the valid 32-bit range _(so being too small or large)_.
     * @since 3.0
     */
    fun toIntArray(ignoreIllegalValues: Boolean): IntArray {
        val array = IntArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                val value = item.toLong()
                if (value in Int.MIN_VALUE.toLong() ..  Int.MAX_VALUE.toLong()) {
                    array[end++] = value.toInt()
                    continue
                }
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid integer")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an integer-array.
     * @param ignoreIllegalValues if _true_, then invalid elements, `null` elements, or elements not being in the valid integer range are ignored; otherwise an exception is thrown.
     * @return this list as [LongArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no 64-bit integer.
     * @since 3.0
     */
    fun toLongArray(ignoreIllegalValues: Boolean): LongArray {
        val array = LongArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toLong()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid long")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a float-array.
     * @param ignoreIllegalValues if _true_, then invalid elements, `null` elements are ignored; otherwise an exception is thrown.
     * @return this list as [DoubleArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no number.
     * @since 3.0
     */
    fun toFloatArray(ignoreIllegalValues: Boolean): FloatArray {
        val array = FloatArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toFloat()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid number")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a double-array.
     * @param ignoreIllegalValues if _true_, then invalid elements, `null` elements are ignored; otherwise an exception is thrown.
     * @return this list as [DoubleArray].
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an elements is no number.
     * @since 3.0
     */
    fun toDoubleArray(ignoreIllegalValues: Boolean): DoubleArray {
        val array = DoubleArray(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is Number) {
                array[end++] = item.toDouble()
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid number")
            }
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into an array of [ByteArray].
     * @param ignoreIllegalValues if _true_, then elements not being a [ByteArray] are ignored; otherwise an exception is thrown.
     * @return this list as array of strings.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no [ByteArray].
     * @since 3.0
     */
    fun toByteArrayArray(ignoreIllegalValues: Boolean): Array<ByteArray> {
        val array = arrayOfNulls<ByteArray>(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is ByteArray) {
                array[end++] = item
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid string")
            }
        }
        @Suppress("UNCHECKED_CAST")
        return (if (end == array.size) array else array.copyOf(end)) as Array<ByteArray>
    }

    /**
     * Convert this list into a string-array.
     * @param ignoreIllegalValues if _true_, then elements not being string are ignored; otherwise an exception is thrown.
     * @return this list as array of strings.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is no string.
     * @since 3.0
     */
    fun toStringArray(ignoreIllegalValues: Boolean): Array<String> {
        val array = arrayOfNulls<String>(size)
        var end = 0
        for (i in 0 until size) {
            val item = get(i)
            if (item is String) {
                array[end++] = item
                continue
            }
            if (!ignoreIllegalValues) {
                throw illegalState("The element at index $i is no valid string")
            }
        }
        @Suppress("UNCHECKED_CAST")
        return (if (end == array.size) array else array.copyOf(end)) as Array<String>
    }

    /**
     * Convert this list into a string-array.
     * @param invalidToNull if _true_, then invalid elements are turned into `null` values.
     * @param ignoreIllegalValues if `invalidToNull` is _false_ and this argument is _true_, invalid elements are ignored _(be removed from the returned list)_; otherwise an exception is thrown for invalid elements.
     * @return this list as array of strings, may contain `null` values.
     * @throws NakshaException with error [ILLEGAL_STATE][NakshaError.ILLEGAL_STATE] if `ignoreIllegalValues` is _false_ and an element is neither `null` nor a string.
     * @since 3.0
     */
    fun toStringNullableArray(invalidToNull: Boolean, ignoreIllegalValues: Boolean): Array<String?> {
        val array = arrayOfNulls<String>(size)
        var end = 0
        for (i in 0 until size) {
            var item = get(i)
            if (item != null && item !is String) {
                if (invalidToNull) item = null
                else if (ignoreIllegalValues) continue
                else throw illegalState("The element at index $i is no valid string")
            }
            array[end++] = item
        }
        return if (end == array.size) array else array.copyOf(end)
    }

    /**
     * Convert this list into a string-array that contains all elements `JSON` serialized.
     * @return this list as a string-array of `JSON` serialized elements.
     * @since 3.0
     */
    fun toJsonArray(): Array<String> = Array(size) { toJSON(it) }

    class ListProxyIterator<T: Any>(
        private val basicIterator: MutableListIterator<T?>,
        private val owner: ListProxy<T>
    ): MutableListIterator<T?> by basicIterator {

        private var currentItem: T? = null

        override fun next(): T? {
            val next = basicIterator.next()
            currentItem = next
            return next
        }

        override fun remove() {
            owner.remove(currentItem)
        }
    }
}
