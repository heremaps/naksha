package naksha.psql

import naksha.base.Id
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadLimitTest : PgTestBase() {

    @Test
    fun shouldUseLimitWhenReturningResults() {
        // Given
        val writeReq = WriteRequest().apply {
            add(Write().createFeature(collection, NakshaFeature(Id("test_feature1"))))
            add(Write().createFeature(collection, NakshaFeature(Id("test_feature2"))))
            add(Write().createFeature(collection, NakshaFeature(Id("test_feature3"))))
        }
        executeWriteAndLoadTuples(writeReq)

        // When
        val readWithLimit = executeReadAndLoadTuple(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionId = collection.id
            limit = 2
        })

        // then
        assertEquals(2, readWithLimit.asFeatures.size)
    }
}