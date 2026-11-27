@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

/**
 * A value with a lazily allocated, transient attribute map. No map exists until attributes are accessed. The published map reference is never reset or replaced; mutation of its entries is provided by AttributeMap.
 * @since 3.0
 */
@JsExport
expect abstract class Attributable() {
  /**
   * Returns this instance's lazily created, canonical attribute map. Serialization must not invoke this getter or include the private cache.
   * @since 3.0
   */
  val attributes: AttributeMap
}
