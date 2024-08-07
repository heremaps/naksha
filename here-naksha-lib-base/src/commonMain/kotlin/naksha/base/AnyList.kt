@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * Standard definition of a list that can hold any value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyList : ListProxy<Any>(Any::class)
