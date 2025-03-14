@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A concurrent (atomic) boolean.
 */
@JsExport
interface AtomicBool {
    /**
     * Returns the current value.
     */
    fun get(): Boolean

    /**
     * Sets the current value.
     * @param value the value to set.
     */
    fun set(value: Boolean)

    /**
     * Set the value, if the current value is the expected.
     * @param expect the value that is expected.
     * @param update the new value to set.
     * @return _true_ if the value was set.
     */
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean
}