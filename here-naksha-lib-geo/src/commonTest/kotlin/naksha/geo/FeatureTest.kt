package naksha.geo

import naksha.base.NullableProperty
import naksha.base.Platform
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.fromJson
import naksha.base.String_TYPE
import kotlin.test.*

class FeatureTest {
    @Test
    fun testPolygonFeature() {
        // given
        val polygonJson = """
    {
      "type": "Feature",
      "id": "Example",
      "bbox": [100.0, 0.0, -100.0, 105.0, 1.0, 0.0],
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [
            [
              20.6899332,
               6.6805668
            ],
            [
              20.5619886,
               4.7968097
            ],
            [
              20.6899332,
               6.6805668
            ]
          ]
        ]
      }
    }
"""

        val feature = assertNotNull(fromJson(polygonJson, GeoFeature.TYPE))
        assertEquals("Feature", feature.type)
        assertEquals("Example", feature.id)
        val bbox = feature.bbox
        assertNotNull(bbox)
        assertEquals(6, bbox.size)
        assertEquals(100.0, bbox[0])
        assertEquals(0.0, bbox[1])
        assertEquals(-100.0, bbox[2])
        assertEquals(105.0, bbox[3])
        assertEquals(1.0, bbox[4])
        assertEquals(0.0, bbox[5])

        val polygon = assertIs<SpPolygon>(feature.geometry)
        val polygonCoords = polygon.coordinates
        assertEquals(1, polygon.coordinates.size)
        val polyCoords = assertNotNull(polygonCoords[0])
        assertEquals(3, polyCoords.size)
        val p = assertNotNull(polyCoords[0])
        assertEquals(20.6899332, p.longitude)
        assertEquals(6.6805668, p.latitude)
    }

    open class Foo : GeoFeature() {
        companion object Foo_C {
            val TYPE = forKClass(Foo::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("foo")

            private val NAME_MEMBER = NullableProperty<Foo, String>(String_TYPE)
        }

        var name: String? by NAME_MEMBER
    }

    class DataHubFoo : Foo() {
        companion object DataHubFoo_C {
            val TYPE = forKClass(DataHubFoo::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("dataHubFoo")
                .withIsDataHubType(true)
        }
    }

    class MomFoo : Foo() {
        companion object MomFoo_C {
            val TYPE = forKClass(MomFoo::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("momFoo")
                .withIsMomType(true)
                .withIsDataHubType(true)
        }
    }

    @Test
    fun testOwnType() {
        Foo.TYPE.initialize()
        val json = """{
    "id": "demo",
    "type": "Feature",
    "featureType": "foo"
  }"""
        val foo = fromJson(json)
        assertIs<Foo>(assertNotNull(foo))
    }

    @Test
    fun testFooToJson() {
        val feature = Foo()
        feature.id = "demo"
        feature.name = "Example"
        val json = Platform.toJson(feature)
        val expect = """{"type":"Feature","featureType":"foo","id":"demo","name":"Example"}"""
        assertEquals(expect, json)
    }

    @Test
    fun testDataHubFooToJson() {
        DataHubFoo.TYPE.initialize()
        val feature = DataHubFoo()
        feature.id = "demo"
        feature.name = "Example"
        val json = Platform.toJson(feature)
        val expect = """{"type":"Feature","properties":{"featureType":"dataHubFoo"},"id":"demo","name":"Example"}"""
        assertEquals(expect, json)
    }

    @Test
    fun testMomFooToJson() {
        MomFoo.TYPE.initialize()
        val feature = MomFoo()
        feature.id = "demo"
        feature.name = "Example"
        val json = Platform.toJson(feature)
        val expect = """{"type":"Feature","momType":"momFoo","properties":{"featureType":"momFoo"},"id":"demo","name":"Example"}"""
        assertEquals(expect, json)
    }

    @Test
    fun removeGeometry() {
        val feature = GeoFeature()
        feature.id = "demo"
        feature.geometry = SpPoint(0.0, 0.0)
        feature.apply {
            assertEquals(3, size)
            assertTrue(containsKey("id"))
            assertTrue(containsKey("type"))
            assertTrue(containsKey("geometry"))
        }

        feature.geometry = null
        feature.apply {
            assertEquals(2, size)
            assertTrue(containsKey("id"))
            assertTrue(containsKey("type"))
            assertFalse(containsKey("geometry"))
        }
    }
}