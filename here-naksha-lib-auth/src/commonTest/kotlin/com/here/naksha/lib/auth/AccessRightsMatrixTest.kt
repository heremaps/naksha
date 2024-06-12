package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.CreateCollections
import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.CollectionAttributes
import com.here.naksha.lib.auth.attribute.FeatureAttributes
import com.here.naksha.lib.auth.attribute.FeatureAttributes.Companion.STORAGE_ID_KEY
import com.here.naksha.lib.auth.attribute.NakshaAttributes.Companion.ID_KEY
import com.here.naksha.lib.auth.attribute.NakshaAttributes.Companion.TAGS_KEY
import kotlin.test.*

class AccessRightsMatrixTest {

    @Test
    fun shouldReturnUnregisteredService() {
        // Given: ARM without service
        val arm = AccessRightsMatrix()

        // When: getting service that was not in ARM before
        val freshService = arm.getService("some_service")

        // Then: requested service got created
        assertNotNull(freshService)

        // When: editing requested service
        freshService.withAction(
            ReadFeatures()
                .withAttributes(
                    FeatureAttributes()
                        .id("feature_1")
                        .storageId("storage_1")
                )
        )

        // And: fetching this service directly from ARM again
        val modifiedService = arm.getService("some_service")

        // Then: returned instance contains modifications
        assertSame(freshService, modifiedService)
        val attributes = freshService.getActionAttributeMaps(ReadFeatures.NAME)
        assertNotNull(attributes)
        assertEquals(1, attributes.size)
        assertEquals("feature_1", attributes[0]!![ID_KEY])
        assertEquals("storage_1", attributes[0]!![STORAGE_ID_KEY])
    }

    @Test
    fun shouldAddNewService() {
        // Given:
        val arm = AccessRightsMatrix()

        // And:
        val freshService = ServiceAccessRights().withAction(
            CreateCollections().withAttributes(
                CollectionAttributes()
                    .id("c_id")
                    .storageId("s_id")
            )
        )

        // When:
        val serviceName = "some_service"
        arm.withService(serviceName, freshService)

        // Then:
        val retrievedService = arm.getService(serviceName)
        assertSame(retrievedService, freshService)
    }

    @Test
    fun shouldMergeWithExistingService() {
        // Given:
        val arm = AccessRightsMatrix()

        // And:
        val firstService = ServiceAccessRights().withAction(
            CreateCollections().withAttributes(
                CollectionAttributes()
                    .id("c_id")
                    .storageId("s_id")
            )
        )

        // And:
        val secondService = ServiceAccessRights().withAction(
            ReadFeatures().withAttributes(
                FeatureAttributes()
                    .id("f_id")
                    .tags("tag_1", "tag_2")
            )
        )

        // When:
        val serviceName = "some_service"
        arm.withService(serviceName, firstService)

        // And:
        arm.withService(serviceName, secondService)

        // When:
        val retrievedService = arm.getService(serviceName)

        // Then:
        retrievedService[CreateCollections.NAME].let { createCollectionsAttrs ->
            assertNotNull(createCollectionsAttrs)
            assertEquals(1, createCollectionsAttrs.size)
            assertEquals("c_id", createCollectionsAttrs[0]!![ID_KEY])
            assertEquals("s_id", createCollectionsAttrs[0]!![CollectionAttributes.STORAGE_ID_KEY])
        }
        retrievedService[ReadFeatures.NAME].let { readFeaturesAttrs ->
            assertNotNull(readFeaturesAttrs)
            assertEquals(1, readFeaturesAttrs.size)
            assertEquals("f_id", readFeaturesAttrs[0]!![ID_KEY])
            assertContentEquals(
                arrayOf("tag_1", "tag_2"),
                readFeaturesAttrs[0]!![TAGS_KEY] as Array<String>
            )
        }
    }
}