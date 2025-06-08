@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fx1
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * A simple cross-platform atomic set implementation, using a copy-on-write strategy.
 *
 * Can be created via:
 * ```kotlin
 * val set: AtomicSet<Int> = AtomicSet(arrayOf())
 * ```
 * @since 3.0
 */
@JsExport
open class AtomicSet<E> private constructor(array: Array<E>, private val EMPTY: Array<E>) {

    companion object AtomicSetCompanion {
        /**
         * The [PlatformType] of [AtomicSet].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(AtomicSet::class).withPackageName(PACKAGE_NAME)
    }

    private val contentRef: AtomicNonNullRef<Array<E>> = AtomicNonNullRef(array)

    /**
     * Creates a new initialized set.
     * @param initialValues The initial value, can be an empty array.
     * @since 3.0
     */
    @JsName("AtomicSetOf")
    constructor(initialValues: Array<E>): this(initialValues.copyOf(), initialValues.copyOfRange(0,0))

    /**
     * The size of the set.
     * @since 3.0
     */
    val size: Int
        get() = contentRef.get().size

    /**
     * Returns a copy of the current content.
     * @since 3.0
     */
    val content: Array<E>
        get() = contentRef.get().copyOf()

    /**
     * Clear the array and return the current content (atomically).
     *
     * @return the content before clearing.
     * @since 3.0
     */
    fun clear(): Array<E> {
        while (true) {
            val existing = contentRef.get()
            if (existing.isEmpty()) return existing.copyOf()
            val new_array = EMPTY
            if (contentRef.compareAndSet(existing, new_array)) return existing
            // Concurrent update, retry
        }
    }

    /**
     * Atomically add the given value.
     *
     * If the value is already in the set, just returns the position at which it is.
     *
     * @param value The value to add.
     * @return this.
     * @since 3.0
     */
    operator fun plus(value: E): AtomicSet<E> {
        add(value)
        return this
    }

    /**
     * Returns the value at the given index.
     *
     * - [NakshaError.NOT_FOUND], if the index is out of bounds.
     * @param index The index to read.
     * @return the value.
     * @since 3.0
     */
    operator fun get(index: Int): E {
        val existing = contentRef.get()
        if (index >= 0 && index < existing.size) return existing[index]
        throw notFound("Index out of bounds: $index")
    }

    /**
     * Sets the value at the given index to the given value.
     *
     * - [NakshaError.NOT_FOUND], if the index is out of bounds.
     * - [NakshaError.CONFLICT], if value is already in the set, but at a different index.
     * @param index The index to write.
     * @param value The value to write.
     * @return this.
     * @since 3.0
     */
    operator fun set(index: Int, value: E): AtomicSet<E> {
        put(index, value, false)
        return this
    }

    /**
     * Sets the value at the given index to the given value.
     *
     * - [NakshaError.NOT_FOUND], if the index is out of bounds.
     * - [NakshaError.CONFLICT], if value is already in the set, but at a different index.
     * @param index The index to write.
     * @param value The value to write.
     * @param relocate If _true_ and value is already within the set, it is moved to the end; otherwise [NakshaError.CONFLICT] is thrown.
     * @return the value that was replaced or the given value, if it was either relocated or already at that index.
     * @since 3.0
     */
    @JvmOverloads
    fun put(index: Int, value: E, relocate: Boolean = true): E {
        while (true) { // Optimistic locking algorithm.
            val existing = contentRef.get()
            if (index < 0 || index >= existing.size) throw notFound("Index out of bounds: $index")

            val existing_i = existing.indexOf(value)
            if (existing_i == index) return value // The value is already at that index.
            val old: E
            val new_array: Array<E>
            if (existing_i == -1) {
                // The new value is not yet in the set, replace existing old value.
                old = existing[index]
                new_array = existing.copyOf()
                new_array[index] = value
            } else {
                // The value is already in the array, but at another position, could be relocated.
                if (!relocate) throw conflict("Cannot set value to index $index, it is already at index $existing_i")
                old = value
                // Swap values
                new_array = existing.copyOf()
                new_array[existing_i] = new_array[index]
                new_array[index] = value
            }
            if (contentRef.compareAndSet(existing, new_array)) return old
            // Concurrent update, retry.
        }
    }

    /**
     * Find the given old value and replace it with the given new value.
     * @param old_value The value to be replaced.
     * @param new_value The new value to replace it with.
     * @return the index where the old value was found and replaced; `-1` if the old value is not in the set, replace failed.
     * @since 3.0
     */
    fun replace(old_value: E, new_value: E): Int {
        while (true) { // Optimistic locking algorithm.
            val existing = contentRef.get()
            val existing_i = existing.indexOf(old_value)
            if (existing_i < 0) return -1

            val new_array: Array<E> = existing.copyOf()
            new_array[existing_i] = new_value
            if (contentRef.compareAndSet(existing, new_array)) return existing_i
            // Concurrent update, retry.
        }
    }

    /**
     * Atomically add the given value.
     *
     * If the value is already in the set, just returns the position at which it is.
     *
     * @param value The value to add.
     * @return the index of the value.
     * @since 3.0
     */
    fun add(value: E): Int {
        while (true) { // Optimistic locking algorithm.
            val existing = contentRef.get()
            val existing_index = existing.indexOf(value)
            if (existing_index >= 0) return existing_index
            val new_array = existing + value
            if (contentRef.compareAndSet(existing, new_array)) return new_array.lastIndex
            // Concurrent update, retry.
        }
    }

    /**
     * Atomically add all given values.
     *
     * @param values The values to add.
     * @return the index of each value given.
     * @since 3.0
     */
    fun addAll(values: Array<E>): IntArray {
        if (values.isEmpty()) return IntArray(0)
        val indices = IntArray(values.size)
        while (true) { // Optimistic locking algorithm.
            val existing = contentRef.get()
            var addCount = 0
            for (i in values.indices) {
                val value = values[i]
                val index = existing.indexOf(value)
                indices[i] = index
                if (index == -1) addCount++
            }
            val new_array: Array<out E>
            if (addCount == values.size) { // add all
                new_array = existing + values
            } else {
                // This copy contains junk, but we anyway override all, so who cares.
                // There is anyway no other way to implement this!
                val add: Array<E> = values.copyOfRange(0, addCount)
                var add_i = 0
                for (i in values.indices) {
                    val existing_i = indices[i]
                    if (existing_i == -1) { // this value is mew, append
                        val value = values[i]
                        add[add_i] = value
                        indices[i] = existing.size + add_i
                        add_i++
                    }
                }
                check(add.size == add_i) { "AtomicSet: Missed to add all missing values, must not happen!"}
                new_array = existing + add
            }
            if (contentRef.compareAndSet(existing, new_array)) return indices
            // Concurrent update, retry.
        }
    }

    /**
     * Tests if the given value is in the set.
     * @param value The value to look for.
     * @return _true_ if the value is in the set; _false_ otherwise.
     * @since 3.0
     * @see indexOf
     */
    operator fun contains(value: E): Boolean = indexOf(value) >= 0

    /**
     * Returns the index of the given value.
     * @param value The value to search.
     * @return the index of the `value` in this set or `-1`, if the value is not in the set.
     * @since 3.0
     */
    fun indexOf(value: E): Int {
        val existing = contentRef.get()
        for (i in existing.indices) {
            val v = existing[i]
            if (v === value) return i
        }
        return -1
    }

    /**
     * Removes the given value from the set, if it is in it.
     * @param value The value to remove.
     * @return this.
     * @since 3.0
     */
    operator fun minus(value: E): AtomicSet<E> {
        remove(value)
        return this
    }

    /**
     * Removes the given value from the set, if it is in it.
     * @param value The value to remove.
     * @return _true_ if the value was removed; _false_ otherwise.
     * @since 3.0
     */
    fun remove(value: E): Boolean {
        while (true) { // Optimistic locking algorithm.
            val existing: Array<E> = contentRef.get()
            val remove_index = existing.indexOf(value)
            if (remove_index < 0) return false
            val new_size = existing.size - 1
            val new_array: Array<E>
            if (new_size == 0) { // clear
                new_array = existing.copyOfRange(0, 0)
            } else if (remove_index == new_size) { // pop
                new_array = existing.copyOfRange(0, new_size)
            } else if (remove_index == 0) { // unshift
                new_array = existing.copyOfRange(1, existing.size)
            } else {
                val start = existing.copyOfRange(0, remove_index)
                val end = existing.copyOfRange(remove_index+1, existing.size)
                new_array = start + end
            }
            if (contentRef.compareAndSet(existing, new_array)) return true
            // Concurrent update, retry.
        }
    }

    /**
     * Atomically remove all given values.
     *
     * @param values The values to remove.
     * @return an array where for each given value the index from which it was removed is stored, or `-1` if this value was not in the set.
     * @since 3.0
     */
    fun removeAll(values: Array<E>): IntArray {
        while (true) { // Optimistic locking algorithm.
            if (values.isEmpty()) return IntArray(0)
            val removedFrom = IntArray(values.size)
            while (true) { // Optimistic locking algorithm.
                val existing = contentRef.get()
                val removeIt = BooleanArray(existing.size)
                var removeCount = 0
                for (i in existing.indices) {
                    val existing_value = existing[i]
                    val index = existing.indexOf(existing_value)
                    removedFrom[i] = index
                    if (index >= 0) { // The value exists, we should remove it.
                        removeIt[i] = true
                        removeCount++
                    }
                }
                val new_size = existing.size - removeCount
                val new_array: Array<E>
                if (new_size == 0) {
                    new_array = EMPTY
                } else {
                    new_array = existing.copyOfRange(0, new_size)
                    var new_i = 0
                    for (i in existing.indices) {
                        val remove = removeIt[i]
                        if (!remove) new_array[new_i++] = existing[i]
                    }
                }
                if (contentRef.compareAndSet(existing, new_array)) return removedFrom
                // Concurrent update, retry.
            }
        }
    }

    /**
     * Removes the last element from the set and returns it.
     * @return the last element or `null`, if the set is empty.
     * @since 3.0
     */
    fun pop(): E? {
        while (true) {
            val existing = contentRef.get()
            if (existing.isEmpty()) return null
            val removed = existing[existing.lastIndex]
            val new_array = existing.copyOfRange(0, existing.lastIndex)
            if (contentRef.compareAndSet(existing, new_array)) return removed
            // Concurrent update, retry.
        }
    }

    /**
     * Push the given value to the end of the set.
     *
     * @param value The value to add to the end.
     * @param relocate If _true_ and value is already within the set, it is moved to the end; otherwise [NakshaError.CONFLICT] is thrown.
     * @return _true_ if the set was modified; _false_ otherwise.
     * @since 3.0
     */
    @JvmOverloads
    fun push(value: E, relocate: Boolean = false): Boolean {
        while (true) {
            val existing = contentRef.get()
            val new_array: Array<E>
            val index = existing.indexOf(value)
            if (index == existing.lastIndex) return false
            if (index == -1) { // new value, add to end
                new_array = existing + value
            } else { // move value to end
                if (!relocate) throw conflict("Cannot push value $value, it is already at index $index")
                new_array = existing.copyOf()
                val last = new_array[new_array.lastIndex]
                new_array[index] = last
                new_array[new_array.lastIndex] = value
            }
            if (contentRef.compareAndSet(existing, new_array)) return true
            // Concurrent update, retry.
        }
    }

    /**
     * Removes the first element from the set and returns it.
     * @return the first element or `null`, if the set is empty.
     * @since 3.0
     */
    fun shift(): E? {
        while (true) {
            val existing = contentRef.get()
            if (existing.isEmpty()) return null
            val removed = existing[0]
            val new_array = existing.copyOfRange(1, existing.size)
            if (contentRef.compareAndSet(existing, new_array)) return removed
            // Concurrent update, retry.
        }
    }

    /**
     * Push the given value to the start of the set.
     *
     * @param value The value to add to the start.
     * @param relocate If _true_ and value is already within the set, it is moved to the end; otherwise [NakshaError.CONFLICT] is thrown.
     * @return _true_ if the set was modified; _false_ otherwise.
     * @since 3.0
     */
    @JvmOverloads
    fun unshift(value: E, relocate: Boolean = false): Boolean {
        while (true) {
            val existing = contentRef.get()
            val new_array: Array<E>
            val existing_i = existing.indexOf(value)
            if (existing_i == 0) return false
            if (existing_i == -1) { // new value, add to start
                val add = existing.copyOfRange(0, 1)
                add[0] = value
                new_array = add + existing
            } else { // move value to start
                if (!relocate) throw conflict("Cannot unshift value, it is already at index $existing_i")
                new_array = existing.copyOf()
                val first = new_array[0]
                new_array[existing_i] = first
                new_array[0] = value
            }
            if (contentRef.compareAndSet(existing, new_array)) return true
            // Concurrent update, retry.
        }
    }

    /**
     * For each element being in the set, call the given method, and return a product.
     *
     * If the set is modified while this method is running, the changes are ignored. So, this method logically makes a snapshot of the content and then iterates the snapshot. The method can abort the visit by calling [AbortVisit.with].
     * @param initialValue The initial value of the product.
     * @param visitor The visitor to call for each value in the set, is invoked with two parameters, the product as returned by the previous visitor, and the current element from this set; the visitor should return the calculated product.
     * @return the calculated product.
     * @since 3.0
     * @see AbortVisit.with
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> dot(initialValue: R?, visitor: Fn2<R?, R?, E>): R? {
        val existing = contentRef.get()
        var v = initialValue
        try {
            for (i in existing.indices) {
                val element = existing[i]
                v = visitor.call(v, element)
            }
        } catch (e: NakshaException) {
            val err = e.error
            if (err is AbortVisit<*>) return err.value as R?
            throw e
        }
        return v
    }

    /**
     * For each element being in the set, call the given visitor.
     *
     * If the set is modified while this method is running, the changes are ignored. So, this method logically makes a snapshot of the content and then iterates the snapshot. The method can abort the visit by calling [AbortVisit.with], in that case returning a value is possible.
     * @param visitor The visitor to call for each value in the set.
     * @return normally `null`, except the `visitor` aborts via [AbortVisit.with], then a value can be returned.
     * @since 3.0
     * @see AbortVisit.with
     */
    @Suppress("UNCHECKED_CAST")
    fun <R> forEach(visitor: Fx1<E>): R? {
        val existing = contentRef.get()
        try {
            for (i in existing.indices) {
                val element = existing[i]
                visitor.call(element)
            }
        } catch (e: NakshaException) {
            val err = e.error
            if (err is AbortVisit<*>) return err.value as R?
            throw e
        }
        return null
    }

    /**
     * Copy this set.
     *
     * ## Note
     * This is lazy copy, so the underlying array is not copied, this only creates a new wrapper, but ones the content is modified, a new underlying array copy is done. Therefore, this is a cheap copy.
     * @return a copy of this.
     */
    fun copy(): AtomicSet<E> = AtomicSet(contentRef.get(), EMPTY)

    // TODO: Add a atomic add and remove aka splice:
    // fun splice(remove: Array<T>, add: Array<T>): Boolean // true if modified; false otherwise
}