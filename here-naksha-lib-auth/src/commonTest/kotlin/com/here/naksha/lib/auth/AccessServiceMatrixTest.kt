package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.action.ReadFeatures.Companion.READ_FEATURES_ACTION_NAME
import com.here.naksha.lib.auth.attribute.XyzFeatureAttributes
import com.here.naksha.lib.base.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccessServiceMatrixTest {

    @Test fun shouldAddAttributesToAnAction() {
        // Given
        val serviceMatrix = AccessServiceMatrix()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        XyzFeatureAttributes()
                            .id("id_123"),
                        XyzFeatureAttributes()
                            .storageId("storage_1")
                    )
            )

        // When:
        serviceMatrix.withActionAttributeMaps(
            actionName = READ_FEATURES_ACTION_NAME,
            AccessAttributeMap("one", "A"),
            AccessAttributeMap("two", "B"),
            AccessAttributeMap("three", "C")
        )

        // Then:
        val attributeMaps = serviceMatrix.getActionAttributeMaps(READ_FEATURES_ACTION_NAME)
        assertNotNull(attributeMaps)
        assertEquals(5, attributeMaps.size)
        listOf(
             "id" to "id_123",
             "storageId" to "storage_1",
             "one" to "A",
             "two" to "B",
             "three" to "C"
        ).forEachIndexed { index, (expectedKey, expectedValue) ->
            val attributeData = attributeMaps[index].data()
            assertEquals(expectedValue, attributeData[expectedKey])
        }
    }

    @Test
    fun shouldMergeActionsProperly(){
        // Given:
        val leftService = AccessServiceMatrix()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        XyzFeatureAttributes()
                            .storageId("s_1")
                    )
            )

        // And:
        val rightService = AccessServiceMatrix()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        XyzFeatureAttributes("s_2")
                            .collectionId("c_2")
                    )
            )

        // When:
        leftService.mergeActionsFrom(rightService)

        // Then:
        assertNotNull(leftService)
    }
}