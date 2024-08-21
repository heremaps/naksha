package naksha.psql

import naksha.base.Int64
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_DICTIONARIES
import naksha.model.Naksha.NakshaCompanion.VIRT_TRANSACTIONS
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadCollections
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import kotlin.test.*

/**
 * We add all tests into a single file, because ordering of tests is not supported, and we do not want to create a new schema and initialize the database for every single test. I understand that in theory, each test should be independent, but if we do this, tests will become so slow, that it takes hours to run them all eventually, and this is worse than the alternative of having tests being strongly dependent on each other. Specifically, this makes writing more tests faster, because we can reuse test code and create multiple things in a row, testing multiple things at ones, and not need to always set up everything gain. As said, it is true that this way of testing is suboptimal from testing perspective, but it is a lot faster in writing the tests, and quicker at runtime, and it is more important to have fast tests, and spend only a minimal amount of time creating them, than to have the perfect tests. This is not a nuclear plant!
 */
class TestPsql {
    // This will create a docker, drop maybe existing schema, and initialize the storage.
    private val env = TestEnv(dropSchema = true, initStorage = true, enableInfoLogs = true)

    private fun isLockReleased(collectionId: String): Boolean {
        val lock = PgUtil.lockId(collectionId).toLong()
        env.pgSession.usePgConnection().execute(
            "select count(*) as count from pg_locks where locktype='advisory' and ((classid::bigint << 32) | objid::bigint) = $lock;"
        ).fetch().use {
            return (it.column("count") as Int64).toInt() == 0
        }
    }

    @Test
    fun ensure_that_essentials_exist() {
        val schema = env.storage.defaultMap
        assertTrue(schema.exists(), "The default schema should exists!")

        val naksha_collections = schema[VIRT_COLLECTIONS]
        assertTrue(naksha_collections.exists(), "$VIRT_COLLECTIONS should exist!")
        val naksha_dictionaries = schema[VIRT_DICTIONARIES]
        assertTrue(naksha_dictionaries.exists(), "$VIRT_DICTIONARIES should exist!")
        val naksha_transactions = schema[VIRT_TRANSACTIONS]
        assertTrue(naksha_transactions.exists(), "$VIRT_TRANSACTIONS should exist!")
    }

    @Test
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
    fun create_collection_and_insert_feature() {
        val col = NakshaCollection("test_${randomString().lowercase()}")
        val writeRequest = WriteRequest()
        writeRequest.writes += Write().createCollection(null, col)
        var session = env.storage.newWriteSession()
        session.use {
            val response = session.execute(writeRequest)
            assertIs<SuccessResponse>(response)
            session.commit()
        }
        // insert feature to collection
        val feature = NakshaFeature()
        feature.id = "feature1"
        val writeFeaturesReq = WriteRequest()
        val writeFeature = Write()
        writeFeaturesReq.add(writeFeature)
        writeFeature.createFeature(null, col.id, feature)
        session = env.storage.newWriteSession()
        session.use {
            val response = session.execute(writeFeaturesReq)
            assertIs<SuccessResponse>(response)
            session.commit()
        }
    }
}