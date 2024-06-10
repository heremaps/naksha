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

    operator fun getValue(self: Any?, property: KProperty<*>): T = get()

    /**
     * Sets the thread-local.
     * @param value The value to set.
     */
    fun set(value: T)

    operator fun setValue(self: Any?, property: KProperty<*>, value: T) = set(value)
}