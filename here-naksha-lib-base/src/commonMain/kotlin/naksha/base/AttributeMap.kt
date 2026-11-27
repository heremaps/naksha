@file:Suppress("OPT_IN_USAGE")

package naksha.base

import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fn3
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A concurrent attribute map backed by the platform's atomic map implementation. Keys must match their attribute's key and consistently identify one concrete value type. Direct map mutations must preserve that contract. Conditional mutations use value equality on both platforms.
 *
 * Creation is atomic per key. A constructor must be short and must not recursively modify this map. Exceptions and mismatched keys leave an absent entry unchanged. Pass-through arguments affect creation only; an installed value is returned regardless of those arguments. Removing an entry permits later creation again. Generic result types are erased; inconsistent types for a key can cause ClassCastException.
 * @since 3.0
 */
@JsExport
expect class AttributeMap : AtomicMap<String, Attribute> {
  @JsName("newAttributeMap")
  constructor()

  override val size: Int
  override val entries: MutableSet<MutableMap.MutableEntry<String, Attribute>>
  override val keys: MutableSet<String>
  override val values: MutableCollection<Attribute>
  override fun isEmpty(): Boolean
  override fun containsKey(key: String): Boolean
  override fun containsValue(value: Attribute): Boolean
  override fun get(key: String): Attribute?
  override fun put(key: String, value: Attribute): Attribute?
  override fun putAll(from: Map<out String, Attribute>)
  override fun remove(key: String): Attribute?
  override fun clear()
  override fun putIfAbsent(key: String, value: Attribute): Attribute?
  override fun remove(key: String, value: Attribute): Boolean
  override fun replace(key: String, oldValue: Attribute, newValue: Attribute): Boolean

  /**
   * Returns the installed value, or constructs and atomically installs one using the requested key.
   * @since 3.0
   */
  fun <T : Attribute> getOrCreate(key: String, constructor: Fn1<T, String>): T

  /**
   * Passes one additional value to an absent entry's constructor. A may itself be nullable.
   * @since 3.0
   */
  @JsName("getOrCreateWith")
  fun <T : Attribute, A> getOrCreate(key: String, a: A, constructor: Fn2<T, String, A>): T

  /**
   * Passes two additional values to an absent entry's constructor. Either argument type may be nullable.
   * @since 3.0
   */
  @JsName("getOrCreateWith2")
  fun <T : Attribute, A, B> getOrCreate(key: String, a: A, b: B, constructor: Fn3<T, String, A, B>): T
}
