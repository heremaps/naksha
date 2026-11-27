@file:Suppress("OPT_IN_USAGE")

package naksha.base

import kotlin.js.JsExport

@JsExport
actual abstract class Attributable actual constructor() {
  private var _attributes: AttributeMap? = null

  actual val attributes: AttributeMap
    get() {
      val current = _attributes
      if (current != null) return current
      val created = AttributeMap()
      _attributes = created
      hideAttributes(created)
      return created
    }

  @Suppress("UNUSED_VARIABLE")
  private fun hideAttributes(created: AttributeMap) {
    // Private fields are renamed in production JS. Identify the newly stored reference instead of depending on a compiler-specific field spelling.
    val owner = this
    val keys = js("Object.keys(owner)").unsafeCast<Array<String>>()
    for (key in keys) {
      if (js("owner[key] === created").unsafeCast<Boolean>()) {
        js("Object.defineProperty(owner, key, {enumerable: false})")
        break
      }
    }
  }
}
