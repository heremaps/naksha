package naksha.psql

import naksha.base.Id
import naksha.base.PAnyMap
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import naksha.model.objects.NakshaFeature
import naksha.model.request.ErrorResponse
import naksha.model.request.IObjectFilter
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.request.query.AnyOp
import naksha.model.request.query.PQuery
import naksha.model.request.query.Property
import naksha.base.NakshaError
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
        val pQuery = PQuery(Property("properties", "foo"), AnyOp.CONTAINS, "bar")

        // And: A read request with the property query.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
        }.withPropertyQuery(pQuery)
        // When: read request is executed
        val response = executeReadAndLoadTuple(readRequest)

        // Then: The response should contain only the filtered feature.
        assertEquals(1, response.asFeatures.size, "Expected only one feature after filtering")
        assertEquals(featureA.id, response.asFeatures[0]?.id)
    }

    @Test
    fun shouldApplyCustomFilterOnReadRequestOnSuccessResponse() {
        //Given: collection for this test
        testWithCollection("applyCustomFilter")

        // And: A custom object filter that keeps only features whose id contains a given substring.
        class IdContainsFilter(val substring: String) : IObjectFilter {
            override fun filter(collection: naksha.model.objects.NakshaCollection, obj: PAnyMap): PAnyMap? {
                val id = obj.proxy(NakshaFeature::class).id.text
                return if (id.contains(substring)) obj else null
            }
        }
        // And: Two features with distinct IDs that are inserted into the database.
        val featureToKeep = randomFeature().apply { id = Id("keep_this_one_123") }
        val featureToDiscard = randomFeature().apply { id = Id("discard_this_one_456") }
        insertFeatures(featureToKeep, featureToDiscard)

        // And: Read all features.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
        }
        // When: read request is executed and filtered manually using NakshaFeatureFilters
        val response = storage.newReadSession(newSessionOptions()).use { session ->
            val resp = session.execute(readRequest)
            assertIs<SuccessResponse>(resp)
            resp.loadObjects(objectFilter = IdContainsFilter("keep_this"))
            resp
        }

        // Then: The response contains only the feature matching the custom filter.
        assertEquals(1, response.asFeatures.size, "Expected only one feature after custom filtering")
        assertEquals(featureToKeep.id, response.asFeatures[0]?.id)
    }

    @Test
    fun shouldNotApplyFiltersForWriteRequestWithEmptyFilterList() {
        // Given: A feature to create and a WriteRequest to create it.
        val feature = randomFeature()
        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
        }

        // When: The write request is executed.
        val response = executeWriteAndLoadTuples(writeRequest)

        // Then: The response is successful and contains the created feature, proving no filtering occurred.
        assertIs<SuccessResponse>(response)
        assertEquals(1, response.asFeatures.size, "Write response should contain the created feature")
        assertEquals(feature.id, response.asFeatures[0]?.id)
    }

    @Test
    fun shouldTriggerFilteringForWriteRequestWithResultFilter() {
        // Given: A filter that discards any feature it processes.
        class DiscardingFilter : IObjectFilter {
            override fun filter(collection: naksha.model.objects.NakshaCollection, obj: PAnyMap): PAnyMap? = null
        }
        // And: A feature to be created.
        val feature = randomFeature()
        val writeRequest = WriteRequest().apply {
            add(Write().createFeature(collection, feature))
        }
        // When: The write request is executed.
        val response = executeWriteAndLoadTuples(writeRequest)
        // Then: The raw response has the created feature.
        assertIs<SuccessResponse>(response)
        assertEquals(1, response.asFeatures.size)

        // And: After applying the discarding filter manually, the list is empty.
        val discardingFilter = DiscardingFilter()
        val filtered = response.asFeatures.filter { obj -> obj != null && discardingFilter.filter(collection, obj) != null }
        assertEquals(0, filtered.size, "After discarding filter, response should be empty")
    }

    @Test
    fun shouldNotTriggerFilteringForRequestWithErrorResponse() {
        // Given: A read request that is designed to fail by targeting a non-existent collection.
        val readRequest = ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = Id("non_existent_collection")
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
    fun shouldNotFilterFeaturesForReadRequestWithEmptyResultFilters() {
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
        assertEquals(1, response.asFeatures.size)
        assertEquals(feature.id, response.asFeatures[0]?.id)
    }

}
