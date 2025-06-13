package naksha.psql

import naksha.model.NakshaError
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.model.request.ErrorResponse
import naksha.model.request.FeatureTuple
import naksha.model.request.ReadFeatures
import naksha.model.request.ResultFilter
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.AnyOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostProcessTest: PgTestBase() {

    @Test
    fun shouldApplyPropertyFilterDuringPostProcessing() {
        testWithCollection("applyPropertyFilter")

        val featureA = randomFeature()
        val featureB = randomFeature()
        featureA.properties["foo"] = "bar"
        featureB.properties["foo"] = "baz"
        insertFeatures(featureA, featureB)

        val pQuery = PQuery(Property( "foo"), AnyOp.CONTAINS, "bar")
        val readRequest = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
        }.withPropertyQuery(pQuery)

        val response = executeRead(readRequest)

        assertEquals(1, response.features.size, "Expected only one feature after filtering")
        assertEquals(featureA.id, response.features[0]?.id)
    }

    @Test
    fun shouldApplyCustomFilterDuringPostProcessing() {
        testWithCollection("applyCustomFilter")

        class IdContainsFilter(val substring: String) : ResultFilter {
            override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
                return if (featureTuple.tuple?.id?.contains(substring) == true) {
                    featureTuple
                } else {
                    null
                }
            }
        }

        val featureToKeep = randomFeature().apply { id = "keep_this_one_123" }
        val featureToDiscard = randomFeature().apply { id = "discard_this_one_456" }
        insertFeatures(featureToKeep, featureToDiscard)

        val readRequest = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            resultFilters.add(IdContainsFilter("keep_this"))
        }
        val response = executeRead(readRequest)

        assertEquals(1, response.features.size, "Expected only one feature after custom filtering")
        assertEquals(featureToKeep.id, response.features[0]?.id)
    }

    @Test
    fun shouldNotTriggerPostProcessingForWriteRequest() {
        val feature = randomFeature()
        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
        }

        val response = executeWrite(writeRequest)

        assertIs<SuccessResponse>(response)
        assertEquals(1, response.features.size, "Write response should contain the created feature")
        assertEquals(feature.id, response.features[0]?.id)
    }

    @Test
    fun shouldNotTriggerPostProcessingForWriteRequestWithResultFilter() {
        class discardingFilter() : ResultFilter {
            override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
                return null
            }
        }

        val feature = randomFeature()

        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
            resultFilters.add(discardingFilter())
        }

        val response = executeWrite(writeRequest)

        assertIs<SuccessResponse>(response)
        assertEquals(1, response.features.size, "Write response should contain the created feature")
        assertEquals(feature.id, response.features[0]?.id)
    }



    @Test
    fun shouldNotTriggerPostProcessingForRequestWithErrorResponse() {
        val readRequest = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += "non_existent_collection"
        }

        val response = storage.newReadSession(newSessionOptions()).use {
            it.execute(readRequest)
        }

        assertIs<ErrorResponse>(response)
        assertEquals(NakshaError.COLLECTION_NOT_FOUND, response.error.code)
    }

    @Test
    fun shouldNotTriggerFilteringForReadRequestWthEmptyResultFilters() {
        val feature = randomFeature()
        insertFeature(feature)

        val readRequest = ReadFeatures().apply {
            mapId = collection.mapId
            collectionIds += collection.id
            featureIds += feature.id
        }

        val response = storage.newReadSession(newSessionOptions()).use { session ->
            session.execute(readRequest)
        }

        assertIs<SuccessResponse>(response)
        assertEquals(1, response.features.size)
        assertEquals(feature.id, response.features[0]?.id)
    }

}