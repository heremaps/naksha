package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.CreateCollections
import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.XyzCollectionAttributes
import com.here.naksha.lib.auth.attribute.XyzFeatureAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NakshaArmBuilderTest {

    @Test
    fun shouldProduceValidArm() {
        // Given: recipe for Naksha Service ARM
        val nakshaArmBuilder = NakshaArmBuilder()
            .withAction(
                ReadFeatures()
                    .withAttributes(
                        XyzFeatureAttributes()
                            .storageId("storage_1")
                            .collectionTags(listOf("c_tag_1", "c_tag_2")),
                        XyzFeatureAttributes()
                            .appId("this_app"),
                    )
            )
            .withAction(
                CreateCollections()
                    .withAttributes(
                        XyzCollectionAttributes()
                            .id("collection_1")
                    )
            )

        // When: the final ARM is build
        val nakshaArm = nakshaArmBuilder.buildArm()

        // Then: the ARM contains `naksha` service
        val nakshaServiceMatrix = nakshaArm.getService(NakshaArmBuilder.NAKSHA_SERVICE_NAME)
        assertNotNull(nakshaServiceMatrix)

        // And: naksha service contains specified attributes
        val readFeaturesAttributeMaps =
            nakshaServiceMatrix!!.getActionAttributeMaps(ReadFeatures.READ_FEATURES_ACTION_NAME)
        assertNotNull(readFeaturesAttributeMaps)
        assertEquals(2, readFeaturesAttributeMaps!!.size)
//        assertEquals("storage_1", readFeaturesAttributeMaps[0]!!.data()!!["storageId"])
//        assertEquals("this_app", readFeaturesAttributeMaps[1]!!.data()["appId"])

        // And
        val createCollectionsAttributeMaps =
            nakshaServiceMatrix.getActionAttributeMaps(CreateCollections.CREATE_COLLECTIONS_ACTION_NAME)
        assertNotNull(createCollectionsAttributeMaps)
        assertEquals(1, createCollectionsAttributeMaps!!.size)
//        assertEquals("collection_1", createCollectionsAttributeMaps[0].data()["id"])
    }
}