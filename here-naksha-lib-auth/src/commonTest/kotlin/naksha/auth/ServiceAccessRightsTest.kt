package naksha.auth

import naksha.auth.action.ManageEventHandlers
import naksha.auth.action.ReadFeatures
import naksha.auth.attribute.EventHandlerAttributes
import naksha.auth.attribute.FeatureAttributes
import naksha.auth.attribute.FeatureAttributes.Companion.COLLECTION_ID_KEY
import naksha.auth.attribute.FeatureAttributes.Companion.STORAGE_ID_KEY
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
        val retrievedFeatureAttributes = service.getResourceAttributesForAction(ReadFeatures.NAME)
        assertNotNull(retrievedFeatureAttributes)
        assertContentEquals(retrievedFeatureAttributes, readFeaturesAction)

        // And
        val retrievedHandlerAttributes = service.getResourceAttributesForAction(ManageEventHandlers.NAME)
        assertNotNull(retrievedHandlerAttributes)
        assertContentEquals(retrievedHandlerAttributes, manageEventHandlers)
    }

    @Test
    fun shouldReturnNullWhenRequestedMissingAction() {
        // Given:
        val service = ServiceAccessRights()

        // Then
        assertNull(service.getResourceAttributesForAction(ReadFeatures.NAME))
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
        val readFeaturesAttrs = leftService.getResourceAttributesForAction(ReadFeatures.NAME)
        assertNotNull(readFeaturesAttrs)
        assertEquals(2, readFeaturesAttrs.size)
        assertEquals("s_1", readFeaturesAttrs[0]!![STORAGE_ID_KEY])
        assertEquals("s_2", readFeaturesAttrs[1]!![STORAGE_ID_KEY])
        assertEquals("c_2", readFeaturesAttrs[1]!![COLLECTION_ID_KEY])
    }
}