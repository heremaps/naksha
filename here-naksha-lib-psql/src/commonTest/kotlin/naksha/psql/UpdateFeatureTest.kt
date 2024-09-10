package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.base.PgTestBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateFeatureTest : PgTestBase(NakshaCollection("update_feature_test_c")) {

    @Test
    fun shouldPerformSimpleUpdate() {
        // Given: Initial state of feature
        val initialFeature = NakshaFeature().apply {
            id = "feature_1"
            properties = NakshaProperties().apply {
                featureType = "some_feature_type"
            }
        }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(null, collection!!.id, initialFeature)
        )

        // And: Updated state of feature
        val featureToUpdate = NakshaFeature().apply {
            id = initialFeature.id
            properties = NakshaProperties().apply {
                featureType = "new_feature_type"
            }
        }
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(null, collection.id, featureToUpdate)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        // And: Updating feature
        executeWrite(updateFeaturesReq)

        // And: Retrieving feature by id
        val retrievedFeature = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
        }).let { response ->
            val responseFeatures = response.features
            assertEquals(1, responseFeatures.size)
            responseFeatures[0]!!
        }

        // Then
        assertThatFeature(retrievedFeature)
            .isIdenticalTo(
                other = initialFeature,
                ignoreProps = true // we ignore properties because we want to examine them later
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasFeatureType(featureToUpdate.properties.featureType)
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("appId", PgTest.TEST_APP_ID)
                            .hasProperty("author", PgTest.TEST_APP_AUTHOR!!)
                            .hasProperty("action", Action.CREATED)
                    }
            }
    }
}