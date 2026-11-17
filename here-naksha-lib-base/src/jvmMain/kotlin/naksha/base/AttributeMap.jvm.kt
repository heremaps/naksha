package naksha.base

import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fn3
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Function

/**
 * The final backing field also provides construction-time safety for Attributable's plain-reference fast path. All subsequent map accesses use ConcurrentHashMap's synchronization.
 * @since 3.0
 */
actual class AttributeMap private constructor(private val backing: JvmAtomicMap<String, Attribute>) : AtomicMap<String, Attribute> by backing {
  actual constructor() : this(JvmAtomicMap())

  @Suppress("UNCHECKED_CAST")
  actual fun <T : Attribute> getOrCreate(key: String, constructor: Fn1<T, String>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    return backing.computeIfAbsent(key) { attributeForKey(it, constructor.call(it)) } as T
  }

  @Suppress("UNCHECKED_CAST")
  actual fun <T : Attribute, A> getOrCreate(key: String, a: A, constructor: Fn2<T, String, A>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    return backing.computeIfAbsent(key) { attributeForKey(it, constructor.call(it, a)) } as T
  }

  @Suppress("UNCHECKED_CAST")
  actual fun <T : Attribute, A, B> getOrCreate(key: String, a: A, b: B, constructor: Fn3<T, String, A, B>): T {
    val existing = backing[key]
    if (existing != null) return existing as T
    return backing.computeIfAbsent(key) { attributeForKey(it, constructor.call(it, a, b)) } as T
  }

  // Kotlin delegation does not forward java.util.Map's additional default methods. Retain the backing concurrent map's atomic compound operations for JVM callers too.
  override fun replace(key: String, value: Attribute): Attribute? = backing.replace(key, value)
  override fun computeIfAbsent(key: String, mappingFunction: Function<in String, out Attribute>): Attribute = backing.computeIfAbsent(key, mappingFunction)
  override fun computeIfPresent(key: String, remappingFunction: BiFunction<in String, in Attribute, out Attribute?>): Attribute? = backing.computeIfPresent(key, remappingFunction)
  override fun compute(key: String, remappingFunction: BiFunction<in String, in Attribute?, out Attribute?>): Attribute? = backing.compute(key, remappingFunction)
  override fun merge(key: String, value: Attribute, remappingFunction: BiFunction<in Attribute, in Attribute, out Attribute?>): Attribute? = backing.merge(key, value, remappingFunction)
  override fun replaceAll(function: BiFunction<in String, in Attribute, out Attribute>) = backing.replaceAll(function)
  override fun getOrDefault(key: String, defaultValue: Attribute): Attribute = backing.getOrDefault(key, defaultValue)
  override fun forEach(action: BiConsumer<in String, in Attribute>) = backing.forEach(action)

  override fun equals(other: Any?): Boolean = other === this || backing == other
  override fun hashCode(): Int = backing.hashCode()
  override fun toString(): String = backing.toString()
}
