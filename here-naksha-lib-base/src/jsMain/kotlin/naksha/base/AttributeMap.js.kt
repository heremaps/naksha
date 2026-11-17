@file:Suppress("UNCHECKED_CAST", "OPT_IN_USAGE")

package naksha.base

import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fn3
import kotlin.js.JsName
import kotlin.js.JsExport

@JsExport
actual class AttributeMap private constructor(private val backing: JsAtomicMap<String, Attribute>) : AtomicMap<String, Attribute> by backing {
  @JsName("newAttributeMap")
  actual constructor() : this(JsAtomicMap())

  actual fun <T : Attribute> getOrCreate(key: String, constructor: Fn1<T, String>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    val created = attributeForKey(key, constructor.call(key))
    backing[key] = created
    return created
  }

  @JsName("getOrCreateWith")
  actual fun <T : Attribute, A> getOrCreate(key: String, a: A, constructor: Fn2<T, String, A>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    val created = attributeForKey(key, constructor.call(key, a))
    backing[key] = created
    return created
  }

  @JsName("getOrCreateWith2")
  actual fun <T : Attribute, A, B> getOrCreate(key: String, a: A, b: B, constructor: Fn3<T, String, A, B>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    val created = attributeForKey(key, constructor.call(key, a, b))
    backing[key] = created
    return created
  }

  // JsAtomicMap currently uses reference equality here. AttributeMap deliberately follows the JVM/value-equality contract instead.
  override fun remove(key: String, value: Attribute): Boolean {
    if (backing[key] != value) return false
    backing.remove(key)
    return true
  }

  override fun replace(key: String, oldValue: Attribute, newValue: Attribute): Boolean {
    if (backing[key] != oldValue) return false
    backing[key] = newValue
    return true
  }

  override fun compareAndSet(key: String, oldValue: Attribute?, newValue: Attribute?): Boolean {
    if (newValue == null) return if (oldValue == null) !containsKey(key) else remove(key, oldValue)
    if (oldValue == null) return putIfAbsent(key, newValue) == null
    return replace(key, oldValue, newValue)
  }

  override fun equals(other: Any?): Boolean = other === this || backing == other
  override fun hashCode(): Int = backing.hashCode()
  override fun toString(): String = backing.toString()
}
