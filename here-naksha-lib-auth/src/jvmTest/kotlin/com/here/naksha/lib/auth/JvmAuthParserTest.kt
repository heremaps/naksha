package com.here.naksha.lib.auth

import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.BaseList
import com.here.naksha.lib.base.get
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JvmAuthParserTest {

    @Test
    fun `should correctly parse URM`() {
        // Given: raw URM json
        val rawUrm = """
        {
            "naksha": {
                "readFeatures": [
                    {
                        "id": "my-unique-feature-id",
                        "storageId": "id-with-wild-card-*",
                        "tags": [  
                            "my-unique-tag",
                            "some-common-tag-with-wild-card-*"
                        ]
                    }
                ]
            }
        }
        """.trimIndent()

        // When: parsing raw URM
        val parsedUrm = JvmAuthParser.parseUrm(rawUrm)

        // Then: 'naksha' service is defined
        val naksha = parsedUrm.getAccessMatrixForService("naksha")
        assertNotNull(naksha)

        // And: attributes for action 'readFeatures` of 'naksha` are defined
        val readFeaturesAttributeMaps = naksha?.getAttributesForAction("readFeatures")
        assertNotNull(readFeaturesAttributeMaps)

        // And: attributes match json payload
        assertEquals(1, readFeaturesAttributeMaps!!.size)
        val attributeMapData = readFeaturesAttributeMaps.get(0).data()
        assertEquals("my-unique-feature-id", attributeMapData["id"])
        assertEquals("id-with-wild-card-*", attributeMapData["storageId"])
        val tags = Base.assign(attributeMapData["tags"]!!, BaseList.klass)
        assertEquals(2, tags.size())
        assertEquals("my-unique-tag", tags[0])
        assertEquals("some-common-tag-with-wild-card-*", tags[1])
    }

    @Test
    fun `should correctly parse ARM`() {
        // Given: raw ARM json
        val rawArm = """
        {
            "naksha": {
                "readFeatures": [
                    {
                        "id": "my-unique-feature-id",
                        "storageId": "id-with-wild-card-matching-value",
                        "collectionId": "unused-id-during-checks",
                        "tags": [
                            "my-unique-tag",
                            "some-common-tag-with-wild-card-matching-value",
                            "some-additional-tag"
                        ]
                    }
                ]
            }
        }
        """.trimIndent()

        // When: parsing raw ARM
        val parsedArm = JvmAuthParser.parseArm(rawArm)

        // Then: 'naksha' service is defined
        val naksha = parsedArm.getAccessMatrixForService("naksha")
        assertNotNull(naksha)

        // And: attributes for action 'readFeatures` of 'naksha` are defined
        val readFeaturesAttributeMaps = naksha?.getAttributesForAction("readFeatures")
        assertNotNull(readFeaturesAttributeMaps)

        // And: attributes match json payload
        assertEquals(1, readFeaturesAttributeMaps!!.size)
        val attributeMapData = readFeaturesAttributeMaps.get(0).data()
        assertEquals("my-unique-feature-id", attributeMapData["id"])
        assertEquals("id-with-wild-card-matching-value", attributeMapData["storageId"])
        assertEquals("unused-id-during-checks", attributeMapData["collectionId"])
        val tags = Base.assign(attributeMapData["tags"]!!, BaseList.klass)
        assertEquals(3, tags.size())
        assertEquals("my-unique-tag", tags[0])
        assertEquals("some-common-tag-with-wild-card-matching-value", tags[1])
        assertEquals("some-additional-tag", tags[2])
    }
}