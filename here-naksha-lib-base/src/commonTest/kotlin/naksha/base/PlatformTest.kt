package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.Platform.PlatformCompanion.identityHashCode
import naksha.base.PlatformMapApi.PlatformMapApiCompanion.map_get
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeDouble
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeInt
import naksha.base.PlatformUtil.PlatformUtilCompanion.asSafeInt64
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalDouble
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalInt
import naksha.base.PlatformUtil.PlatformUtilCompanion.isLogicalInt64
import kotlin.test.*

class PlatformTest {
    @Test
    fun testFromJSON() {
        val raw = Platform.fromJson(
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
}""", Any_TYPE, FromJsonOptions(true))
        val feature = assertIs<AnyObject>(raw)
        assertEquals("Foo", feature["id"])
        val properties = feature.getAs("properties", AnyObject.TYPE)
        assertNotNull(properties)
        val xyz = properties.getAs("@ns:com:here:xyz", AnyObject.TYPE)
        assertNotNull(xyz)
        assertEquals(14, xyz.getAs("someInt", Int_Type))
        assertTrue(xyz["bigInt"] is Number)
        val hexBigInt = xyz["hexBigInt"]
        assertTrue(hexBigInt is Int64)
        assertEquals(Int64(9007199254740991L), hexBigInt)
        val decimalBigInt = xyz["decimalBigInt"]
        assertTrue(decimalBigInt is Int64)
        assertEquals(Int64(9007199254740991L), decimalBigInt)
        val tags = xyz.getAs("tags", StringList.TYPE)
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

    @Test
    fun testFromJson() {
        val json = """
            {
                "company": "Abc",
                "staff": [
                    {
                        "name": "John",
                        "id": 123,
                        "seniority": "junior",
                        "roles": [
                            {
                                "system": "s1",
                                "role": "admin"
                            },
                            {
                                "system": "s2",
                                "role": "user"
                            }
                        ]
                    },
                    {
                        "name": "Phil",
                        "id": 456,
                        "seniority": "senior",
                        "roles": [
                            {
                                "system": "s1",
                                "role": "admin"
                            },
                            {
                                "system": "s2",
                                "role": "admin"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()
        val map = assertIs<AnyObject>(Platform.fromJson(json))
        assertEquals(2, map.size)
        val keys: MutableSet<String> = map.keys
        val keysIt: MutableIterator<String> = keys.iterator()
        assertTrue(keysIt.hasNext())
        var key = keysIt.next()
        assertEquals("company", key)
        assertTrue(keysIt.hasNext())
        key = keysIt.next()
        assertEquals("staff", key)
        assertFalse(keysIt.hasNext())

        val entries = map.entries
        val entryIt = entries.iterator()
        assertTrue(entryIt.hasNext())
        var entry = entryIt.next()
        assertNotNull(entry)
        assertEquals("company", entry.key)
        assertEquals("Abc", entry.value)
        assertTrue(entryIt.hasNext())
        entry = entryIt.next()
        assertEquals("staff", entry.key)
        val list = assertIs<AnyList>(entry.value)
        assertFalse(entryIt.hasNext())
        assertEquals(2, list.size)
        val elementsIt: MutableIterator<Any?> = list.iterator()
        assertTrue(elementsIt.hasNext())
        var element = elementsIt.next()
        assertIs<AnyObject>(element)
        assertTrue(elementsIt.hasNext())
        element = elementsIt.next()
        assertIs<AnyObject>(element)
        assertFalse(elementsIt.hasNext())
    }

    @Test
    fun testLogicalInt() {
        val f32 = 32.0f
        assertTrue(isLogicalInt(f32))
        assertTrue(isLogicalInt64(f32))
        assertFalse(isLogicalDouble(f32))

        assertEquals(32, asSafeInt(f32))
        assertEquals(Int64(32), asSafeInt64(f32))
        assertEquals(32.0, asSafeDouble(f32))
    }

    @Test
    fun testLogicalInt64() {
        val f64 = 9007199254740991.0
        assertFalse(isLogicalInt(f64))
        assertTrue(isLogicalInt64(f64))
        assertFalse(isLogicalDouble(f64))

        assertNull(asSafeInt(f64))
        assertEquals(Int64(9007199254740991.0), asSafeInt64(f64))
        assertEquals(9007199254740991.0, asSafeDouble(f64))
    }

    @Test
    fun testLogicalDouble() {
        val f64 = 9254740992.12
        assertFalse(isLogicalInt(f64))
        assertFalse(isLogicalInt64(f64))
        assertTrue(isLogicalDouble(f64))

        assertNull(asSafeInt(f64))
        assertNull(asSafeInt64(f64))
        assertEquals(9254740992.12, asSafeDouble(f64))
    }

    private class BarMap : AnyObject() {
        init {
            this["name"] = "Unknown"
            this["surname"] = "Unknown"
            this["other"] = "Unknown"
        }
    }

    private class BarList : ListProxy<Double>(Double_TYPE) {
        init {
            this[0] = 0.0
            this[1] = 1.0
        }
    }

    @Test
    fun testPlatformMapRebind() {
        val foo = AnyObject()
        foo["name"] = "John"
        foo["surname"] = "Smith"
        val bar = foo.proxy(forKClass(BarMap::class))
        assertSame(foo.platformObject(), bar.platformObject())
        assertEquals("John", bar["name"])
        assertEquals("Smith", bar["surname"])
        assertEquals("Unknown", bar["other"])
    }

    @Test
    fun testPlatformListRebind() {
        val foo = DoubleList()
        val bar = foo.proxy(forKClass(BarList::class))
        assertSame(foo.platformObject(), bar.platformObject())
        assertEquals(2, bar.size)
        assertEquals(0.0, bar[0])
        assertEquals(1.0, bar[1])
    }

    class MyJsonType : AnyObject() {
        companion object MyJsonTypeCompanion {
            val TYPE = forKClass(MyJsonType::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("myFooBar")
        }
    }

    class MyFooBar : AnyObject() {
        companion object MyFooBarCompanion {
            val TYPE = forKClass(MyFooBar::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("myFooBar")
        }
    }

    @Test
    fun testJsonType() {
        MyJsonType.TYPE.initialize()
        MyFooBar.TYPE.initialize()

        val existing = Platform.forJsonType("myFooBar")
        assertNotNull(existing)
        assertEquals(2, existing.size)
        assertSame(MyJsonType.TYPE, existing[0])
        assertSame(MyFooBar.TYPE, existing[1])
    }

    @Test
    fun testFirstJsonType() {
        MyJsonType.TYPE.initialize()
        MyFooBar.TYPE.initialize()

        val type = Platform.forFirstJsonType("myFooBar", forKClass(MyFooBar::class))
        assertNotNull(type)
        assertSame(MyFooBar.TYPE, type)
    }

    @Test
    fun testIdentifyHashCode() {
        // Note: It should be `Double?` to ensure we do not get autoboxing below, could create two idents!
        @Suppress("RedundantNullableReturnType") //
        val number: Double? = 5.0
        val numberId = identityHashCode(number)
        assertEquals(numberId, identityHashCode(number))

        val list = AnyList()
        val listId = identityHashCode(list)
        assertEquals(listId, identityHashCode(list))

        val list2 = AnyList()
        val list2Id = identityHashCode(list2)
        assertEquals(list2Id, identityHashCode(list2))

        assertNotEquals(listId, list2Id)
    }

    class MyFooObject : AnyObject() {
        companion object MyFooObjectCompanion {
            val TYPE = forKClass(MyFooObject::class).withPackageName(PACKAGE_NAME)
            private val ID_MEMBER = NotNullProperty<MyFooObject, String>(String_TYPE)
        }

        var id: String by ID_MEMBER
    }

    class MyFooObjectDetector : TypeDetector {
        override fun detectMap(map: PlatformMap): PlatformType<out MapProxy<*, *>>?
            = if ("MyFooObject" == map_get(map, "@customType")) MyFooObject.TYPE else null
    }

    @Test
    fun testTypeDetection() {
        val json = """{
    "id": "Hello",
    "@customType": "MyFooObject"
}"""
        val detectors = AtomicSet<TypeDetector>(arrayOf(MyFooObjectDetector()))
        val fromJsonOptions = FromJsonOptions(detectors = detectors)
        val fooObject = assertIs<MyFooObject>(Platform.fromJson(json, fromJsonOptions))
        assertEquals("Hello", fooObject.id)
    }

    @Test
    fun testGlobalTypeDetection() {
        val json = """{
    "id": "Hello",
    "@customType": "MyFooObject"
}"""
        val detector = MyFooObjectDetector()
        Platform.globalDetectors.add(detector)
        try {
            val fooObject = assertIs<MyFooObject>(Platform.fromJson(json))
            assertEquals("Hello", fooObject.id)
        } finally {
            Platform.globalDetectors.remove(detector)
        }
    }

}