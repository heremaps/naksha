package naksha.base

import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.identityHashCode
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_get
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeDouble
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeInt
import naksha.base.PlatformUtil.PlatformUtil_C.asSafeInt64
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalDouble
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalInt
import naksha.base.PlatformUtil.PlatformUtil_C.isLogicalInt64
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
        assertEquals(14, xyz.getAs("someInt", Int_TYPE))
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
        val json = Platform.toJson(data)
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
        companion object MyJsonType_C {
            val TYPE = forKClass(MyJsonType::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("myFooBar")
        }
    }

    class MyFooBar : AnyObject() {
        companion object MyFooBar_C {
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
        companion object MyFooObject_C {
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

    class BasicTypedObject : AnyTypedObject() {
        companion object MyTypedObject_C {
            val TYPE = forKClass(BasicTypedObject::class).withJsonType("basic")
        }
    }

    @Test
    fun testBasicTypedObject() {
        BasicTypedObject.TYPE.initialize()
        BasicTypedObject().apply {
            assertEquals("basic", type)

            // Read raw.
            assertEquals("basic", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertFalse(containsKey("featureType"))
            assertFalse(containsKey("properties"))
        }

        val json = """{
    "type": "basic"
}"""
        val myTypedObject = assertIs<BasicTypedObject>(Platform.fromJson(json))
        myTypedObject.apply {
            assertEquals("basic", type)

            // Read raw.
            assertEquals("basic", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertFalse(containsKey("featureType"))
            assertFalse(containsKey("properties"))
        }
    }


    class CustomFeature : AnyTypedObject() {
        companion object CustomFeature_C {
            val TYPE = forKClass(CustomFeature::class)
                .withJsonType("custom")
                .withIsFeature(true)

            private val FOO_MEMBER = NullableProperty<CustomFeature, Boolean>(Boolean_TYPE)
        }

        var foo: Boolean? by FOO_MEMBER
    }

    @Test
    fun testCustomFeature() {
        CustomFeature.TYPE.initialize()
        CustomFeature().apply {
            assertEquals("custom", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertEquals( "custom", getRaw("featureType"))
            assertFalse(containsKey("properties"))
        }

        val json = """{
    "type": "Feature",
    "featureType": "custom",
    "foo": "abc"
}"""
        val parsed = assertIs<CustomFeature>(Platform.fromJson(json))
        parsed.apply {
            assertEquals("custom", type)
            assertNull(parsed.foo) // because "abc" is no Boolean
            assertEquals("abc", getRaw("foo")) // Because the underlying object still stores the real value

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertEquals( "custom", getRaw("featureType"))
            assertFalse(containsKey("properties"))
        }
    }

    class CustomMomObject : AnyTypedObject() {
        companion object CustomMomObject_C {
            val TYPE = forKClass(CustomMomObject::class)
                .withJsonType("myMom")
                .withIsMomType(true)
        }
    }

    @Test
    fun testMomType() {
        CustomMomObject.TYPE.initialize()
        val new_object = CustomMomObject()
        new_object.apply {
            assertEquals("myMom", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertEquals( "myMom", getRaw("momType"))
            assertFalse(containsKey("featureType"))
            assertFalse(containsKey("properties"))
        }

        // Try parsing.
        val json = """{
    "type": "Feature",
    "momType": "myMom"
}"""
        val parsed = assertIs<CustomMomObject>(Platform.fromJson(json))
        parsed.apply {
            assertEquals("myMom", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertEquals( "myMom", getRaw("momType"))
            assertFalse(containsKey("featureType"))
            assertFalse(containsKey("properties"))
        }
    }

    class CustomDataHubObject : AnyTypedObject() {
        companion object CustomDataHubObject_C {
            val TYPE = forKClass(CustomDataHubObject::class)
                .withJsonType("myDataHub")
                .withIsDataHubType(true)
        }
    }

    @Test
    fun testCustomDataHubType() {
        CustomDataHubObject.TYPE.initialize()
        CustomDataHubObject().apply {
            assertEquals("myDataHub", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertFalse(containsKey("featureType"))
            val properties = assertIs<PlatformMap>(getRaw("properties"))
            assertEquals("myDataHub", map_get(properties, "featureType"))
        }

        // Try parsing.
        val json = """{
    "type": "Feature",
    "properties": {
        "featureType": "myDataHub"
    }
}"""
        val parsed = assertIs<CustomDataHubObject>(Platform.fromJson(json))
        parsed.apply {
            assertEquals("myDataHub", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertFalse(containsKey("momType"))
            assertFalse(containsKey("featureType"))
            val properties = assertIs<PlatformMap>(getRaw("properties"))
            assertEquals("myDataHub", map_get(properties, "featureType"))
        }
    }

    class CustomDataHubMomObject : AnyTypedObject() {
        companion object CustomDataHubMomObject_C {
            val TYPE = forKClass(CustomDataHubMomObject::class)
                .withJsonType("dataHubAndMom")
                .withIsMomType(true)
                .withIsDataHubType(true)
        }
    }

    @Test
    fun testCustomDataHubMomType() {
        CustomDataHubMomObject.TYPE.initialize()
        CustomDataHubMomObject().apply {
            assertEquals("dataHubAndMom", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertEquals( "dataHubAndMom", getRaw("momType"))
            assertFalse(containsKey("featureType"))
            val properties = assertIs<PlatformMap>(getRaw("properties"))
            assertEquals("dataHubAndMom", map_get(properties, "featureType"))
        }

        // Try parsing.
        val json = """{
    "type": "Feature",
    "momType": "dataHubAndMom",
    "properties": {
        "featureType": "dataHubAndMom"
    }
}"""
        val parsed = assertIs<CustomDataHubMomObject>(Platform.fromJson(json))
        parsed.apply {
            assertEquals("dataHubAndMom", type)

            // Read raw.
            assertEquals("Feature", getRaw("type"))
            assertEquals( "dataHubAndMom", getRaw("momType"))
            assertFalse(containsKey("featureType"))
            val properties = assertIs<PlatformMap>(getRaw("properties"))
            assertEquals("dataHubAndMom", map_get(properties, "featureType"))
        }
    }

    @Test
    fun testGzip() {
        try {
            val text = "Some test string test test test test test test test test test test test"
            val raw = text.encodeToByteArray()
            val compressed = Platform.gzipDeflate(raw)
            val restored = Platform.gzipInflate(compressed)
            assertContentEquals(raw, restored)
            val restoredText = restored.decodeToString()
            assertEquals(text, restoredText)
        } catch (_: UnsupportedOperationException) {
            // TODO: We need to implement ZIP in JavaScript!
        }
    }
}