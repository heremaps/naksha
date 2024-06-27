package naksha.plv8

import naksha.jbon.XYZ_OP_CREATE
import naksha.plv8.PsqlPlan
import naksha.base.Int64
import naksha.model.*
import naksha.model.request.InsertRow
import naksha.model.request.WriteRequest
import naksha.model.response.Row
import naksha.model.response.SuccessResponse
import naksha.plv8.write.RowUpdater
import naksha.plv8.write.WriteRequestExecutor
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WriteFeaturesTest {

    private val fooCollectionConfig = NakshaCollectionProxy(
        id = "foo",
        partitions = 1,
        autoPurge = false,
        disableHistory = true
    )

    @Test
    fun testWrite() {
        // given
        val collectionId = "foo"
        val row = Row(
            mock<IStorage>(),
            guid = null,
            flags = Flags.DEFAULT_FLAGS,
            id = "foo1",
            feature = "dummyFeature".encodeToByteArray()
        )

        val createFeatureRequest = WriteRequest(ops = arrayOf(InsertRow(collectionId = collectionId, row = row)))

        val storageMock = mock<PgStorage> {
            on { id() } doReturn "storageId"
        }

        val sqlMock = mock<PgSession> {
            on { PgUtil.quoteIdent(anyString()) } doReturn ""
            on { prepare(any(), any()) } doReturn mock<PsqlPlan>()
            on { executeBatch(any(), any()) } doReturn intArrayOf(1)
        }

        val sessionMock = mock<NakshaSession> {
            on { getBaseCollectionId(collectionId) } doReturn collectionId
            on { getCollectionConfig(collectionId) } doReturn fooCollectionConfig
            on { storage } doReturn storageMock
            on { txn() } doReturn Txn(Int64(1))
            on { txnTs() } doReturn Int64(2)
            on { rowUpdater } doReturn RowUpdater(it)
        }

        val executor = WriteRequestExecutor(sessionMock, false)

        // when
        val result = executor.write(createFeatureRequest)

        // then
        assertInstanceOf(SuccessResponse::class.java, result)
        assertEquals(1, result.rows.size)
        val row0 = result.rows[0].row
        assertEquals(XYZ_EXEC_CREATED, result.rows[0].op)
        assertNotNull(row0)
        assertEquals(row.id, row0.id)
        assertEquals(Flags.DEFAULT_FLAGS, row0.flags)
        assertEquals(0, row0.meta?.uid)
        assertEquals(XYZ_OP_CREATE.toShort(), row0.meta?.action)
        assertEquals(1, row0.meta?.version)
        assertEquals(-1906261745, row0.meta?.fnva1)
    }
}
