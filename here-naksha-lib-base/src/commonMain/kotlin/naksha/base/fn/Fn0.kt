package naksha.base.fn

import kotlin.js.JsExport

/**
 * A functional interface to a function lambda.
 */
@JsExport
fun interface Fn0<Z> : Fn {
    fun call(): Z
}