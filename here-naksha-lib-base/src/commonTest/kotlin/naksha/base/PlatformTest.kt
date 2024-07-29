package naksha.base

import kotlin.test.*

class PlatformTest {
    @Test
    fun testFromJSON() {
        val raw = Platform.fromJSON(
            """{
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
}""", FromJsonOptions(true))
        val map = assertIs<PlatformMap>(raw)
        val feature = map.proxy(AnyObject::class)
        assertEquals("Foo", feature["id"])
        val properties = feature.getAs("properties", AnyObject::class)
        assertNotNull(properties)
        val xyz = properties.getAs("@ns:com:here:xyz", AnyObject::class)
        assertNotNull(xyz)
        assertEquals(14, xyz.getAs("someInt", Int::class))
        assertTrue(xyz["bigInt"] is Number)
        val hexBigInt = xyz["hexBigInt"]
        assertTrue(hexBigInt is Int64)
        assertEquals(Int64(9007199254740991L), hexBigInt)
        val decimalBigInt = xyz["decimalBigInt"]
        assertTrue(decimalBigInt is Int64)
        assertEquals(Int64(9007199254740991L), decimalBigInt)
        val tags = xyz.getAs("tags", StringList::class)
        assertNotNull(tags)
        assertEquals(2, tags.size)
        assertEquals("a", tags[0])
        assertEquals("b", tags[1])
    }

    @Test
    fun testToJson() {
        val data = AnyObject()
        data["name"] = "Mustermann"
        data["age"] = 69
        data["boolean"] = true
        data["array"] = AnyList()
        (data["array"] as AnyList).add("a")
        (data["array"] as AnyList).add("b")
        (data["array"] as AnyList).add("c")
        data["map"] = AnyObject()
        (data["map"] as AnyObject)["foo"] = "bar"
        val json = Platform.toJSON(data)
        Platform.logger.info("json: {}", json)
        val jsonString = "{\"name\":\"Mustermann\",\"age\":69,\"boolean\":true,\"array\":[\"a\",\"b\",\"c\"],\"map\":{\"foo\":\"bar\"}}"
        assertEquals(jsonString, json)
    }

    @Test
    fun testNormalization() {
        // given
        val str = "Åh no ﬁ"

        // expect
        assertEquals("\u0041\u030Ah no \uFB01", Platform.normalize(str, NormalizerForm.NFD))
        assertEquals("\u00C5h no \uFB01", Platform.normalize(str, NormalizerForm.NFC))
        assertEquals("\u00C5h no \u0066\u0069", Platform.normalize(str, NormalizerForm.NFKC))
        assertEquals("\u0041\u030Ah no \u0066\u0069", Platform.normalize(str, NormalizerForm.NFKD))
    }
}