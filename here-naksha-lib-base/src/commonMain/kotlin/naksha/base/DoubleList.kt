@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard declaration of a list of doubles.
 */
@JsExport
open class DoubleList : ListProxy<Double>(Double::class)

