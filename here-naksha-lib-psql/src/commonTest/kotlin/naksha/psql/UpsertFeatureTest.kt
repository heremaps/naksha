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

class UpsertFeatureTest : PgTestBase(NakshaCollection("upsert_feature_test_c")) {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
        // Given: Initial state of feature
        val initialFeature = NakshaFeature().apply {
            id = "feature_1"
        }
        val writeInitialFeature = WriteRequest().add(
            Write().upsertFeature(null, collection!!.id, initialFeature)
        )

        val upsertFeaturesReq = WriteRequest().add(
            Write().upsertFeature(null, collection.id, initialFeature)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        executeWrite(upsertFeaturesReq)

        // And: Retrieving feature by id
        val retrievedFeatures = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
        }).features.sortedBy { it!!.properties.xyz.version.toLong() }

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
                            .hasProperty("action", Action.CREATED)
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
                            .hasProperty("action", Action.UPDATED)
                            .hasProperty("changeCount", 2)
                    }
            }
    }
}