package naksha.base

import kotlin.js.JsExport

/**
 * A simple API to access the native logging.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface BaseThreadLocal<T> {
    /**
     * Returns the thread-local value.
     */
    fun get(): T

    /**
     * Sets the thread-local.
     * @param value The value to set.
     */
    fun set(value: T)
}