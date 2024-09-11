package naksha.psql

import naksha.model.Action
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeatures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InsertFeatureTest : PgTestBase(NakshaCollection("insert_feature_test_c")) {

    @Test
    fun shouldInsertFeatures() {
        // Given: features to create
        val featuresToCreate = generateRandomFeatures(count = 10)
        val writeFeaturesReq = WriteRequest().apply {
            featuresToCreate.forEach { featureToCreate ->
                add(Write().createFeature(null, collection!!.id, featureToCreate))
            }
        }

        // When: executing feature write request
        executeWrite(writeFeaturesReq)

        // And: reading all features from collection
        val retrievedFeatures = executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
        }).features

        // Then: we got 10 features
        assertEquals(10, retrievedFeatures.size)

        // And:
        featuresToCreate.forEach { featureToCreate ->
            val retrievedFeature = retrievedFeatures.find { it?.id == featureToCreate.id }
            assertNotNull(retrievedFeature, "Missing feature with id: ${featureToCreate.id}")
            assertThatFeature(retrievedFeature)
                .isIdenticalTo(
                    other = featureToCreate,
                    ignoreProps = true // we ignore properties because Xyz is not defined by client
                )
                .hasPropertiesThat { retrievedProperties ->
                    retrievedProperties
                        .hasFeatureType(featureToCreate.properties.featureType)
                        .hasXyzThat { retrievedXyz ->
                            retrievedXyz
                                .hasProperty("appId", PgTest.TEST_APP_ID)
                                .hasProperty("author", PgTest.TEST_APP_AUTHOR!!)
                                .hasProperty("action", Action.CREATED)
                        }
                }
        }
    }
}