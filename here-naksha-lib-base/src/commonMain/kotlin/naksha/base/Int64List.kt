@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of 64-bit integers.
 */
@JsExport
open class Int64List : ListProxy<Int64>(Int64::class)

