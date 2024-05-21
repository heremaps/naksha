package com.here.naksha.lib.auth

import com.here.naksha.lib.base.Base
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import kotlin.test.Test
import kotlin.test.assertTrue

class UserRightsMatrixTest {

    @Test
    fun shouldMatchSimpleArm() {
        // Given: URM
        val rawUrm = Base.newObject(
            // service:
            "naksha", Base.newObject(
                // actions:
                "readFeatures", Base.newArray(
                    // attribute maps:
                    Base.newObject(
                        "id", "my-unique-feature-id",
                        "storageId", "id-with-wild-card-*"
                    )
                )
            )
        )
        val urm = Base.assign(rawUrm, UserRightsMatrix.klass)

        // And: ARM
        val rawArm = Base.newObject(
            // service:
            "naksha", Base.newObject(
                // actions:
                "readFeatures", Base.newArray(
                    // attribute maps:
                    Base.newObject(
                        "id", "my-unique-feature-id",
                        "storageId", "id-with-wild-card-matching-value",
                        "collectionId", "this-should-not-break-anything"
                    )
                )
            )
        )
        val arm = Base.assign(rawArm, AccessRightsMatrix.klass)

        // Then:
        assertTrue { urm.matches(arm) }
    }
}