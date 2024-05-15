import com.here.naksha.lib.base.NakCollection
import com.here.naksha.lib.base.NakErrorResponse
import com.here.naksha.lib.jbon.XYZ_OP_CREATE
import com.here.naksha.lib.jbon.XYZ_OP_UPDATE
import com.here.naksha.lib.jbon.XYZ_OP_UPSERT
import com.here.naksha.lib.jbon.XyzBuilder
import com.here.naksha.lib.jbon.asArray
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.newMap
import com.here.naksha.lib.jbon.put
import com.here.naksha.lib.plv8.JvmPlv8Table
import com.here.naksha.lib.plv8.NKC_DISABLE_HISTORY
import com.here.naksha.lib.plv8.NKC_PARTITION_COUNT
import com.here.naksha.lib.plv8.NakshaSession
import com.here.naksha.lib.plv8.PARTITION_COUNT_NONE
import com.here.naksha.lib.plv8.RET_ERR_MSG
import com.here.naksha.lib.plv8.ReqHelper
import com.here.naksha.lib.plv8.ReqHelper.prepareFeatureReq
import com.here.naksha.lib.plv8.ReqHelper.prepareFeatureReqForOperations
import com.here.naksha.lib.plv8.ReqHelper.prepareOperation
import com.here.naksha.lib.plv8.getCollectionPartitionCount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(Plv8TestContainer::class)
class NakshaSessionTest : JbTest() {

    private val collectionId = "foo_common"

    @Test
    fun testGetBaseCollectionId() {
        // given
        val session = NakshaSession.get()

        // expect
        assertEquals("foo", session.getBaseCollectionId("foo\$p7"))
    }

    @Test
    fun testEnsureHistoryPartition() {
        // given
        val session = NakshaSession.get()
        createCollection(session = session, collectionId = collectionId, partitionCount = 8, disableHistory = false)

        // then
        val collectionConfig = session.getCollectionConfig(collectionId)
        val isHistoryDisabled: Boolean = collectionConfig.isDisableHistory()
        assertFalse(isHistoryDisabled)
        val partitionCount: Int = collectionConfig.getPartitions()
        assertEquals(8, partitionCount)
        val expectedPartitionName = "${collectionId}\$hst_${session.txn().year()}"
        assertTrue(doesTableExist(session, expectedPartitionName))
    }

    @Test
    fun transactionShouldBeUpdatedWhenExecutingWriteFeaturesMultipleTimes() {
        // given
        val session = NakshaSession.get()
        createCollection(session = session, collectionId = collectionId, partitionCount = 8, disableHistory = false)
        val otherCollection = "collection2"
        createCollection(session = session, collectionId = otherCollection, partitionCount = 8, disableHistory = false)
        session.clear()

        // when
        session.writeFeatures(prepareFeatureReq(XYZ_OP_CREATE, collectionId, "feature1"))

        // then
        assertEquals(1, session.transaction.modifiedFeatureCount)
        assertEquals(1, session.transaction.collectionCounters[collectionId])

        // when executed again in same session
        session.writeFeatures(prepareFeatureReq(XYZ_OP_CREATE, collectionId, "feature2"))
        session.writeFeatures(prepareFeatureReq(XYZ_OP_CREATE, otherCollection, "feature3"))

        // then
        assertEquals(3, session.transaction.modifiedFeatureCount)
        assertEquals(2, session.transaction.collectionCounters[collectionId])
        assertEquals(1, session.transaction.collectionCounters[otherCollection])
    }

    @Test
    fun writeFeaturesShouldNotAllowMultipleOperationsOnSameFeature() {
        // given
        val collectionId = "foo"
        val session = NakshaSession.get()
        session.collectionConfiguration.put(collectionId, NakCollection())

        val op1 = prepareOperation(XYZ_OP_CREATE, "someId")
        val op2 = prepareOperation(XYZ_OP_UPDATE, "someId")

        // when

        val result = session.writeFeatures(prepareFeatureReqForOperations(collectionId, op1, op2)) as NakErrorResponse

        // then
        val error: String = result.message
        assertEquals("Cannot perform multiple operations on single feature in one transaction", error)
    }

    private fun createCollection(session: NakshaSession, collectionId: String, partitionCount: Int = PARTITION_COUNT_NONE, disableHistory: Boolean = true) {
        val feature = NakCollection()
        feature.setId(collectionId)
        feature.setPartitions(partitionCount)
        feature.setDisableHistory(disableHistory)
        session.writeCollections(ReqHelper.prepareCollectionReq(XYZ_OP_UPSERT, collectionId, collectionFeature = feature))
    }

    private fun doesTableExist(session: NakshaSession, tableName: String): Boolean {
        val result = asArray(session.sql.execute("""SELECT EXISTS (
            SELECT FROM pg_tables WHERE tablename  = '$tableName'
            );"""))
        return asMap(result[0])["exists"]!!
    }
}