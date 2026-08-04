@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A standard definition of a map that can have any key and value.
 */
@JsExport
open class AnyMap : PTypedMap<Any, Any>(Any::class, Any::class)