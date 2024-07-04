package naksha.auth

import naksha.auth.action.CreateCollections
import naksha.auth.action.ReadCollections
import naksha.auth.action.ReadFeatures
import naksha.auth.attribute.CollectionAttributes
import naksha.auth.attribute.FeatureAttributes
import naksha.auth.attribute.NakshaAttributes.Companion.ID_KEY
import naksha.auth.attribute.NakshaAttributes.Companion.TAGS_KEY
import naksha.base.AnyListProxy
import naksha.base.Platform
import naksha.base.Proxy
import kotlin.test.*

class AccessRightsMatrixTest {

    @Test
    fun shouldReturnUnregisteredService() {
        // Given: ARM without service
        val arm = AccessRightsMatrix()

        // When: getting service that was not in ARM before
        val freshService = arm.useService("some_service")

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

//        // And: fetching this service directly from ARM again
//        val modifiedService = arm.useService("some_service")
//
//        // Then: returned instance contains modifications
//        assertSame(freshService, modifiedService)
//        val attributes = freshService.getResourceAttributesForAction(ReadFeatures.NAME)
//        assertNotNull(attributes)
//        assertEquals(1, attributes.size)
//        assertEquals("feature_1", attributes[0]!![ID_KEY])
//        assertEquals("storage_1", attributes[0]!![STORAGE_ID_KEY])
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
        val retrievedService = arm.useService(serviceName)
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
        val retrievedService = arm.useService(serviceName)

        // Then:
        val createCollectionsAttrs = retrievedService[CreateCollections.NAME]
        assertNotNull(createCollectionsAttrs)
        assertEquals(1, createCollectionsAttrs.size)
        assertEquals("c_id", createCollectionsAttrs[0]!![ID_KEY])
        assertEquals("s_id", createCollectionsAttrs[0]!![CollectionAttributes.STORAGE_ID_KEY])

        val readFeaturesAttrs = retrievedService[ReadFeatures.NAME]
        assertNotNull(readFeaturesAttrs)
        assertEquals(1, readFeaturesAttrs.size)
        val readFeaturesAttrs_0 = readFeaturesAttrs[0]
        assertNotNull(readFeaturesAttrs_0)
        assertEquals("f_id", readFeaturesAttrs_0[ID_KEY])
        val tags = readFeaturesAttrs_0[TAGS_KEY]
        assertNotNull(tags)
        assertIs<AnyListProxy>(tags)
        assertContentEquals( listOf("tag_1", "tag_2"), tags)
    }

    @Test
    fun shouldPersistTypeAfterSerialization() {
        // Given:
        val arm = AccessRightsMatrix()
        arm.useNaksha()
            .withAction(
                ReadFeatures().withAttributes(
                    FeatureAttributes()
                        .id("f_id")
                        .storageId("s_id")
                )
            )
            .withAction(
                ReadCollections().withAttributes(
                    CollectionAttributes()
                        .id("c_id")
                        .tags("t1", "t2"),
                    CollectionAttributes()
                        .id("c_id_2")
                        .tags("t3", "t4")
                )
            )

        // When
        val asJson = Platform.toJSON(arm)

        // And:
        val fromJson = Proxy.box(Platform.fromJSON(asJson), AccessRightsMatrix::class)!!

        // Then
        val nakshaFromJson = fromJson.useNaksha()
        assertIs<ReadFeatures>(nakshaFromJson[ReadFeatures.NAME])
        assertIs<ReadCollections>(nakshaFromJson[ReadCollections.NAME])
    }
}