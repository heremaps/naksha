package naksha.base

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AttributableJsTest {
  private class Owned(id: Id, key: String) : IdAttribute(id, key)

  @Test
  fun nativeSerializationDoesNotExposeTheMapIncludingCyclicValues() {
    val id = Id(42L)
    val before = js("JSON.stringify(id, (key, value) => typeof value === 'bigint' ? String(value) : value)").unsafeCast<String>()
    assertFalse(before.contains("js-attribute"))
    val map = id.attributes
    val attr = Owned(id, "js-attribute")
    map[attr.key] = attr
    assertSame(map, id.attributes)
    assertSame(attr, id.attributes[attr.key])
    assertFalse(js("Object.keys(id).some(key => id[key] === map)").unsafeCast<Boolean>())
    val json = js("JSON.stringify(id, (key, value) => typeof value === 'bigint' ? String(value) : value)").unsafeCast<String>()
    assertFalse(json.contains("js-attribute"))
  }
}
