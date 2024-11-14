@file:Suppress("OPT_IN_USAGE")

package naksha.jbon

import kotlin.js.JsExport

/**
 * A dictionary entry.
 * @since 3.0.0
 */
@JsExport
data class DictEntry(
    /**
     * The dictionary to which the entry belongs.
     * @since 3.0.0
     */
    val dict: IDict,

    /**
     * The index of the entry in the [dictionary][IDict].
     * @since 3.0.0
     */
    val index: Int,

    /**
     * The entry value, must be one of:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Int64`
     * - `Double`
     * - `String`
     * - `Map<String,Any?>` - with _Any_ again being limited to these types.
     * - `List<Any?>` - with _Any_ again being limited to these types.
     * @since 3.0.0
     */
    val value: Any?
)