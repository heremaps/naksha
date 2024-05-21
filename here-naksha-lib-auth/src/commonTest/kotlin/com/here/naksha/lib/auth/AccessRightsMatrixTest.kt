package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.XyzFeatureAttributes
import com.here.naksha.lib.base.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccessRightsMatrixTest {

    @Test
    fun shouldReturnUnregisteredService() {
        // Given: ARM without service
        val arm = AccessRightsMatrix()

        // When
        val service = arm.getService("some_service")

        // Then
        assertNotNull(service)

        // When
        service.withAction(
            ReadFeatures()
                .withAttributes(
                    XyzFeatureAttributes()
                        .id("feature_1")
                        .storageId("storage_1")
                )
        )

        // Then
        val rfAttributes = arm.getService("some_service")
            .getActionAttributeMaps(ReadFeatures.READ_FEATURES_ACTION_NAME)
        assertNotNull(rfAttributes)
        assertEquals(1, rfAttributes.size)
        assertEquals("feature_1", rfAttributes[0].data()["id"])
        assertEquals("storage_1", rfAttributes[0].data()["storageId"])
    }
}