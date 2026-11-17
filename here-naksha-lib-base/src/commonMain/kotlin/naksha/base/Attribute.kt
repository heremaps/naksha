@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * An attribute identified by an immutable, namespaced key. A key must consistently identify one concrete attribute type within a map.
 * @since 3.0
 */
@JsExport
open class Attribute(val key: String) {
  init {
    if (key.isEmpty()) throw illegalArg("An attribute key must not be empty")
  }
}

internal fun <T : Attribute> attributeForKey(key: String, attribute: T): T {
  if (attribute.key != key) throw illegalArg("Created attribute key '${attribute.key}' does not match '$key'")
  return attribute
}
