package naksha.base

import kotlin.js.JsExport

/**
 * The map where the key is [String] and the value can be anything. This is basically what objects normally look like.
 * - [AnyList]
 * - [AnyMap]
 * - [AnyObject]
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class AnyObject : MapProxy<String, Any>(String::class, Any::class)
