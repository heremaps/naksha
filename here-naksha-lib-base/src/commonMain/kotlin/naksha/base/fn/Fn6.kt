package naksha.base.fn

import kotlin.js.JsExport

/**
 * A functional interface to a function lambda.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
fun interface Fn6<Z, A1, A2, A3, A4, A5, A6> : Fn {
    fun call(a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6): Z
}