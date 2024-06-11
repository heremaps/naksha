package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRights
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserActionRights
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsMatrix
import com.here.naksha.lib.base.com.here.naksha.lib.auth.UserRightsService
import kotlin.test.Test
import kotlin.test.assertTrue

class UserRightsMatrixTest {

    @Test
    fun shouldMatchSimpleArm() {
        // Given:
        val urm = UserRightsMatrix()
            .withService(
                "sample_service", UserRightsService()
                    .withAction(
                        "readFeatures", UserActionRights()
                            .withCheckMap(
                                UserRights()
                                    .withPropertyCheck("id", "my-unique-feature-id")
                                    .withPropertyCheck("storageId", "id-with-wildcard-*")
                            )

                    )
            )

        // And:
        val arm = AccessRightsMatrix()
            .withService(
                "sample_service",
                AccessRightsService().withAction(
                    ReadFeatures().withAttributes(
                        FeatureAttributes()
                            .id("my-unique-feature-id")
                            .storageId("id-with-wildcard-suffix")
                    )
                )
            )

        // Then:
        assertTrue(urm.matches(arm))
    }
}