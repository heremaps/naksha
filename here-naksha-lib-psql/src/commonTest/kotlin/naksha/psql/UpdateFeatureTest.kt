package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import naksha.psql.base.PgTestBase
import kotlin.test.*

class UpdateFeatureTest : PgTestBase(NakshaCollection("update_feature_test_c")) {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
        // CREATE FEATURE
        val initialFeature = NakshaFeature().apply {
            id = "feature_1"
            featureType = "some_feature_type"
        }
        val writeFeatureReq = WriteRequest().add(
            Write().createFeature(collection, initialFeature)
        )
        val writeFeatureResp = executeWrite(writeFeatureReq)
        assertEquals(1, writeFeatureResp.features.size)
        val feature = assertNotNull(writeFeatureResp.features[0])
        assertEquals(initialFeature.id, feature.id)
        assertEquals(initialFeature.type, feature.type)
        assertEquals(1, feature.properties.xyz.changeCount)

        // UPDATE featureType
        feature.featureType = "new_feature_type"
        assertEquals("new_feature_type", feature.featureType)
        assertEquals("new_feature_type", feature.momType)
        assertEquals("new_feature_type", feature.properties.featureType)
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(collection, feature, true)
        )
        val updateFeatureResp = executeWrite(updateFeaturesReq)
        assertEquals(1, updateFeatureResp.features.size)
        val updatedFeature = assertNotNull(updateFeatureResp.features[0])
        assertEquals(2, updatedFeature.properties.xyz.changeCount)

        // Retrieving feature by id
        Naksha.cache.clear()
        val readFeatureResp = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
        })
        assertEquals(1, readFeatureResp.size)
        val readFeature = assertNotNull(readFeatureResp.features[0])
        assertEquals(initialFeature.id, readFeature.id)
        assertEquals(2, updatedFeature.properties.xyz.changeCount)

        // Then
        assertThatFeature(readFeature)
            .isIdenticalTo(
                other = updatedFeature,
                ignoreProps = true // we ignore properties because we want to examine them later
            )
            .hasPropertiesThat { retrievedProperties ->
                retrievedProperties
                    .hasFeatureType(updatedFeature.properties.featureType)
                    .hasXyzThat { retrievedXyz ->
                        retrievedXyz
                            .hasProperty("appId", PgTest.TEST_APP_ID)
                            .hasProperty("author", PgTest.TEST_APP_AUTHOR)
                            .hasProperty("action", Action.UPDATED.text)
                            .hasProperty("changeCount", 2)
                    }
            }
    }

    @Test
    fun shouldHaveValidHistoryFeatureAfterUpdate() {
        // CREATE FEATURE
        val initialFeature = NakshaFeature().apply { id = "feature_2" }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(collection, initialFeature)
        )
        val writeResp = executeWrite(writeInitialFeature)
        assertEquals(1, writeResp.features.size)
        val writtenFeature = assertNotNull(writeResp.features[0])
        assertEquals(initialFeature.id, writtenFeature.id)

        // UPDATE FEATURE
        val featureToUpdate = writtenFeature.copy<NakshaFeature>(true).apply {
            properties["new_attr"] = "some_value"
        }
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(collection, featureToUpdate, true)
        )
        val updateFeatureResp = executeWrite(updateFeaturesReq)
        assertEquals(1, updateFeatureResp.features.size)
        val updatedFeature = assertNotNull(updateFeatureResp.features[0])
        assertEquals(initialFeature.id, updatedFeature.id)
        assertEquals(2, updatedFeature.properties.xyz.changeCount)

        // READ FEATURE HISTORY
        Naksha.cache.clear()
        val readResp = executeRead(ReadFeatures().apply {
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
        })
        assertEquals(2, readResp.resultSize())
        val retrievedTuples = Naksha.cache.load(readResp.useFeatureTupleList()).toTupleList()
        assertNotNull(retrievedTuples)
        assertEquals(2, retrievedTuples.size)

        val createdTuple = retrievedTuples.first { it.meta.action() == Action.CREATED }
        val updatedTuple = retrievedTuples.first { it.meta.action() == Action.UPDATED }

        // Then
        assertNotEquals(updatedTuple.tupleNumber.version, createdTuple.tupleNumber.version)
        assertNull(createdTuple.meta.prevTupleNumber)
        assertEquals(createdTuple.meta.nextTupleNumber, updatedTuple.tupleNumber)
        assertEquals(updatedTuple.meta.prevTupleNumber, createdTuple.tupleNumber)
        assertNull(updatedTuple.meta.nextTupleNumber)
        assertNotEquals(createdTuple.meta.flags, updatedTuple.meta.flags)
        assertEquals(1, createdTuple.meta.changeCount)
        assertEquals(2, updatedTuple.meta.changeCount)
        assertEquals(createdTuple.geo, updatedTuple.geo)
        assertEquals(createdTuple.tags, updatedTuple.tags)
        assertNotEquals(createdTuple.feature, updatedTuple.feature)
        assertEquals(createdTuple.referencePoint, updatedTuple.referencePoint)
        assertNull(createdTuple.toNakshaFeature().properties["new_attr"])
        assertEquals("some_value", updatedTuple.toNakshaFeature().properties["new_attr"])
        assertEquals(createdTuple.meta.createdAt, updatedTuple.meta.createdAt)
        assertNotEquals(updatedTuple.meta.createdAt, updatedTuple.meta.updatedAt)
        assertEquals(createdTuple.meta.updatedAt, createdTuple.meta.createdAt)
        assertEquals(createdTuple.meta.hereTile, updatedTuple.meta.hereTile)
        assertEquals(0, updatedTuple.meta.uid)
        assertEquals(0, createdTuple.meta.uid)
        assertEquals(0, updatedTuple.tupleNumber.uid)
        assertEquals(0, createdTuple.tupleNumber.uid)
        assertNotEquals(createdTuple.meta.authorTs, updatedTuple.meta.authorTs)
    }

    @Test
    fun atomicUpdateOfNotExistingFeatureWithoutUuid() {
        val featureId = "feature_not_existing"
        val feature = NakshaFeature().apply { id = featureId }
        val updateFeatureResponse = executeWriteErrorResponse(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, updateFeatureResponse.error.code)
    }

    @Test
    fun atomicUpdateOfNotExistingFeatureWithFakeUuid() {
        val featureId = "feature_not_existing"
        val feature = NakshaFeature().apply {
            id = featureId
            properties.xyz.setRaw("uuid", Guid(featureId, TupleNumber.HEAD).toString())
        }
        val updateFeatureResponse = executeWriteErrorResponse(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(NakshaError.FEATURE_NOT_FOUND, updateFeatureResponse.error.code)
        assertTrue(updateFeatureResponse.error.msg.contains(featureId))
    }
}