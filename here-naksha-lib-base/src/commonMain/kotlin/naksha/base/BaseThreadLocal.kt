package naksha.base

import kotlin.js.JsExport
import kotlin.reflect.KProperty

/**
 * A simple API to access the native logging.
 */
@Suppress("OPT_IN_USAGE", "NON_EXPORTABLE_TYPE")
@JsExport
interface BaseThreadLocal<T> {
    /**
     * Returns the thread-local value.
     */
    fun get(): T

    /**
     * Returns the thread-local value.
     * @param self The reference to the object called (delegation source).
     * @param property The property being accessed (delegation source).
     */
    operator fun getValue(self: Any?, property: KProperty<*>): T = get()

    /**
     * Sets the thread-local.
     * @param value The value to set.
     */
    fun set(value: T)

    /**
     * Sets the thread-local.
     * @param self The reference to the object called (delegation source).
     * @param property The property being accessed (delegation source).
     * @param value The value to set.
     */
    operator fun setValue(self: Any?, property: KProperty<*>, value: T) = set(value)
}