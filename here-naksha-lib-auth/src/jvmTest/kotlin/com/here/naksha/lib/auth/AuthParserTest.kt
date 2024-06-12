package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.attribute.ResourceAttributes
import naksha.base.JvmList
import naksha.base.Proxy
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
        val parsedUrm = AuthParser.parseUrm(rawUrm)

        // Then: 'naksha' service is defined
        val naksha = parsedUrm.getService("naksha")
        assertNotNull(naksha)
        val readFeatures = naksha["readFeatures"]
        assertNotNull(readFeatures)
        assertEquals(1, readFeatures!!.size)
        val attributeMap = readFeatures[0]!!
        assertEquals("my-unique-feature-id", attributeMap["id"])
        assertEquals("id-with-wild-card-*", attributeMap["storageId"])
        val tags = attributeMap["tags"] as JvmList
        assertEquals(2, tags.size)
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
        val parsedArm = AuthParser.parseArm(rawArm)

        // Then:
        val naksha = parsedArm.getService("naksha")
        assertNotNull(naksha)
        val readFeatures = naksha.getActionAttributeMaps("readFeatures")
        assertEquals(1, readFeatures!!.size)
        val attributeMap = Proxy.box(readFeatures[0]!!, ResourceAttributes::class)!!
        assertEquals("my-unique-feature-id", attributeMap["id"])
        assertEquals("id-with-wild-card-matching-value", attributeMap["storageId"])
        assertEquals("unused-id-during-checks", attributeMap["collectionId"])
        val tags = attributeMap["tags"] as JvmList
        assertEquals(3, tags.size)
        assertEquals("my-unique-tag", tags[0])
        assertEquals("some-common-tag-with-wild-card-matching-value", tags[1])
        assertEquals("some-additional-tag", tags[2])
    }
}