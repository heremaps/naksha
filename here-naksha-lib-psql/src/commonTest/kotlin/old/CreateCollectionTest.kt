import naksha.base.Int64
import naksha.base.PlatformUtil
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadCollections
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.PgUtil
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

//package old
//
//import naksha.psql.NKC_TABLE
//import naksha.psql.base.PgTestBase
//
//class CreateCollectionTest {
//private fun isLockReleased(collectionId: String): Boolean {
//    val lock = PgUtil.lockId(collectionId).toLong()
//    useConnection().execute(
//        "select count(*) as count from pg_locks where locktype='advisory' and ((classid::bigint << 32) | objid::bigint) = $lock;"
//    ).fetch().use {
//        return (it.column("count") as Int64).toInt() == 0
//    }
//}
//
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

//// @Test TODO: fix dropping
//fun create_collection_and_drop_it() {
//    val col = NakshaCollection(PlatformUtil.randomString())
//    val writeRequest = WriteRequest()
//    writeRequest.writes += Write().createCollection(null, col)
//    var session = env.storage.newWriteSession()
//    session.use {
//        val response = session.execute(writeRequest)
//        assertIs<SuccessResponse>(response)
//        session.commit()
//    }
//
//    val readRequest = ReadCollections()
//    readRequest.collectionIds += col.id
//    session = env.storage.newReadSession()
//    session.use {
//        val response = session.execute(readRequest)
//        assertIs<SuccessResponse>(response)
//        assertEquals(1, response.resultSize())
//        assertEquals(1, response.features.size)
//        val feature = response.features[0]
//        assertNotNull(feature)
//        assertEquals(col.id, feature.id)
//    }
//
//    val dropRequest = WriteRequest()
//    dropRequest.writes += Write().deleteCollectionById(null, col.id)
//    session = env.storage.newWriteSession()
//    session.use {
//        val response = session.execute(dropRequest)
//        assertIs<SuccessResponse>(response)
//    }
//}
//}