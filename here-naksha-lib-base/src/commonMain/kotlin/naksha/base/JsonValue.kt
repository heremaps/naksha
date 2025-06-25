@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * An interface to be implemented by a type that represents a JSON value.
 *
 * Should be recognized by [Platform.toJson] and [Platform.copy] to turn an object into JSON, or to make a copy of that object.
 * @since 3.0
 */
@JsExport
interface JsonValue {
    /**
     * The JSON representation of this object.
     *
     * This must be one of:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Double`
     * - `Int64`
     * - `String`
     * - `PlatformMap`
     * - `PlatformList`
     * - `PlatformDataView`
     * @since 3.0
     */
    val jsonValue: Any?

    /**
     * Returns a copy of this object.
     *
     * This method is invoked, when [Platform.copy] is called with the option `recursive` set to `true`.
     * @since 3.0
     */
    fun duplicate(): JsonValue
}