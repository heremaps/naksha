package naksha.plv8

import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.plv8.JvmPlv8Plan
import naksha.base.Int64
import naksha.base.JvmMap
import naksha.base.P_JsMap
import naksha.model.*
import naksha.model.request.InsertFeature
import naksha.model.request.InsertRow
import naksha.model.request.WriteRequest
import naksha.model.response.Row
import naksha.model.response.SuccessResponse
import naksha.plv8.write.RowUpdater
import naksha.plv8.write.WriteRequestExecutor
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            id = "foo1"
        )

        val createFeatureRequest = WriteRequest(ops = arrayOf(InsertRow(collectionId = collectionId, row = row)))

        val storageMock = mock<IStorage> {
            on { id() } doReturn "storageId"
        }

        val sqlMock = mock<IPlv8Sql> {
            on { quoteIdent(anyString()) } doReturn ""
            on { prepare(any(), any()) } doReturn mock<JvmPlv8Plan>()
            on { executeBatch(any(), any()) } doReturn intArrayOf(1)
        }

        val sessionMock = mock<NakshaSession> {
            on { getBaseCollectionId(collectionId) } doReturn collectionId
            on { getCollectionConfig(collectionId) } doReturn fooCollectionConfig
            on { storage } doReturn storageMock
            on { txn() } doReturn Txn(Int64(1))
            on { txnTs() } doReturn Int64(2)
            on { rowUpdater } doReturn RowUpdater(it)
            on { appId } doReturn "appId"
            on { sql } doReturn sqlMock
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
    }
}