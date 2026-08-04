package naksha.base

import kotlin.test.*

class BaseTest {
    @Test
    fun testFromJSON() {
        val raw = Base.fromJSON(
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
        val feature = map.proxy(PAnyMap::class)
        assertEquals("Foo", feature["id"])
        val properties = feature.getAs("properties", PAnyMap::class)
        assertNotNull(properties)
        val xyz = properties.getAs("@ns:com:here:xyz", PAnyMap::class)
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
        val data = PAnyMap()
        data["name"] = "Mustermann"
        data["age"] = 69
        data["boolean"] = true
        data["array"] = PAnyArray()
        (data["array"] as PAnyArray).add("a")
        (data["array"] as PAnyArray).add("b")
        (data["array"] as PAnyArray).add("c")
        data["map"] = PAnyMap()
        (data["map"] as PAnyMap)["foo"] = "bar"
        val json = Base.toJSON(data)
        Base.logger.info("json: {}", json)
        val jsonString = "{\"name\":\"Mustermann\",\"age\":69,\"boolean\":true,\"array\":[\"a\",\"b\",\"c\"],\"map\":{\"foo\":\"bar\"}}"
        assertEquals(jsonString, json)
    }

    @Test
    fun testNormalization() {
        // given
        val str = "Åh no ﬁ"

        // expect
        assertEquals("\u0041\u030Ah no \uFB01", Base.normalize(str, NormalizerForm.NFD))
        assertEquals("\u00C5h no \uFB01", Base.normalize(str, NormalizerForm.NFC))
        assertEquals("\u00C5h no \u0066\u0069", Base.normalize(str, NormalizerForm.NFKC))
        assertEquals("\u0041\u030Ah no \u0066\u0069", Base.normalize(str, NormalizerForm.NFKD))
    }
}