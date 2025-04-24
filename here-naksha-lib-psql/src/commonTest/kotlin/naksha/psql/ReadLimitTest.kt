package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import naksha.psql.base.PgTestBase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadLimitTest : PgTestBase(NakshaCollection("read_limit_test", TEST_MAP_ID)) {

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
            mapId = collection.mapId
            collectionIds += collection.id
            limit = 2
        })

        // then
        assertEquals(2, readWithLimit.features.size)
    }
}