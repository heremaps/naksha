package naksha.psql

import naksha.base.*
import naksha.base.PlatformUtil.PlatformUtilCompanion.randomString
import naksha.model.IReadSession
import naksha.model.IWriteSession
import naksha.model.Naksha.NakshaCompanion.VIRT_COLLECTIONS
import naksha.model.Naksha.NakshaCompanion.VIRT_DICTIONARIES
import naksha.model.Naksha.NakshaCompanion.VIRT_TRANSACTIONS
import naksha.model.XyzNs
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaProperties
import naksha.model.request.*
import naksha.psql.PgTest.PgTest_C.TEST_APP_ID
import naksha.psql.util.CommonProxyComparisons
import naksha.psql.util.CommonProxyComparisons.assertAnyObjectsEqual
import naksha.psql.util.ProxyBuilder
import naksha.psql.util.ProxyBuilder.make
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

    //    @Test
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
        // Given: Create collection request
        val col = NakshaCollection("test_${randomString().lowercase()}")
        val writeCollectionRequest = WriteRequest()
        writeCollectionRequest.writes += Write().createCollection(null, col)

        // And: create feature in collection request
        val feature = NakshaFeature().apply {
            id = "feature_1"
            properties = NakshaProperties().apply {
                featureType = "some_feature_type"
            }
        }
        val writeFeaturesReq = WriteRequest().add(
            Write().createFeature(null, col.id, feature)
        )

        // When: executing collection write request
        env.storage.newWriteSession().use { session: IWriteSession ->
            val response = session.execute(writeCollectionRequest)
            assertIs<SuccessResponse>(response)
            session.commit()
        }

        // And: executing feature write request
        env.storage.newWriteSession().use { session: IWriteSession ->
            val response = session.execute(writeFeaturesReq)
            assertIs<SuccessResponse>(response)
            session.commit()
        }

        // Then: feature is retrievable from the collection
        val retrievedFeature = env.storage.newReadSession().use { session: IReadSession ->
            val response = session.execute(
                ReadFeatures().apply {
                    collectionIds += col.id
                    featureIds += feature.id
                }
            )
            assertIs<SuccessResponse>(response)
            val features = response.features
            assertEquals(1, features.size)
            features[0]!!
        }

        // And:
        assertEquals(feature.id, retrievedFeature.id)
        assertEquals(feature.properties?.featureType, retrievedFeature.properties?.featureType)
        // TODO (Jakub): ignore more granurarly and switch to the line below, instead of the check above
//        assertAnyObjectsEqual(feature, retrievedFeature, ignorePaths = setOf(NakshaProperties.XYZ_KEY))
    }

}