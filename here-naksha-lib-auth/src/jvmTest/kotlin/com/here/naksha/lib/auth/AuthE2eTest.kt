package com.here.naksha.lib.auth

import org.junit.jupiter.api.Test

class AuthE2eTest {

    @Test
    fun `should parse and match URM and AMR combination`(){
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

        // When: parsing both Auth matrices
        val urm = JvmAuthParser.parseUrm(rawUrm)
        val arm = JvmAuthParser.parseArm(rawArm)

        // And: checking the access-match between them
        val access = urm.matches(arm)

        // Then: access should be granted
        access.assertTrue()
    }
}