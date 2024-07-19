@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A concurrent (atomic) integer.
 */
@JsExport
interface AtomicInt {
    /**
     * Returns the current value.
     */
    fun get(): Int

    /**
     * Sets the current value.
     * @param value the value to set.
     */
    fun set(value: Int)

    /**
     * Set the value, if the current value is the expected.
     * @param expect the value that is expected.
     * @param update the new value to set.
     * @return _true_ if the value was set.
     */
    fun compareAndSet(expect: Int, update: Int): Boolean

    /**
     * Add the given value atomically, return the value before adding.
     * @param value the value to add atomically to the current one.
     * @return the value before adding.
     */
    fun getAndAdd(value: Int): Int

    /**
     * Add the given value atomically, return the new value.
     * @param value the value to add atomically to the current one.
     * @return the new value after adding.
     */
    fun addAndGet(value: Int): Int
}