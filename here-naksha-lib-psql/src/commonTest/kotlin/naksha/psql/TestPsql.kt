package naksha.psql

import naksha.base.Int64
import naksha.model.*
import naksha.model.request.ResultRow
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.request.ExecutedOp.Companion.CREATED
import naksha.model.request.Response
import naksha.model.request.SuccessResponse
import kotlin.test.*

/**
 * We add all tests into a single file, because ordering of tests is not supported, and we do not want to create a new schema and initialize the database for every single test. I understand that in theory, each test should be independent, but if we do this, tests will become so slow, that it takes hours to run them all eventually, and this is worse than the alternative of having tests being strongly dependent on each other. Specifically, this makes writing more tests faster, because we can reuse test code and create multiple things in a row, testing multiple things at ones, and not need to always set up everything gain. As said, it is true that this way of testing is suboptimal from testing perspective, but it is a lot faster in writing the tests, and quicker at runtime, and it is more important to have fast tests, and spend only a minimal amount of time creating them, than to have the perfect tests. This is not a nuclear plant!
 */
class TestPsql {
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
    fun run_all() {
        val schema = env.storage.defaultSchema()
        assertTrue(schema.exists(), "The default schema should exists!")

        val naksha_collections = schema[PgNakshaCollections.ID]
        assertTrue(naksha_collections.exists(), "${PgNakshaCollections.ID} should exist!")
        val naksha_dictionaries = schema[PgNakshaDictionaries.ID]
        assertTrue(naksha_dictionaries.exists(), "${PgNakshaDictionaries.ID} should exist!")
        val naksha_transactions = schema[PgNakshaTransactions.ID]
        assertTrue(naksha_transactions.exists(), "${PgNakshaTransactions.ID} should exist!")

        // create_collection("test", 0)
    }

    private fun create_collection_low_level(id: String, partitions: Int) {

    }

    private fun drop_collection_low_level(id: String) {

    }

    private fun create_collection(id: String, partitions: Int) {
        val nakCollection = NakshaCollectionProxy(id, partitions, autoPurge = false, disableHistory = false)
        val collectionWriteReq = WriteRequest()
        collectionWriteReq.add(WriteFeature(NKC_TABLE, nakCollection))
        try {
            val response: Response = env.pgSession.write(collectionWriteReq)
            assertIs<SuccessResponse>(response, response.toString())
            val successResponse: SuccessResponse = response
            val responseRow: ResultRow = successResponse.resultSet.rows()[0]
            val row: Row = responseRow.row!!
            assertEquals(id, row.id)
            assertNotNull(row.meta?.getLuid())
            assertSame(CREATED, responseRow.op)
            val collection = responseRow.getFeature()?.proxy(NakshaCollectionProxy::class)!!
            assertNotNull(collection)
            assertEquals(id, row.id)
            assertFalse(collection.disableHistory)
            assertEquals(partitions > 0, collection.hasPartitions())
            assertNotNull(collection.properties)
            assertSame(ACTION_CREATE, Flags(row.meta!!.flags).action())
        } finally {
            env.pgSession.commit()
        }
    }

}