package naksha.psql

import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadLimitTest : PgTestBase(NakshaCollection("read_limit_test")) {

    @AfterTest
    fun cleanUp() {
        dropCollection()
    }

    @Test
    fun shouldUseLimitWhenReturningResults() {
        // Given
        val writeReq = WriteRequest().apply {
            add(Write().createFeature(null, collection!!.id, NakshaFeature("test_feature1")))
            add(Write().createFeature(null, collection.id, NakshaFeature("test_feature2")))
            add(Write().createFeature(null, collection.id, NakshaFeature("test_feature3")))
        }
        executeWrite(writeReq)

        // When
        val readWithLimit = executeRead(ReadFeatures().apply {
            collectionIds += collection!!.id
            limit = 2
        })

        // then
        assertEquals(2, readWithLimit.tuples.size)
    }
}