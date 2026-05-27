package naksha.psql

import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import kotlin.test.*

class UpdateFeatureTest : PgTestBase(collection = null, mapId = "") {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
        testWithCollection("shouldPerformSimpleUpdateAndUpsert")

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
        val readFeature = fetchSingleFeature(initialFeature.id)

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
    fun testFeatureHistoryAfterUpdate() {
        testWithCollection("testFeatureHistoryAfterUpdate")

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
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += initialFeature.id
            queryHistory = true
        })
        assertEquals(2, readResp.length)
        val retrievedTuples = Naksha.cache.load(readResp.featureTupleList).toTupleList()
        assertNotNull(retrievedTuples)
        assertEquals(2, retrievedTuples.size)

        val createdTuple = retrievedTuples.first { it.meta.action() == Action.CREATED }
        val updatedTuple = retrievedTuples.first { it.meta.action() == Action.UPDATED }

        // Then
        assertNotEquals(updatedTuple.tupleNumber.version, createdTuple.tupleNumber.version)
        assertEquals(createdTuple.meta.nextVersion, updatedTuple.tupleNumber.version.txn)
        assertNull(updatedTuple.meta.nextVersion)
        // Both tuples share the collection's feature encoding (flags carries encoding only,
        // action lives in version bits).
        assertEquals(createdTuple.meta.flags, updatedTuple.meta.flags)
        assertEquals(1, createdTuple.meta.changeCount)
        assertEquals(2, updatedTuple.meta.changeCount)
        assertEquals(createdTuple.geo, updatedTuple.geo)
        assertEquals(createdTuple.tags, updatedTuple.tags)
        assertNotEquals(createdTuple.feature, updatedTuple.feature)
        assertEquals(createdTuple.referencePoint, updatedTuple.referencePoint)
        assertNull(createdTuple.toNakshaFeature().properties["new_attr"])
        assertEquals("some_value", updatedTuple.toNakshaFeature().properties["new_attr"])
        assertEquals(createdTuple.meta.createdAt ?: createdTuple.meta.updatedAt, updatedTuple.meta.createdAt)
        assertNotEquals(updatedTuple.meta.createdAt, updatedTuple.meta.updatedAt)
        assertNull(createdTuple.meta.createdAt)
        assertNotNull(createdTuple.meta.updatedAt)
        assertEquals(createdTuple.meta.hereTile, updatedTuple.meta.hereTile)
        assertEquals(Action.UPDATED, updatedTuple.tupleNumber.action)
        assertEquals(Action.CREATED, createdTuple.tupleNumber.action)
        assertNotEquals(createdTuple.meta.authorTs, updatedTuple.meta.authorTs)
    }

    @Test
    fun atomicUpdateNotExistingWithoutUuid() {
        testWithCollection("atomicUpdateNotExistingWithoutUuid")

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
    fun atomicUpdateNotExistingWithFakeUuid() {
        testWithCollection("atomicUpdateNotExistingWithFakeUuid")

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

    @Test
    fun shouldRequireUuidForAtomicUpdate(){
        testWithCollection("shouldRequireUuidForAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = "feature_for_update"
            momType = "type_before"
        }
        val featureCreationResponse = executeWrite(WriteRequest().add(Write().createFeature(collection, initialFeature)))

        // And: desired update - without prev UUID
        val desiredFeature = NakshaFeature().apply {
            id = initialFeature.id
            momType = "type_after"
            properties.xyz.setRaw("uuid", null)
        }
        val update = WriteRequest().add(Write().updateFeature(collection, desiredFeature, atomic = true))

        // When: performing atomic update
        val response = executeWriteErrorResponse(update)

        // Then: request fails due to missing UUID
        assertIs<ErrorResponse>(response)
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, response.error.code)

        // And:
        val persistedFeature = fetchSingleFeature(initialFeature.id)
        assertEquals(initialFeature.momType,persistedFeature.momType)
        assertEquals(featureCreationResponse.features[0]!!.featureNumber, persistedFeature.featureNumber)
    }

    @Test
    fun allowMissingUuidForNonAtomicUpdate(){
        testWithCollection("allowMissingUuidForNonAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = "feature_for_update"
            momType = "type_before"
        }
        executeWrite(WriteRequest().add(Write().createFeature(collection, initialFeature)))

        // And: desired update - without prev UUID
        val desiredFeature = NakshaFeature().apply {
            id = initialFeature.id
            momType = "type_after"
            properties.xyz.setRaw("uuid", null)
        }
        val update = WriteRequest().add(Write().updateFeature(collection, desiredFeature, atomic = false))

        // When: performing non-atomic update
        val updateResponse = executeWrite(update)

        // Then: request succeeds
        assertIs<SuccessResponse>(updateResponse)

        // And:
        val persistedFeature = fetchSingleFeature(initialFeature.id)
        assertEquals(desiredFeature.momType, persistedFeature.momType)
        assertEquals(updateResponse.features[0]!!.featureNumber, persistedFeature.featureNumber)
    }

    @Test
    fun shouldPerformAtomicUpdate(){
        testWithCollection("shouldPerformAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = "feature_for_update"
            momType = "type_before"
        }
        val featureCreationResponse = executeWrite(WriteRequest().add(Write().createFeature(collection, initialFeature)))
        val initialFeatureUuid = featureCreationResponse.features[0]!!.properties.xyz.uuid

        // And: desired update - with prev UUID
        val desiredFeature = NakshaFeature().apply {
            id = initialFeature.id
            momType = "type_after"
            properties.xyz.setRaw("uuid", initialFeatureUuid.toString())
        }
        val update = WriteRequest().add(Write().updateFeature(collection, desiredFeature, atomic = true))

        // When: performing atomic update
        val updateResponse = executeWrite(update)

        // Then: request succeeds
        assertIs<SuccessResponse>(updateResponse)

        // And:
        val persistedFeature = fetchSingleFeature(initialFeature.id)
        assertEquals(desiredFeature.momType, persistedFeature.momType)
        assertEquals(updateResponse.features[0]!!.featureNumber, persistedFeature.featureNumber)
    }

    private fun fetchSingleFeature(id: String): NakshaFeature {
        Naksha.cache.clear()
        val readFeatureResp = executeRead(ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += id
        })
        assertEquals(1, readFeatureResp.length)
        val retrievedFeature = assertNotNull(readFeatureResp.features[0])
        assertEquals(id, retrievedFeature.id)
        return retrievedFeature
    }
}
