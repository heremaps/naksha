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

  actual override val size: Int get() = backing.size
  actual override val entries: MutableSet<MutableMap.MutableEntry<String, Attribute>> get() = backing.entries
  actual override val keys: MutableSet<String> get() = backing.keys
  actual override val values: MutableCollection<Attribute> get() = backing.values
  actual override fun isEmpty(): Boolean = backing.isEmpty()
  actual override fun containsKey(key: String): Boolean = backing.containsKey(key)
  actual override fun containsValue(value: Attribute): Boolean = backing.containsValue(value)
  actual override fun get(key: String): Attribute? = backing[key]
  actual override fun put(key: String, value: Attribute): Attribute? = backing.put(key, value)
  actual override fun putAll(from: Map<out String, Attribute>) = backing.putAll(from)
  actual override fun remove(key: String): Attribute? = backing.remove(key)
  actual override fun clear() = backing.clear()
  actual override fun putIfAbsent(key: String, value: Attribute): Attribute? = backing.putIfAbsent(key, value)

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
  actual override fun remove(key: String, value: Attribute): Boolean {
    if (backing[key] != value) return false
    backing.remove(key)
    return true
  }

  actual override fun replace(key: String, oldValue: Attribute, newValue: Attribute): Boolean {
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
