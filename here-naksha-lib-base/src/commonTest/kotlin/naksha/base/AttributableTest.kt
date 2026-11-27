package naksha.base

import naksha.base.fn.Fn2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AttributableTest {
  private class Owner : Attributable()
  private class Owned(id: Id, key: String) : IdAttribute(id, key)

  @Test
  fun eachOwnerHasItsOwnStableMutableMap() {
    val first = Owner()
    val second = Owner()
    val map = first.attributes
    assertTrue(map.isEmpty())
    assertSame(map, first.attributes)
    assertNotSame(map, second.attributes)
    val value = Attribute("sample")
    map[value.key] = value
    assertSame(value, first.attributes[value.key])
    map.clear()
    assertSame(map, first.attributes)
    assertTrue(first.attributes.isEmpty())
  }

  @Test
  fun identifierSpecificValuesRetainTheirOwnerWithoutChangingIdentifierEquality() {
    val id = Id(42L)
    val equal = Id(42L)
    val hash = id.hashCode()
    val constructor = Fn2<Owned, String, Id> { key, owner -> Owned(owner, key) }
    val attr = id.attributes.getOrCreate("id-owned", id, constructor)
    assertSame(id, attr.id)
    assertSame(attr, id.attributes.getOrCreate("id-owned", id, constructor))
    assertEquals(equal, id)
    assertEquals(hash, id.hashCode())
    assertEquals(0, id.compareTo(equal))
    assertTrue(equal.attributes.isEmpty())
  }
}
