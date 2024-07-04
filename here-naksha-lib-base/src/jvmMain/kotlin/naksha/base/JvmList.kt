package naksha.base

import naksha.base.Platform.Companion.unsafe
import java.util.*
import kotlin.math.max
import kotlin.math.round

/**
 * The JVM implementation of a [PlatformList].
 */
open class JvmList() : JvmObject(), MutableList<Any?>, PlatformList {
    companion object {
        // We need to hack a bit, because of the stupidity today, that "private" properties are not accessible,
        // even for the price of total inefficiency, when not done! We rather waste huge amount of CPU cycles,
        // then give developers access to class internals, was a foolish world we live in!
        @Suppress("DEPRECATION")
        internal val arrayList_elementDataOFFSET = unsafe.objectFieldOffset(ArrayList::class.java.getDeclaredField("elementData"))

        @Suppress("DEPRECATION")
        internal val arrayList_sizeOFFSET = unsafe.objectFieldOffset(ArrayList::class.java.getDeclaredField("size"))
        internal val emptyArrayListElement = emptyArray<Any?>()

        @Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
        private inline fun elementDataOf(list: ArrayList<Any?>): Array<Any?> =
            unsafe.getObject(list, arrayList_elementDataOFFSET) as Array<Any?>

        private const val MAX_OPT_CAPACITY = Int.MAX_VALUE - 16

        /**
         * Returns the optimal capacity to allocate for the given size.
         * @param size the size (number of elements).
         * @return the optimal recommended capacity to allocate (guaranteed to be greater/equal size).
         */
        fun optimalCapacity(size: Int): Int = if (size >= MAX_OPT_CAPACITY) Int.MAX_VALUE else size + max(16, round(size * 0.2).toInt())
    }

    /**
     * The payload of the array.
     */
    internal var list: ArrayList<Any?>? = null

    /**
     * Returns the element data of the underlying list.
     * @return the element data of the underlying list; _null_ if no list is used.
     */
    protected fun elementData(): Array<Any?>? {
        val list = this.list
        return if (list != null) elementDataOf(list) else null
    }

    /**
     * Search for the given object in this list, starting at [start], up until (excluding) [end].
     * @param o the object to search for.
     * @param start the index to start search at, defaults to `0`.
     * @param end the index to not search, default to [size].
     * @return the index of the next occurrence of [o] or `-1`.
     */
    fun indexOfRange(o: Any?, start: Int = 0, end: Int = list?.size ?: 0): Int {
        val l = list ?: return -1
        if (o == null) {
            for (i in start until end) {
                if (l[i] == null) {
                    return i
                }
            }
        } else {
            for (i in start until end) {
                if (o == l[i]) {
                    return i
                }
            }
        }
        return -1
    }

    /**
     * Search for the given object in this list, starting at [end] - 1, down until (including) [start].
     * @param o the object to search for.
     * @param start the index to start search at, defaults to [size].
     * @param end the index to end the search at, default to `0`.
     * @return the index of the next occurrence of [o] or `-1`.
     */
    fun lastIndexOfRange(o: Any?, start: Int = list?.size ?: 0, end: Int = 0): Int {
        require(end <= start)
        val l = list ?: return -1
        if (o == null) {
            for (i in end - 1 downTo start) {
                if (l[i] == null) {
                    return i
                }
            }
        } else {
            for (i in end - 1 downTo start) {
                if (o == l[i]) {
                    return i
                }
            }
        }
        return -1
    }

    constructor(vararg entries: Any?) : this() {
        val list: ArrayList<Any?>?
        if (entries.isNotEmpty()) {
            list = ArrayList(entries.size + 4)
            list.addAll(entries)
        } else {
            list = null
        }
        this.list = list
    }

    open fun list(): ArrayList<Any?> {
        var list = this.list
        if (list == null) {
            list = ArrayList()
            this.list = list
        }
        return list
    }

    override var size: Int
        get() = list?.size ?: 0
        set(newLength) {
            if (newLength <= 0) {
                this.list = null
                return
            }

            var list = this.list
            if (list == null) {
                list = ArrayList(optimalCapacity(newLength))
                unsafe.putInt(list, arrayList_sizeOFFSET, newLength)
                this.list = list
            } else {
                val length = list.size
                if (newLength > length) { // inflate list
                    list.ensureCapacity(optimalCapacity(newLength))
                    Arrays.fill(elementDataOf(list), length, newLength, null)
                    unsafe.putInt(list, arrayList_sizeOFFSET, newLength)
                } else if (newLength < length) { // deflate list, we know that newLength >= 1!
                    unsafe.putInt(list, arrayList_sizeOFFSET, newLength)
                    Arrays.fill(elementDataOf(list), newLength, length, null)
                }
            }
        }

    /**
     * Ensures that the list as at least the given size. If the list is too short, inflate it with _null_ value.
     * @param size the size required.
     * @return _true_ if the size was modified; _false_ otherwise.
     */
    fun ensureSize(size: Int): Boolean {
        if (this.size <= size) {
            this.size = size
            return true
        }
        return false
    }

    override fun clear() {
        list?.clear()
    }

    override fun addAll(elements: Collection<Any?>): Boolean = list().addAll(elements)

    override fun addAll(index: Int, elements: Collection<Any?>): Boolean = list().addAll(index, elements)

    override fun add(index: Int, element: Any?) {
        list().add(index, element)
    }

    override fun add(element: Any?): Boolean = list().add(element)

    override fun containsAll(elements: Collection<Any?>): Boolean = list?.containsAll(elements) ?: false

    override operator fun contains(element: Any?): Boolean = element != null && list?.contains(element) == true

    override operator fun get(index: Int): Any? {
        val d = list
        if (d == null || index < 0 || index >= d.size) return null
        return d[index]
    }

    override fun isEmpty(): Boolean = list?.isEmpty() ?: true

    override fun iterator(): MutableIterator<Any?> = list().iterator()

    override fun listIterator(): MutableListIterator<Any?> = list().listIterator()

    override fun listIterator(index: Int): MutableListIterator<Any?> = list().listIterator(index)

    override fun removeAt(index: Int): Any? {
        val d = list
        if (d == null || index < 0 || index >= d.size) return null
        return d.removeAt(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Any?> = list().subList(fromIndex, toIndex)

    @Suppress("ConvertArgumentToSet")
    override fun retainAll(elements: Collection<Any?>): Boolean = list?.retainAll(elements) ?: false

    @Suppress("ConvertArgumentToSet")
    override fun removeAll(elements: Collection<Any?>): Boolean = list?.removeAll(elements) ?: false

    override fun remove(element: Any?): Boolean {
        if (element is Int) return list?.remove(element) ?: false
        val list = this.list
        if (list != null) {
            for (i in 0..<list.size) {
                val e = list[i]
                if (e == element) {
                    list.removeAt(i)
                    return true
                }
            }
        }
        return false
    }

    override fun lastIndexOf(element: Any?): Int = list?.lastIndexOf(element) ?: -1

    override fun indexOf(element: Any?): Int = list?.indexOf(element) ?: -1

    override operator fun set(index: Int, element: Any?): Any? {
        require(index >= 0) { "Illegal index $index, must be positive number" }
        val d = list()
        val old: Any? = if (index < d.size) d[index] else null
        while (index >= d.size) d.add(null)
        d[index] = element
        return old
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is JvmList && list != null && list == other.list
    }
}