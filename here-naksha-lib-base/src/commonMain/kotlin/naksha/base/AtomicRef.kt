@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A atomic reference.
 */
@JsExport
interface AtomicRef<R : Any> {
    /**
     * Returns the value.
     */
    fun get(): R?

    /**
     * Sets the value.
     * @param newValue the new value to set.
     */
    fun set(newValue: R?)

    /**
     * Atomically sets the value to `newValue`, and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    fun getAndSet(newValue: R?): R?

    /**
     * Atomically sets the value to `newValue` if the current value is the `expectedValue` (`===` compare).
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return _true_ if successful; _false_ return indicates that the actual value was not equal to the expected value.
     */
    fun compareAndSet(expectedValue: R?, newValue: R?): Boolean
}