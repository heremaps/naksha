@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.math.max
import kotlin.math.min

/**
 * JVM actual of [BaseArray]. A JavaScript-compatible ordered list, backed by an [ArrayList].
 * @since 3.0
 */
actual class BaseArray actual constructor() : BaseObject() {

    companion object {
        /**
         * [VarHandle] for the private `elementData` field of [ArrayList].
         * Used to read and expand the backing array capacity without changing the logical size.
         */
        private val ELEMENT_DATA: VarHandle = MethodHandles
            .privateLookupIn(ArrayList::class.java, MethodHandles.lookup())
            .findVarHandle(ArrayList::class.java, "elementData", Array<Any>::class.java)

        @Suppress("UNCHECKED_CAST")
        internal fun getCapacity(list: ArrayList<*>): Int =
            (ELEMENT_DATA.get(list) as Array<Any?>).size

        @Suppress("UNCHECKED_CAST")
        internal fun expandCapacity(list: ArrayList<*>, newCapacity: Int) {
            val current = ELEMENT_DATA.get(list) as Array<Any?>
            if (newCapacity <= current.size) return
            ELEMENT_DATA.set(list, current.copyOf(newCapacity))
        }
    }

    actual constructor(capacity: Int) : this() {
        array = ArrayList(capacity)
    }

    private var _array: ArrayList<Any?>? = null

    /**
     * The underlying array.
     * @since 3.0
     */
    internal var array: ArrayList<Any?>
        get() {
            var array = _array
            if (array == null) {
                array = ArrayList()
                _array = array
            }
            return array
        }
        set(value) {
            _array = value
        }

    /**
     * Maps this to the given [MutableList], wrapping it.
     * @param array the [MutableList] to back this wrapper.
     * @return this
     */
    fun withArray(array: ArrayList<Any?>): BaseArray {
        this.array = array
        return this
    }

    /**
     * The number of elements in the array.
     * Setting this to a smaller value truncates; setting to a larger value pads with _null_.
     * @since 3.0
     */
    actual var length: Int
        get() = array.size
        set(newLength) {
            val arr = array
            val current = arr.size
            when {
                newLength <= 0 -> arr.clear()
                newLength < current -> arr.subList(newLength, current).clear()
                newLength > current -> repeat(newLength - current) { arr.add(null) }
            }
        }

    /**
     * The current allocated capacity. Setting this pre-allocates storage; never shrinks.
     * @since 3.0
     */
    actual var capacity: Int
        get() = getCapacity(array)
        set(value) = expandCapacity(array, value)

    /**
     * Returns the element at [position], or _null_ if the index is out of bounds.
     * @since 3.0
     */
    actual fun get(position: Int): Any? {
        val arr = _array ?: return null
        if (position < 0 || position >= arr.size) return null
        return unbox(arr[position])
    }

    /**
     * Sets the element at [index] to [value], padding with _null_ if needed.
     * @since 3.0
     */
    actual fun set(index: Int, value: Any?): Any? {
        require(index >= 0) { "index must be >= 0" }
        val arr = array
        while (index >= arr.size) arr.add(null)
        val old = unbox(arr[index])
        arr[index] = box(value)
        return old
    }

    /**
     * Appends [value] to the end of the array.
     * @since 3.0
     */
    actual fun push(value: Any?): Int {
        array.add(box(value))
        return array.size
    }

    /**
     * Removes and returns the last element, or _null_ if empty.
     * @since 3.0
     */
    actual fun pop(): Any? {
        val arr = _array ?: return null
        if (arr.isEmpty()) return null
        return unbox(arr.removeAt(arr.size - 1))
    }

    /**
     * Inserts [value] at the beginning of the array.
     * @since 3.0
     */
    actual fun unshift(value: Any?): Int {
        array.add(0, box(value))
        return array.size
    }

    /**
     * Removes and returns the first element, or _null_ if empty.
     * @since 3.0
     */
    actual fun shift(): Any? {
        val arr = _array ?: return null
        if (arr.isEmpty()) return null
        return unbox(arr.removeAt(0))
    }

    /**
     * Removes [deleteCount] elements at [start] and inserts [items].
     * @since 3.0
     */
    actual fun splice(start: Int, deleteCount: Int, vararg items: Any?): BaseArray {
        val arr = array
        val size = arr.size
        val from = if (start < 0) max(size + start, 0) else min(start, size)
        val count = min(deleteCount, size - from).coerceAtLeast(0)
        val removed = BaseArray()
        repeat(count) { removed.push(unbox(arr.removeAt(from))) }
        items.forEachIndexed { i, item -> arr.add(from + i, box(item)) }
        return removed
    }

    /**
     * Returns the first index of [element], or `-1`.
     * @since 3.0
     */
    actual fun indexOf(element: Any?, fromIndex: Int): Int {
        val arr = _array ?: return -1
        val start = fromIndex.coerceAtLeast(0)
        for (i in start until arr.size) if (arr[i] == element) return i
        return -1
    }

    /**
     * Returns the last index of [element], or `-1`.
     * @since 3.0
     */
    actual fun lastIndexOf(element: Any?, fromIndex: Int): Int {
        val arr = _array ?: return -1
        val end = if (fromIndex < 0) arr.size + fromIndex else min(fromIndex, arr.size - 1)
        for (i in end downTo 0) if (arr[i] == element) return i
        return -1
    }

    /**
     * Removes all elements.
     * @since 3.0
     */
    actual fun clear() {
        _array?.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BaseArray && _array == other._array
    }

    override fun hashCode(): Int = _array?.hashCode() ?: 0

    override fun toString(): String = _array?.toString() ?: "[]"
}
