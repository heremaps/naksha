package com.here.naksha.lib.auth

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class MatrixMatchTest {

    @Test
    fun test() {
        // Given:
        val urm = AuthParser.parseUrm(
            """
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
        )

        // And:
        val arm = AuthParser.parseArm(
            """
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
        )

        // Then:
        try {
            assertTrue { urm.matches(arm) }
        } catch (e: Exception){
            e.printStackTrace()
            fail("meh")
        }
    }

}