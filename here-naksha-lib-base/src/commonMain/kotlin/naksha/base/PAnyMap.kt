package naksha.base

import kotlin.js.JsExport

/**
 * A proxy for an arbitrary object as it generally appears in raw `JSON`.
 *
 * So, a map where the key is a [String] and the value can be anything.
 * @see PAnyArray
 */
@Suppress("unused", "OPT_IN_USAGE")
@JsExport
open class PAnyMap : PTypedMap<String, Any>(String::class, Any::class)

