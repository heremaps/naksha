@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * An identifier-specific attribute with immutable ownership. Generic attribute maps permit arbitrary attributes; identifier-specific factories verify the owner before returning cached values.
 * @since 3.0
 */
@JsExport
abstract class IdAttribute(val id: Id, key: String) : Attribute(key)
