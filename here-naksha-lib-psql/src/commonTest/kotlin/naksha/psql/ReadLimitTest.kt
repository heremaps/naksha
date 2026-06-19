package naksha.psql

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
            add(Write().createFeature(collection, NakshaFeature("test_feature1")))
            add(Write().createFeature(collection, NakshaFeature("test_feature2")))
            add(Write().createFeature(collection, NakshaFeature("test_feature3")))
        }
        executeWrite(writeReq)

        // When
        val readWithLimit = executeRead(ReadFeatures().apply {
            catalogId = collection.catalogId
            collectionIds += collection.id
            limit = 2
        })

        // then
        assertEquals(2, readWithLimit.features.size)
    }
}