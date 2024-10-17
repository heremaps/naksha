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
import kotlin.test.*

class UpdateFeatureTest : PgTestBase(NakshaCollection("update_feature_test_c")) {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
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
                            .hasProperty("action", Action.UPDATED)
                            .hasProperty("changeCount", 2)
                    }
            }
    }

    @Test
    fun shouldHaveValidHistoryFeatureAfterUpdate() {
        // Given: Initial state of feature
        val initialFeature = NakshaFeature().apply { id = "feature_2" }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(null, collection!!.id, initialFeature)
        )

        // And: Updated state of feature
        val featureToUpdate = NakshaFeature().apply {
            id = initialFeature.id
            properties = NakshaProperties().apply { put("new_attr", "some_value") }
        }
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(null, collection.id, featureToUpdate)
        )

        // When: Writing initial version of feature
        executeWrite(writeInitialFeature)

        // And: Updating feature
        executeWrite(updateFeaturesReq)

        // And: Retrieving feature by id
        val retrievedTuples = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
        }).tuples

        val retrievedUpdatedTupleResult = retrievedTuples.first { it?.tuple?.meta?.action() == Action.UPDATED }!!
        val retrievedHstCreatedTupleResult = retrievedTuples.first { it?.tuple?.meta?.action() == Action.CREATED }!!

        // Then
        assertNotEquals(retrievedUpdatedTupleResult.tupleNumber.version, retrievedHstCreatedTupleResult.tupleNumber.version)
        val updatedTuple = retrievedUpdatedTupleResult.tuple
        val previousTuple = retrievedHstCreatedTupleResult.tuple
        assertNotNull(updatedTuple)
        assertNotNull(previousTuple)
        assertEquals(updatedTuple.meta?.prevVersion, retrievedHstCreatedTupleResult.tupleNumber.version)
        assertEquals(updatedTuple.meta?.version, retrievedUpdatedTupleResult.tupleNumber.version)
        assertEquals(previousTuple.meta?.version, retrievedHstCreatedTupleResult.tupleNumber.version)
        assertEquals(previousTuple.meta?.nextVersion, retrievedUpdatedTupleResult.tupleNumber.version)
        assertNotEquals(previousTuple.meta?.flags, updatedTuple.meta?.flags)
        assertEquals(1, previousTuple.meta?.changeCount)
        assertEquals(2, updatedTuple.meta?.changeCount)
        assertEquals(previousTuple.geo, updatedTuple.geo)
        assertEquals(previousTuple.tags, updatedTuple.tags)
        assertNotEquals(previousTuple.feature, updatedTuple.feature)
        assertEquals(previousTuple.referencePoint, updatedTuple.referencePoint)
        assertNull(previousTuple.toNakshaFeature().properties["new_attr"])
        assertEquals("some_value", updatedTuple.toNakshaFeature().properties["new_attr"])
        assertEquals(previousTuple.meta?.createdAt, updatedTuple.meta?.createdAt)
        assertNotEquals(updatedTuple.meta?.createdAt, updatedTuple.meta?.updatedAt)
        assertEquals(previousTuple.meta?.updatedAt, previousTuple.meta?.createdAt)
        assertNull(previousTuple.meta?.prevVersion)
        assertEquals(previousTuple.meta?.geoGrid, updatedTuple.meta?.geoGrid)
        assertEquals(0, updatedTuple.meta?.uid)
        assertEquals(0, previousTuple.meta?.uid)
        assertEquals(0, updatedTuple.tupleNumber.uid)
        assertEquals(0, previousTuple.tupleNumber.uid)
        assertNotEquals(previousTuple.meta?.authorTs, updatedTuple.meta?.authorTs)
    }
}