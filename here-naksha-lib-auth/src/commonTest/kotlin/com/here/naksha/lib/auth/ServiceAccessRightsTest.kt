package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.ManageEventHandlers
import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.EventHandlerAttributes
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.auth.attribute.FeatureAttributes.Companion.COLLECTION_ID_KEY
import com.here.naksha.lib.auth.attribute.FeatureAttributes.Companion.STORAGE_ID_KEY
import kotlin.test.*


class ServiceAccessRightsTest {

    @Test
    fun shouldAddAndRetrieveActions() {
        // Given:
        val readFeaturesAction = ReadFeatures().withAttributes(
            FeatureAttributes()
                .id("feature_1")
                .collectionId("collection"),
            FeatureAttributes()
                .id("feature_2")
                .storageId("storage")
        )
        val manageEventHandlers = ManageEventHandlers().withAttributes(
            EventHandlerAttributes()
                .id("event_handler")
                .className("SpecificHandlerClass")
        )

        // When:
        val service = ServiceAccessRights()
            .withAction(readFeaturesAction)
            .withAction(manageEventHandlers)

        // Then:
        val retrievedFeatureAttributes = service.getActionAttributeMaps(ReadFeatures.NAME)
        assertNotNull(retrievedFeatureAttributes)
        assertContentEquals(retrievedFeatureAttributes, readFeaturesAction)

        // And
        val retrievedHandlerAttributes = service.getActionAttributeMaps(ManageEventHandlers.NAME)
        assertNotNull(retrievedHandlerAttributes)
        assertContentEquals(retrievedHandlerAttributes, manageEventHandlers)
    }

    @Test
    fun shouldReturnNullWhenRequestedMissingAction() {
        // Given:
        val service = ServiceAccessRights()

        // Then
        assertNull(service.getActionAttributeMaps(ReadFeatures.NAME))
    }

    @Test
    fun shouldMergeActionsProperly() {
        // Given:
        val leftService = ServiceAccessRights()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        FeatureAttributes()
                            .storageId("s_1")
                    )
            )

        // And:
        val rightService = ServiceAccessRights()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        FeatureAttributes()
                            .storageId("s_2")
                            .collectionId("c_2")
                    )
            )

        // When:
        leftService.mergeActionsFrom(rightService)

        // Then:
        val readFeaturesAttrs = leftService.getActionAttributeMaps(ReadFeatures.NAME)
        assertNotNull(readFeaturesAttrs)
        assertEquals(2, readFeaturesAttrs.size)
        assertEquals("s_1", readFeaturesAttrs[0]!![STORAGE_ID_KEY])
        assertEquals("s_2", readFeaturesAttrs[1]!![STORAGE_ID_KEY])
        assertEquals("c_2", readFeaturesAttrs[1]!![COLLECTION_ID_KEY])
    }
}