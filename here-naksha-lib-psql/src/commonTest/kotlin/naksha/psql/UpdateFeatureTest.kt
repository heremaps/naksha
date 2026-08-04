package naksha.psql

import naksha.base.Action
import naksha.base.Id
import naksha.base.Int64
import naksha.base.NakshaError
import naksha.model.Tuple
import naksha.base.TupleNumber
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.objects.XyzMembers
import naksha.model.request.*
import naksha.psql.assertions.NakshaFeatureFluidAssertions.Companion.assertThatFeature
import kotlin.test.*

class UpdateFeatureTest : PgTestBase(collection = null, catalogId = "") {

    @Test
    fun shouldPerformSimpleUpdateAndUpsert() {
        testWithCollection("shouldPerformSimpleUpdateAndUpsert")

        // CREATE FEATURE
        val initialFeature = NakshaFeature().apply {
            id = Id("feature_1")
            momType = "some_feature_type"
        }
        val writeFeatureReq = WriteRequest().add(
            Write().createFeature(collection, initialFeature)
        )
        val writeFeatureResp = executeWriteAndLoadTuples(writeFeatureReq)
        assertEquals(1, writeFeatureResp.asFeatures.size)
        val feature = assertNotNull(writeFeatureResp.asFeatures[0])
        assertEquals(initialFeature.id, feature.id)
        assertEquals(initialFeature.type, feature.type)
        assertEquals(1, feature.properties.xyz.changeCount)

        // UPDATE momType
        feature.momType = "new_feature_type"
        assertEquals("new_feature_type", feature.momType)
        assertEquals("new_feature_type", feature.properties.featureType)
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(collection, feature, true)
        )
        val updateFeatureResp = executeWriteAndLoadTuples(updateFeaturesReq)
        assertEquals(1, updateFeatureResp.asFeatures.size)
        val updatedFeature = assertNotNull(updateFeatureResp.asFeatures[0])
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
                            .hasProperty("action", Action.UPDATE.string)
                            .hasProperty("changeCount", 2)
                    }
            }
    }

    @Test
    fun testFeatureHistoryAfterUpdate() {
        testWithCollection("testFeatureHistoryAfterUpdate")

        // CREATE FEATURE
        val initialFeature = NakshaFeature().apply { id = Id("feature_2") }
        val writeInitialFeature = WriteRequest().add(
            Write().createFeature(collection, initialFeature)
        )
        val writeResp = executeWriteAndLoadTuples(writeInitialFeature)
        assertEquals(1, writeResp.asFeatures.size)
        val writtenFeature = assertNotNull(writeResp.asFeatures[0])
        assertEquals(initialFeature.id, writtenFeature.id)

        // UPDATE FEATURE
        val featureToUpdate = writtenFeature.copy<NakshaFeature>(true).apply {
            properties["new_attr"] = "some_value"
        }
        val updateFeaturesReq = WriteRequest().add(
            Write().updateFeature(collection, featureToUpdate, true)
        )
        val updateFeatureResp = executeWriteAndLoadTuples(updateFeaturesReq)
        assertEquals(1, updateFeatureResp.asFeatures.size)
        val updatedFeature = assertNotNull(updateFeatureResp.asFeatures[0])
        assertEquals(initialFeature.id, updatedFeature.id)
        assertEquals(2, updatedFeature.properties.xyz.changeCount)

        // READ FEATURE HISTORY
        Naksha.cache.clear()
        val readResp = executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += initialFeature.id
            queryHistory = true
        })
        assertEquals(2, readResp.length)

        // Load tuples from result-set via cache
        val rs = assertNotNull(readResp.resultSet)
        val count = readResp.length
        val tupleNumbers = Array(count) { rs.getTupleNumber(it) }
        val tuples = arrayOfNulls<Tuple>(count)
        Naksha.cache.load(tuples, tupleNumbers)
        assertNotNull(tuples[0])
        assertNotNull(tuples[1])

        val createdTuple = tuples.filterNotNull().first { Action.fromValue((it.getLong(StandardMembers.VersionMember).toInt() and 3)) == Action.CREATE }
        val updatedTuple = tuples.filterNotNull().first { Action.fromValue((it.getLong(StandardMembers.VersionMember).toInt() and 3)) == Action.UPDATE }

        // Then
        assertNotEquals(updatedTuple.tupleNumber.version, createdTuple.tupleNumber.version)
        assertEquals(createdTuple.getLong(StandardMembers.NextVersionMember), updatedTuple.tupleNumber.version)
        assertNull(updatedTuple.nextTupleNumber)
        assertEquals(1, createdTuple.getInt(XyzMembers.XyzChangeCount))
        assertEquals(2, updatedTuple.getInt(XyzMembers.XyzChangeCount))
        assertEquals(createdTuple.getByteArray(StandardMembers.GeometryMember), updatedTuple.getByteArray(StandardMembers.GeometryMember))
        assertEquals(createdTuple.getString(XyzMembers.XyzTags), updatedTuple.getString(XyzMembers.XyzTags))
        assertNotEquals(createdTuple.featureBytes, updatedTuple.featureBytes)
        assertEquals(createdTuple.getByteArray(XyzMembers.XyzReferencePoint), updatedTuple.getByteArray(XyzMembers.XyzReferencePoint))
        assertNull(createdTuple.decodeFeature(null)?.properties["new_attr"])
        assertEquals("some_value", updatedTuple.decodeFeature(null)?.properties["new_attr"])
        assertEquals(createdTuple.getLong(XyzMembers.XyzCreatedAt)?.let { if (it == Int64(0L)) null else it } ?: createdTuple.getLong(XyzMembers.XyzUpdatedAt), updatedTuple.getLong(XyzMembers.XyzCreatedAt)?.let { if (it == Int64(0L)) null else it })
        assertNotEquals(updatedTuple.getLong(XyzMembers.XyzCreatedAt), updatedTuple.getLong(XyzMembers.XyzUpdatedAt))
        assertNull(createdTuple.getLong(XyzMembers.XyzCreatedAt)?.let { if (it == Int64(0L)) null else it })
        assertNotNull(createdTuple.getLong(XyzMembers.XyzUpdatedAt))
        assertEquals(createdTuple.getInt(XyzMembers.XyzHereTile), updatedTuple.getInt(XyzMembers.XyzHereTile))
        assertEquals(Action.UPDATE, updatedTuple.tupleNumber.action)
        assertEquals(Action.CREATE, createdTuple.tupleNumber.action)
        assertNotEquals(createdTuple.getLong(XyzMembers.XyzAuthorTimestamp), updatedTuple.getLong(XyzMembers.XyzAuthorTimestamp))
    }

    @Test
    fun atomicUpdateNotExistingWithoutUuid() {
        testWithCollection("atomicUpdateNotExistingWithoutUuid")

        val featureId = "feature_not_existing"
        val feature = NakshaFeature().apply { id = Id(featureId) }
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

        val featureId = Id("feature_not_existing")
        val featureVersion = Version.now(1L, Action.CREATE)
        val fakeUUID = TupleNumber(
            storage.defaultDatabaseId.number,
            catalog.id.intValue,
            collection.id.intValue,
            featureId.number,
            featureVersion.number
        )
        val feature = NakshaFeature().apply {
            id = featureId
            properties.xyz.setRaw("uuid", fakeUUID)
        }
        val updateFeatureResponse = executeWriteErrorResponse(
            WriteRequest().add(
                Write().updateFeature(collection, feature, true)
            )
        )
        assertEquals(NakshaError.FEATURE_NOT_FOUND, updateFeatureResponse.error.code)
        assertTrue(updateFeatureResponse.error.msg.contains(featureId.text))
    }

    @Test
    fun shouldRequireUuidForAtomicUpdate(){
        testWithCollection("shouldRequireUuidForAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = Id("feature_for_update")
            momType = "type_before"
        }
        val featureCreationResponse = executeWriteAndLoadTuples(WriteRequest().add(Write().createFeature(collection, initialFeature)))

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
        assertEquals(featureCreationResponse.asFeatures[0]!!.properties.xyz.guid?.tupleNumber?.featureNumber, persistedFeature.properties.xyz.guid?.tupleNumber?.featureNumber)
    }

    @Test
    fun allowMissingUuidForNonAtomicUpdate(){
        testWithCollection("allowMissingUuidForNonAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = Id("feature_for_update")
            momType = "type_before"
        }
        executeWriteAndLoadTuples(WriteRequest().add(Write().createFeature(collection, initialFeature)))

        // And: desired update - without prev UUID
        val desiredFeature = NakshaFeature().apply {
            id = initialFeature.id
            momType = "type_after"
            properties.xyz.setRaw("uuid", null)
        }
        val update = WriteRequest().add(Write().updateFeature(collection, desiredFeature, atomic = false))

        // When: performing non-atomic update
        val updateResponse = executeWriteAndLoadTuples(update)

        // Then: request succeeds
        assertIs<SuccessResponse>(updateResponse)

        // And:
        val persistedFeature = fetchSingleFeature(initialFeature.id)
        assertEquals(desiredFeature.momType, persistedFeature.momType)
        assertEquals(updateResponse.asFeatures[0]!!.properties.xyz.guid?.tupleNumber?.featureNumber, persistedFeature.properties.xyz.guid?.tupleNumber?.featureNumber)
    }

    @Test
    fun shouldPerformAtomicUpdate(){
        testWithCollection("shouldPerformAtomicUpdate")

        // Given: initial feature - persisted
        val initialFeature = NakshaFeature().apply {
            id = Id("feature_for_update")
            momType = "type_before"
        }
        val featureCreationResponse = executeWriteAndLoadTuples(WriteRequest().add(Write().createFeature(collection, initialFeature)))
        val initialFeatureUuid = featureCreationResponse.asFeatures[0]!!.properties.xyz.uuid

        // And: desired update - with prev UUID
        val desiredFeature = NakshaFeature().apply {
            id = initialFeature.id
            momType = "type_after"
            properties.xyz.setRaw("uuid", initialFeatureUuid.toString())
        }
        val update = WriteRequest().add(Write().updateFeature(collection, desiredFeature, atomic = true))

        // When: performing atomic update
        val updateResponse = executeWriteAndLoadTuples(update)

        // Then: request succeeds
        assertIs<SuccessResponse>(updateResponse)

        // And:
        val persistedFeature = fetchSingleFeature(initialFeature.id)
        assertEquals(desiredFeature.momType, persistedFeature.momType)
        assertEquals(updateResponse.asFeatures[0]!!.properties.xyz.guid?.tupleNumber?.featureNumber, persistedFeature.properties.xyz.guid?.tupleNumber?.featureNumber)
    }

    private fun fetchSingleFeature(id: Id): NakshaFeature {
        Naksha.cache.clear()
        val readFeatureResp = executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += id
        })
        assertEquals(1, readFeatureResp.length)
        val retrievedFeature = assertNotNull(readFeatureResp.asFeatures[0])
        assertEquals(id, retrievedFeature.id)
        return retrievedFeature
    }
}
