@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Marker interface, to internally mix own tuple representations with official library ones.
 */
@JsExport
interface ITuple {
    /**
     * Convert this int a standard [Tuple].
     * @return either `this`, when this is a [Tuple], a new created standard [Tuple] object, or _null_, if no [Tuple] can be created.
     */
    fun toTuple(): Tuple?
}