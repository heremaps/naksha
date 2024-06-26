package naksha.base

import kotlin.js.JsExport

/**
 * The map where the key is [String] and the value can be anything. This is basically what objects normally
 * look like.
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class ObjectProxy : AbstractMapProxy<String, Any>(String::class, Any::class)
