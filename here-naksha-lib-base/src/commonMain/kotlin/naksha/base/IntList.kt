@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of integers.
 */
@JsExport
open class IntList : ListProxy<Int>(Int::class)

