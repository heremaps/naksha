@file:Suppress("OPT_IN_USAGE")

package naksha.mom.v2

import naksha.base.Platform
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.test.*

class Mom2Test {
    class CustomMomFeature : MomFeature() {
        companion object CustomMomFeature_C {
            @JvmField
            @JsStatic
            val TYPE = forKClass(CustomMomFeature::class)
                .withPackageName(PACKAGE_NAME)
                .withJsonType("CustomMomType")
        }
    }

    @Test
    fun mom2_testFeature() {
        // Register our new type.
        CustomMomFeature.TYPE.initialize()

        // Let's work with it.
        val json = """
{
    "id": "foo",
    "type": "Feature",
    "momType": "CustomMomType",
    "properties": {
        "@ns:com:here:mom:delta": {
            "originId": "test"
        }
    }
}
"""
        val feature: CustomMomFeature = assertIs<CustomMomFeature>(Platform.fromJson(json))
        feature.apply {
            assertEquals(4, size)
            assertEquals("foo", id)
            assertEquals("CustomMomType", type)
            assertTrue(isFeature())
            assertTrue(isMomType())
            assertFalse(isDataHubType())

            assertTrue(containsKey("type"))
            assertEquals("Feature", getRaw("type"))
            assertFalse(containsKey("featureType"))
            assertTrue(containsKey("momType"))
            assertEquals("CustomMomType", getRaw("momType"))
            assertTrue(containsKey("properties"))
            properties.apply {
                assertNotNull(this.delta).apply {
                    assertEquals("test", originId)
                }
            }
        }
    }
}
