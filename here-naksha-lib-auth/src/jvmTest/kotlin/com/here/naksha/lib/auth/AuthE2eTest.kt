package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.auth.matrices.UpmMatrix
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix

class AuthE2eTest {

//    @Test TODO
    fun `should parse and match URM and AMR combination`() {
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

        // And: raw ARM json
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
        val urm: UserRightsMatrix = com.here.naksha.lib.auth.JvmAuthParser.parseUrm(rawUrm)
        val arm: UpmMatrix = com.here.naksha.lib.auth.JvmAuthParser.parseArm(rawArm)

        val goodArm = NakshaArmBuilder()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        FeatureAttributes()
                            .id("my-unique-id")
                            .storageId("asda")
                    )
            )
            .buildArm()

        // And: checking the access-match between them
        val access = urm.matches(arm)

        // Then: access should be granted
        access.assertTrue()
    }
}