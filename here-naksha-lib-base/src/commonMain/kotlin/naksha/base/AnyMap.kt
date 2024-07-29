@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A standard definition of a map that can have any key and value.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@JsExport
open class AnyMap : MapProxy<Any, Any>(Any::class, Any::class)