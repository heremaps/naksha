package naksha.base

import kotlin.js.JsExport

/**
 * A special interface to improve comparing values.
 * @since 3.0
 */
@Suppress("OPT_IN_USAGE")
@JsExport
interface Like {
    /**
     * Tests if this object is like the given value.
     * @param other the other value to compare against.
     * @return _true_ if the other value represents the same as this object; _false_ otherwise.
     * @since 3.0
     */
    fun like(other: Any?): Boolean
}