package naksha.psql

import naksha.base.Action
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class UpsertFeatureTest : PgTestBase() {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
        // Given: Initial state of feature
        val initialFeature = NakshaFeature().apply {
            id = "feature_1"
        }
        val writeInitialFeature = WriteRequest().add(
            Write().upsertFeature(collection, initialFeature)
        )

        val upsertFeaturesReq = WriteRequest().add(
            Write().upsertFeature(collection, initialFeature)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        executeWrite(upsertFeaturesReq)

        // And: Retrieving feature by id
        val retrievedFeatures = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += initialFeature.id
            queryHistory = true
        }).features.sortedBy { it!!.properties.xyz.version!!.number.toLong() }

        // Then
        assertThatFeature(retrievedFeatures[0]!!)
            .isIdenticalTo(
                other = initialFeature,
                ignoreProps = true // we ignore properties because we want to examine them later
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasFeatureType(initialFeature.properties.featureType)
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("action", Action.CREATE.text)
                            .hasProperty("changeCount", 1)
                    }
            }

        assertThatFeature(retrievedFeatures[1]!!)
            .isIdenticalTo(
                other = initialFeature,
                ignoreProps = true // we ignore properties because we want to examine them later
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasFeatureType(initialFeature.properties.featureType)
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("action", Action.UPDATE.text)
                            .hasProperty("changeCount", 2)
                    }
            }
    }

    @Test
    fun shouldUpsertMultipleFeatures() {
        // Given: Initial state of features
        val initialFeatures = (0..3).map { ind ->
            NakshaFeature().apply {
                id = "feature_${ind}_1"
                title = "Initial title $ind"
            }
        }

        // When: first upsert is done
        val writeInitialFeature = WriteRequest()
        initialFeatures.forEach { writeInitialFeature.add(Write().upsertFeature(collection, it)) }
        executeWrite(writeInitialFeature)

        // And: upsert changed features
        val changedFeatures = initialFeatures.mapIndexed { index, nakshaFeature ->
            nakshaFeature.apply {
                title = "Changed title $index"
            }
        }
        val upsertChangedFeatures = WriteRequest()
        changedFeatures.forEach { upsertChangedFeatures.add(Write().upsertFeature(collection, it)) }
        val response = executeWrite(upsertChangedFeatures)

        // Then
        assertIs<SuccessResponse>(response)
        // TODO: only the first one is updated
        response.features.forEach { feature ->
            assertNotNull(feature)
            assertEquals(Action.UPDATE, feature.properties.xyz.action)
        }
    }
}