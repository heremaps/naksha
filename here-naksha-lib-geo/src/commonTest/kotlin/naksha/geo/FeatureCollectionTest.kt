package naksha.geo

import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.Platform.Platform_C.fromJson
import naksha.base.String_TYPE
import kotlin.test.*

// Note: We declare two new types, one being a collection, the other a feature
//       Both types have the same feature-type, being "theFoo"
//       Still, the auto-detection should be able to detect if it is the feature or the collection, based upon the type discriminator.
class FeatureCollectionTest {
    class FooCollection : GeoCollection() {
        companion object FooCollection_C {
            @Suppress("unused")
            val TYPE = forKClass(FooCollection::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("theFoo")

            private val NAME_MEMBER = NullableProperty<FooCollection, String>(String_TYPE)
        }

        var name: String? by NAME_MEMBER
    }

    class FooFeature : GeoFeature() {
        companion object FooFeature_C {
            @Suppress("unused")
            val TYPE = forKClass(FooFeature::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("theFoo")

            private val NAME_MEMBER = NullableProperty<FooFeature, String>(String_TYPE)
        }

        var name: String? by NAME_MEMBER
    }

    @Test
    fun testFooFeature() {
        assertTrue(FooFeature.TYPE.isFeature)
        assertFalse(FooFeature.TYPE.isFeatureCollection)
        assertFalse(FooFeature.TYPE.isMomType)
        assertFalse(FooFeature.TYPE.isDataHubType)
    }

    @Test
    fun testFooCollection() {
        assertFalse(FooCollection.TYPE.isFeature)
        assertTrue(FooCollection.TYPE.isFeatureCollection)
        assertFalse(FooCollection.TYPE.isMomType)
        assertFalse(FooCollection.TYPE.isDataHubType)
    }

    @Test
    fun testOwnCollectionType() {
        val json = """
{
  "type": "FeatureCollection",
  "featureType": "theFoo",
  "name": "Foo"
}
"""
        val collection = assertIs<FooCollection>(fromJson(json))
        assertEquals("Foo", collection.name)
    }

    @Test
    fun testOwnFeatureType() {
        val json = """
{
  "type": "Feature",
  "featureType": "theFoo",
  "name": "Foo"
}
"""
        val feature = assertIs<FooFeature>(fromJson(json))
        assertEquals("Foo", feature.name)
    }
}