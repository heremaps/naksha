package naksha.psql.read

import naksha.base.Int64
import naksha.model.*
import naksha.model.request.InsertFeature
import naksha.model.request.ResultRow
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.response.ErrorResponse
import naksha.model.response.ExecutedOp.Companion.CREATED
import naksha.model.response.Response
import naksha.model.response.SuccessResponse
import naksha.psql.NKC_TABLE
import naksha.psql.PgStatic
import naksha.psql.TestBasics
import kotlin.test.*

class DbCollectionTest : TestBasics() {

    protected val collectionId = "plv8feature"

    init {
        init_storage()
    }

    @Test
    fun t010_createCollection() {
        val nakCollection =
            NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
        val collectionWriteReq = WriteRequest()
        collectionWriteReq.add(WriteFeature(NKC_TABLE, nakCollection))
        try {
            val response: Response = pgSession.write(collectionWriteReq)
            assertIs<SuccessResponse>(response)
            val successResponse: SuccessResponse = response
            val responseRow: ResultRow = successResponse.rows[0]
            val row: Row = responseRow.row!!
            assertEquals(collectionId, row.id)
            assertNotNull(row.meta?.getLuid())
            assertSame(CREATED, responseRow.op)
            val collection = responseRow.getFeature()?.proxy(NakshaCollectionProxy::class)!!
            assertNotNull(collection)
            assertEquals(collectionId, row.id)
            assertFalse(collection.disableHistory)
            assertEquals(partition(), collection.hasPartitions())
            assertNotNull(collection.properties)
            assertSame(ACTION_CREATE, Flags(row.meta!!.flags).action())
        } finally {
            pgSession.commit()
        }
    }

    @Test
    fun t011_createExistingCollection() {
        val collectionId = "collection2"
        val nakCollection =
            NakshaCollectionProxy(collectionId, partitionCount(), autoPurge = false, disableHistory = false)
        val collectionWriteReq = WriteRequest()
        collectionWriteReq.add(InsertFeature(NKC_TABLE, nakCollection))
        try {
            pgSession.write(collectionWriteReq)
            val response = pgSession.write(collectionWriteReq)
            assertIs<ErrorResponse>(response)
            val errorResponse: ErrorResponse = response
            assertEquals("NX000", errorResponse.error.code.value)
        } finally {
            pgSession.commit()
        }

        assertTrue(isLockReleased(collectionId))
    }

    private fun isLockReleased(collectionId: String): Boolean {
        val lock = PgStatic.lockId(collectionId).toLong()
        pgSession.usePgConnection().execute(
            "select count(*) as count from pg_locks where locktype = 'advisory' and ((classid::bigint << 32) | objid::bigint) = $lock;"
        ).fetch()
            .use {
                return (it.column("count") as Int64).toInt() == 0
            }
    }

    open fun partitionCount(): Int {
        return if (partition()) 8 else 1
    }

    open fun partition(): Boolean = true
}
