package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.auth.matrices.UpmMatrix
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class AccessRightsMatrixTest {

    @Test
    fun shouldReturnUnregisteredService() {
        // Given: ARM without service
        val arm = UpmMatrix()

        // When
        val some_service = arm.getService("some_service")

        // Then
        assertNotNull(some_service)

        // When
        some_service.withAction(
            ReadFeatures()
                .withAttributes(
                    FeatureAttributes()
                        .id("feature_1")
                        .storageId("storage_1")
                )
        )
        // Then
        val service = arm.getService("some_service")
        assertSame(some_service, service)
        val attributes = some_service.getActionAttributeMaps(ReadFeatures.NAME)
        assertNotNull(attributes)
//        assertEquals(1, rfAttributes.size)
//        assertEquals("feature_1", rfAttributes[0].data()["id"])
//        assertEquals("storage_1", rfAttributes[0].data()["storageId"])
    }
}