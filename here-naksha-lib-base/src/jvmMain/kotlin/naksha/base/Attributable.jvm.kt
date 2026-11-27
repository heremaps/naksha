package naksha.base

import java.lang.invoke.MethodHandles
import kotlin.jvm.JvmName
import kotlin.jvm.Transient

actual abstract class Attributable actual constructor() {
  @Transient
  private var _attributes: AttributeMap? = null

  @get:JvmName("attributes")
  actual val attributes: AttributeMap
    get() {
      val current = _attributes
      if (current != null) return current
      return createAttributes()
    }

  private fun createAttributes(): AttributeMap {
    val published = ATTRIBUTE_MAP.getVolatile(this) as AttributeMap?
    if (published != null) return published
    val candidate = AttributeMap()
    return (ATTRIBUTE_MAP.compareAndExchange(this, null, candidate) as AttributeMap?) ?: candidate
  }

  private companion object {
    // The head is deliberately not volatile. A non-null fast-path read is safe because AttributeMap fully constructs a final backing-map reference; its operations supply subsequent synchronization. Never reset this write-once field.
    val ATTRIBUTE_MAP = MethodHandles.privateLookupIn(Attributable::class.java, MethodHandles.lookup())
      .findVarHandle(Attributable::class.java, "_attributes", AttributeMap::class.java)
  }
}
