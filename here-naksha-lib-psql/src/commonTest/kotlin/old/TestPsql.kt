package old

import naksha.base.*
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import naksha.model.objects.NakshaCollection
import naksha.model.request.*
import naksha.psql.PgUtil
import naksha.psql.base.PgTestBase
import kotlin.test.*

/**
 * We add all tests into a single file, because ordering of tests is not supported, and we do not want to create a new schema and initialize the database for every single test. I understand that in theory, each test should be independent, but if we do this, tests will become so slow, that it takes hours to run them all eventually, and this is worse than the alternative of having tests being strongly dependent on each other. Specifically, this makes writing more tests faster, because we can reuse test code and create multiple things in a row, testing multiple things at ones, and not need to always set up everything gain. As said, it is true that this way of testing is suboptimal from testing perspective, but it is a lot faster in writing the tests, and quicker at runtime, and it is more important to have fast tests, and spend only a minimal amount of time creating them, than to have the perfect tests. This is not a nuclear plant!
 */
class TestPsql: PgTestBase() {

    private fun isLockReleased(collectionId: String): Boolean {
        val lock = PgUtil.lockId(collectionId).toLong()
        useConnection().execute(
            "select count(*) as count from pg_locks where locktype='advisory' and ((classid::bigint << 32) | objid::bigint) = $lock;"
        ).fetch().use {
            return (it.column("count") as Int64).toInt() == 0
        }
    }


    // @Test TODO: fix dropping
    fun create_collection_and_drop_it() {
        val col = NakshaCollection(randomString())
        val writeRequest = WriteRequest()
        writeRequest.writes += Write().createCollection(null, col)
        var session = env.storage.newWriteSession()
        session.use {
            val response = session.execute(writeRequest)
            assertIs<SuccessResponse>(response)
            session.commit()
        }

        val readRequest = ReadCollections()
        readRequest.collectionIds += col.id
        session = env.storage.newReadSession()
        session.use {
            val response = session.execute(readRequest)
            assertIs<SuccessResponse>(response)
            assertEquals(1, response.resultSize())
            assertEquals(1, response.features.size)
            val feature = response.features[0]
            assertNotNull(feature)
            assertEquals(col.id, feature.id)
        }

        val dropRequest = WriteRequest()
        dropRequest.writes += Write().deleteCollectionById(null, col.id)
        session = env.storage.newWriteSession()
        session.use {
            val response = session.execute(dropRequest)
            assertIs<SuccessResponse>(response)
        }
    }

//    private fun create_collection(id: String, partitions: Int) {
//        val nakCollection = NakshaCollection(id, partitions, autoPurge = false, disableHistory = false)
//        val collectionWriteReq = WriteRequest()
//        collectionWriteReq.add(UpsertFeature(NKC_TABLE, nakCollection))
//        try {
//            val response: Response = env.pgSession.write(collectionWriteReq)
//            assertIs<SuccessResponse>(response, response.toString())
//            val successResponse: SuccessResponse = response
//            val responseRow: ResultRow = successResponse.resultSet.rows()[0]
//            val row: Row = responseRow.row!!
//            assertEquals(id, row.id)
//            assertNotNull(row.meta?.rowId())
//            assertSame(CREATED, responseRow.op)
//            val collection = responseRow.getFeature()?.proxy(NakshaCollection::class)!!
//            assertNotNull(collection)
//            assertEquals(id, row.id)
//            assertFalse(collection.disableHistory)
//            assertEquals(partitions > 0, collection.hasPartitions())
//            assertNotNull(collection.properties)
//            assertSame(ACTION_CREATE, Flags(row.meta!!.flags).action())
//        } finally {
//            env.pgSession.commit()
//        }
//    }



    @Test
    fun read_features_by_geo_intersection(){

    }
}