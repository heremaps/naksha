package naksha.base

import naksha.base.fn.Fn1
import naksha.base.fn.Fn2
import naksha.base.fn.Fn3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AttributeMapTest {
  private class Value(key: String, val payload: String?) : Attribute(key) {
    override fun equals(other: Any?): Boolean = other is Value && key == other.key && payload == other.payload
    override fun hashCode(): Int = 31 * key.hashCode() + (payload?.hashCode() ?: 0)
  }

  @Test
  fun constructorsRunOnlyForAbsentEntriesAndReceiveTheRequestedKey() {
    val map = AttributeMap()
    var calls = 0
    val constructor = Fn1<Value, String> { key -> calls++; Value(key, "created") }
    val first = map.getOrCreate("sample", constructor)
    assertEquals("sample", first.key)
    assertSame(first, map.getOrCreate("sample", constructor))
    assertSame(first, map["sample"])
    assertEquals(1, calls)
    map.remove("sample")
    assertEquals("created", map.getOrCreate("sample", constructor).payload)
    assertEquals(2, calls)
  }

  @Test
  fun passThroughArgumentsCanBeNullableAndAreIgnoredForInstalledValues() {
    val map = AttributeMap()
    var calls = 0
    val one = Fn2<Value, String, String?> { key, value -> calls++; Value(key, value) }
    val first = map.getOrCreate("one", null, one)
    assertNull(first.payload)
    assertSame(first, map.getOrCreate("one", "ignored", one))
    val two = Fn3<Value, String, String?, Int> { key, value, count -> calls++; Value(key, "$value:$count") }
    val second = map.getOrCreate("two", null, 42, two)
    assertEquals("null:42", second.payload)
    assertSame(second, map.getOrCreate("two", "ignored", 99, two))
    assertEquals(2, calls)
  }

  @Test
  fun exceptionsAndMismatchedKeysDoNotInstallValues() {
    val map = AttributeMap()
    assertFailsWith<NakshaException> {
      map.getOrCreate("broken", Fn1<Attribute, String> { throw illegalArg("failed constructor") })
    }
    assertFailsWith<NakshaException> { map.getOrCreate("broken", Fn1 { Attribute("other") }) }
    assertFailsWith<NakshaException> { map.getOrCreate("broken", 1, Fn2<Attribute, String, Int> { _, _ -> Attribute("other") }) }
    assertFailsWith<NakshaException> { map.getOrCreate("broken", 1, 2, Fn3<Attribute, String, Int, Int> { _, _, _ -> Attribute("other") }) }
    assertTrue(map.isEmpty())
    assertEquals("broken", map.getOrCreate("broken", Fn1 { Attribute(it) }).key)
    assertFailsWith<NakshaException> { Attribute("") }
  }

  @Test
  fun conditionalMutationsUseValueEqualityOnEveryPlatform() {
    val map = AttributeMap()
    val first = Value("key", "first")
    val second = Value("key", "second")
    assertNull(map.putIfAbsent("key", first))
    assertSame(first, map.putIfAbsent("key", second))
    assertFalse(map.replace("key", second, first))
    assertTrue(map.replace("key", Value("key", "first"), second))
    assertTrue(map.compareAndSet("key", Value("key", "second"), first))
    assertFalse(map.remove("key", second))
    assertTrue(map.remove("key", Value("key", "first")))
    assertTrue(map.compareAndSet("key", null, second))
    assertTrue(map.compareAndSet("key", Value("key", "second"), null))
    assertTrue(map.compareAndSet("key", null, null))
  }

  @Test
  fun mapEqualityHashingAndMutableViewsFollowTheBackingMap() {
    val map = AttributeMap()
    val value = Value("key", "first")
    map["key"] = value
    val expected = mapOf<String, Attribute>("key" to value)
    assertEquals(expected, map)
    assertEquals(map, expected)
    assertEquals(expected.hashCode(), map.hashCode())
    map.entries.first().setValue(Value("key", "second"))
    assertEquals("second", (map["key"] as Value).payload)
    assertTrue(map.keys.remove("key"))
    map.putAll(expected)
    map.values.clear()
    assertTrue(map.isEmpty())
  }
}
