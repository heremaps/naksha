package naksha.base

import kotlin.test.*

class PlatformTest {
    // TODO: Fix the commented out type casting
    @Test
    fun testFromJSON() {
        Platform.parseDataUrl = true
        val raw = Platform.fromJSON("""{
  "id": "Foo",
  "properties": {
    "@ns:com:here:xyz": {
      "someInt": 14,
      "bigInt": 9007199254740991,
      "hexBigInt": "data:bigint;hex,0x1fffffffffffff",
      "decimalBigInt": "data:bigint;dec,9007199254740991",
      "tags": ["a", "b"]
    }
  }
}""")
        val map = assertIs<PlatformMap>(raw)
        val feature = map.proxy(P_JsMap::class)
        assertEquals("Foo", feature["id"])
        val properties = feature.getAs("properties", P_JsMap::class)
        assertNotNull(properties)
        val xyz = properties.getAs("@ns:com:here:xyz", P_JsMap::class)
        assertNotNull(xyz)
        assertEquals(14, xyz.getAs("someInt", Int::class))
        assertTrue(xyz.get("bigInt") is Int64)
        val int64 = xyz.getAs("hexBigInt", Int64::class)
        assertEquals(Int64(9007199254740991), int64)
        assertEquals(Int64(9007199254740991), xyz.getAs("decimalBigInt", Int64::class))
        val tags = properties.getAs("tags", P_StringList::class)
//        assertNotNull(tags)
//        assertEquals(2, tags.size)
//        assertEquals("a", tags[0])
//        assertEquals("b", tags[1])
    }
}