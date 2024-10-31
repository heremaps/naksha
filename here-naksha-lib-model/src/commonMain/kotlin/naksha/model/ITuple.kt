@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Empty marker interface, to allow implementations to create internal alternatives for `ITuple`.
 */
@JsExport
interface ITuple {
    /**
     * Returns the [Tuple] for this marker.
     * @return either the real [Tuple], `this`, when this is a [Tuple] or _null_, if no [Tuple] can be created.
     */
    fun toTuple(): Tuple?
}