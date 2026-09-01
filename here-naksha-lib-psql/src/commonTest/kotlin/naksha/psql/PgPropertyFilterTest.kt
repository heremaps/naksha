package naksha.psql

import naksha.base.NakshaError
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

class PgPropertyFilterTest: PgTestBase() {

    @Test
    fun shouldApplyPropertyFilterOnReadRequestWithSuccessResponse() {
        //Given: collection for this test
        testWithCollection("applyPropertyFilter")
        // And: Two features with different properties.
        val featureA = randomFeature()
        val featureB = randomFeature()
        featureA.properties["foo"] = "bar"
        featureB.properties["foo"] = "baz"
        insertFeatures(featureA, featureB)
        // And: A PQuery that will match only one feature.
        val pQuery = PQuery(Property( "properties", "foo"), AnyOp.CONTAINS, "bar")

        // And: A read request is created with the property query.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
        }.withPropertyQuery(pQuery)
        // When: read request is executed
        val response = executeRead(readRequest)

        // Then: The response should contain only the filtered feature.
        assertEquals(1, response.features.size, "Expected only one feature after filtering")
        assertEquals(featureA.id, response.features[0]?.id)
    }

    @Test
    fun shouldApplyCustomFilterOnReadRequestOnSuccessResponse() {
        //Given: collection for this test
        testWithCollection("applyCustomFilter")
        // And: A custom filter implementation.
        class IdContainsFilter(val substring: String) : ResultFilter {
            override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
                return if (featureTuple.tuple?.id?.contains(substring) == true) {
                    featureTuple
                } else {
                    null
                }
            }
        }
        // And: Two features with distinct IDs that are inserted into the database.
        val featureToKeep = randomFeature().apply { id = "keep_this_one_123" }
        val featureToDiscard = randomFeature().apply { id = "discard_this_one_456" }
        insertFeatures(featureToKeep, featureToDiscard)

        // And: A read request is made with the custom filter manually added.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            resultFilters.add(IdContainsFilter("keep_this"))
        }
        // When: read request is executed
        val response = executeRead(readRequest)

        // Then: The response contains only the feature matching the custom filter.
        assertEquals(1, response.features.size, "Expected only one feature after custom filtering")
        assertEquals(featureToKeep.id, response.features[0]?.id)
    }

    @Test
    fun shouldNotApplyFiltersForWriteRequestWithEmptyFilterList() {
        // Given: A feature to create and a WriteRequest to create it.
        val feature = randomFeature()
        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
        }

        // When: The write request is executed.
        val response = executeWrite(writeRequest)

        // Then: The response is successful and contains the created feature, proving no filtering occurred.
        assertIs<SuccessResponse>(response)
        assertEquals(1, response.features.size, "Write response should contain the created feature")
        assertEquals(feature.id, response.features[0]?.id)
    }

    @Test
    fun shouldTriggerFilteringForWriteRequestWithResultFilter() {
        // Given: A filter that would discard any feature it processes.
        class discardingFilter() : ResultFilter {
            override fun filter(featureTuple: FeatureTuple): FeatureTuple? {
                return null
            }
        }
        // And: A feature to be created.
        val feature = randomFeature()
        // And: A WriteRequest that has the discarding filter attached.
        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
            resultFilters.add(discardingFilter())
        }
        // When: The write request with the filter is executed.
        val response = executeWrite(writeRequest)

        // Then: The response is successful and still contains the feature, proving the filter was ignored.
        assertIs<SuccessResponse>(response)
        assertEquals(0, response.features.size, "Write response should not contain the created feature")
    }



    @Test
    fun shouldNotTriggerFilteringForRequestWithErrorResponse() {
        // Given: A read request that is designed to fail by targeting a non-existent collection.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = "non_existent_collection"
        }

        // When: The failing request is executed.
        val response = storage.newReadSession(newSessionOptions()).use {
            it.execute(readRequest)
        }

        // Then: The response is an ErrorResponse with the expected error code.
        assertIs<ErrorResponse>(response)
        assertEquals(NakshaError.COLLECTION_NOT_FOUND, response.error.code)
    }

    @Test
    fun shouldNotFilterFeaturesForReadRequestWthEmptyResultFilters() {
        // Given: A single feature inserted into the database.
        val feature = randomFeature()
        insertFeature(feature)

        // When: A read request is made to fetch that specific feature by ID, with no result filters.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            featureIds += feature.id
        }
        val response = storage.newReadSession(newSessionOptions()).use { session ->
            session.execute(readRequest)
        }

        // Then: The response is successful and contains only the requested feature.
        assertIs<SuccessResponse>(response)
        assertEquals(1, response.features.size)
        assertEquals(feature.id, response.features[0]?.id)
    }

}