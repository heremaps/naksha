import com.here.naksha.lib.plv8.JvmPlv8Sql
import naksha.jbon.XyzBuilder
import naksha.model.Flags.DEFAULT_FLAGS
import naksha.model.NakshaCollectionProxy
import naksha.model.NakshaCollectionProxy.Companion.PARTITION_COUNT_NONE
import naksha.model.request.InsertRow
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.request.WriteRow
import naksha.model.response.ErrorResponse
import naksha.model.response.Row
import naksha.model.response.SuccessResponse
import naksha.plv8.DbTest
import naksha.plv8.NKC_TABLE
import naksha.plv8.NakshaSession
import naksha.plv8.Static
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NakshaSessionTest : DbTest() {

    private val collectionId = "foo_common"

    private val session = NakshaSession(
        storage = storage,
        streamId = "stream-id",
        sql = JvmPlv8Sql(connection),
        appName = "unit-tests-app",
        appId = "unit-tests-app-id",
        author = "kotlin",
        schema = SCHEMA
    )

    @Test
    fun testGetBaseCollectionId() {
        // expect
        assertEquals("foo", session.getBaseCollectionId("foo\$p7"))
    }

    @Test
    fun testEnsureHistoryPartition() {
        // given
        createCollection(session = session, collectionId = collectionId, partitionCount = 8, disableHistory = false)

        // then
        val collectionConfig = session.getCollectionConfig(collectionId)
        val isHistoryDisabled: Boolean = collectionConfig.disableHistory
        assertFalse(isHistoryDisabled)
        val partitionCount: Int = collectionConfig.partitions
        assertEquals(8, partitionCount)
        val expectedPartitionName = "${collectionId}\$hst_${session.txn().year()}"
        assertTrue(doesTableExist(session, expectedPartitionName))
    }

    @Test
    fun transactionShouldBeUpdatedWhenExecutingWriteFeaturesMultipleTimes() {
        // given
        createCollection(session = session, collectionId = collectionId, partitionCount = 8, disableHistory = false)
        val otherCollection = "collection2"
        createCollection(session = session, collectionId = otherCollection, partitionCount = 8, disableHistory = false)
        session.clear()

        val op1 = InsertRow(collectionId, Row(storage, DEFAULT_FLAGS, "feature1"))
        val op2 = InsertRow(collectionId, Row(storage, DEFAULT_FLAGS, "feature2"))
        val op3 = InsertRow(otherCollection, Row(storage, DEFAULT_FLAGS, "feature2"))

        // when
        session.write(WriteRequest(ops = arrayOf(op1)))

        // then
        assertEquals(1, session.transaction.featuresModified)
        assertEquals(1, session.transaction.collections[collectionId]?.inserted)

        // when executed again in same session
        session.write(WriteRequest(ops = arrayOf(op2, op3)))

        // then
        assertEquals(3, session.transaction.featuresModified)
        assertEquals(2, session.transaction.collections[collectionId]?.inserted)
        assertEquals(1, session.transaction.collections[otherCollection]?.inserted)
    }

    @Test
    fun writeFeaturesShouldNotAllowMultipleOperationsOnSameFeature() {
        // given
        val collectionId = "foo"
        session.collectionConfiguration.put(collectionId, NakshaCollectionProxy(collectionId, partitions = 1, autoPurge = false, disableHistory = false))

        val op1 = InsertRow(collectionId, Row(storage, DEFAULT_FLAGS, "someId"))
        val op2 = InsertRow(collectionId, Row(storage, DEFAULT_FLAGS, "someId"))

        // when
        val result = session.write(WriteRequest(ops = arrayOf(op1, op2)))

        // then
        assertInstanceOf(ErrorResponse::class.java, result)
        assertEquals("Cannot perform multiple operations on single feature in one transaction", (result as ErrorResponse).reason.message)
    }

    @Test
    fun canTagTransaction() {
        // given
        createCollection(session = session, collectionId = collectionId, partitionCount = 8, disableHistory = false)
        session.clear()
        session.sql.execute("commit")

        val op1 = InsertRow(collectionId, Row(storage, DEFAULT_FLAGS, "feature1"))

        // when
        val result1 = session.write(WriteRequest(ops = arrayOf(op1)))
        // then

        assertInstanceOf(SuccessResponse::class.java, result1) { (result1 as ErrorResponse).reason.message}
        assertEquals(1, session.transaction.featuresModified)
        assertEquals(1, session.transaction.collections[collectionId]?.inserted)
        assertEquals(0, session.transaction.collections[collectionId]?.updated)
        assertEquals(1, (result1 as SuccessResponse).rows[0].row?.meta?.version)

        // when
        val tagBuilder = XyzBuilder()

        tagBuilder.startTags()
        tagBuilder.writeTag("tag1")
        val tagsBytes = tagBuilder.buildTags()

        val op2 = WriteRow(Static.SC_TRANSACTIONS, Row(storage, DEFAULT_FLAGS, session.transaction.id, tags = tagsBytes))

        val result2 = session.write(WriteRequest(ops = arrayOf(op2)))

        // then
        assertInstanceOf(SuccessResponse::class.java, result2) { (result2 as ErrorResponse).reason.message}
        assertEquals(2, session.transaction.featuresModified)
        assertEquals(1, session.transaction.collections[collectionId]?.inserted)
        assertEquals(1, session.transaction.collections[Static.SC_TRANSACTIONS]?.updated)
        val rows = (result2 as SuccessResponse).rows
        assertEquals(4, rows[0].row?.meta?.version)
    }

    private fun createCollection(session: NakshaSession, collectionId: String, partitionCount: Int = PARTITION_COUNT_NONE, disableHistory: Boolean = true) {
        val collection = NakshaCollectionProxy(
            id = collectionId,
            partitions = partitionCount,
            disableHistory = disableHistory,
            autoPurge = false
        )

        val op = WriteFeature(NKC_TABLE, collection)

        session.write(WriteRequest(ops = arrayOf(op)))
        session.sql.execute("commit")
    }

    private fun doesTableExist(session: NakshaSession, tableName: String): Boolean {
        val result = session.sql.rows(session.sql.execute("""SELECT EXISTS (
            SELECT FROM pg_tables WHERE tablename  = '$tableName'
            );"""))
        return result!![0]["exists"] as Boolean
    }
}