package com.here.naksha.lib.auth

import com.here.naksha.lib.auth.action.CreateCollections
import com.here.naksha.lib.auth.action.ReadFeatures
import com.here.naksha.lib.auth.attribute.CollectionAttributes
import com.here.naksha.lib.auth.attribute.FeatureAttributes
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
                        FeatureAttributes()
                            .storageId("storage_1")
                            .collectionTags(listOf("c_tag_1", "c_tag_2")),
                        FeatureAttributes()
                            .appId("this_app"),
                    )
            )
            .withAction(
                CreateCollections()
                    .withAttributes(
                        CollectionAttributes()
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
            nakshaServiceMatrix!!.getActionAttributeMaps(ReadFeatures.NAME)
        assertNotNull(readFeaturesAttributeMaps)
        assertEquals(2, readFeaturesAttributeMaps!!.size)
//        assertEquals("storage_1", readFeaturesAttributeMaps[0]!!.data()!!["storageId"])
//        assertEquals("this_app", readFeaturesAttributeMaps[1]!!.data()["appId"])

        // And
        val createCollectionsAttributeMaps =
            nakshaServiceMatrix.getActionAttributeMaps(CreateCollections.NAME)
        assertNotNull(createCollectionsAttributeMaps)
        assertEquals(1, createCollectionsAttributeMaps!!.size)
//        assertEquals("collection_1", createCollectionsAttributeMaps[0].data()["id"])
    }
}