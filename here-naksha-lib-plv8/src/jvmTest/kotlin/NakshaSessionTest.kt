import com.here.naksha.lib.base.InsertFeature
import com.here.naksha.lib.base.InsertRow
import com.here.naksha.lib.base.NakCollection
import com.here.naksha.lib.base.NakErrorResponse
import com.here.naksha.lib.base.Row
import com.here.naksha.lib.base.UpdateRow
import com.here.naksha.lib.base.WriteCollections
import com.here.naksha.lib.base.WriteFeature
import com.here.naksha.lib.base.WriteRow
import com.here.naksha.lib.jbon.asArray
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.put
import com.here.naksha.lib.nak.Flags
import com.here.naksha.lib.plv8.NakshaSession
import com.here.naksha.lib.plv8.PARTITION_COUNT_NONE
import com.here.naksha.lib.plv8.ReqHelper.prepareCollectionReqCreateFromFeature
import com.here.naksha.lib.plv8.ReqHelper.prepareFeatureReqForOperations
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
        session.writeFeatures(prepareFeatureReqForOperations(collectionId, WriteRow(collectionId, Row(id = "feature1"))))

        // then
        assertEquals(1, session.transaction.modifiedFeatureCount)
        assertEquals(1, session.transaction.collectionCounters[collectionId])

        // when executed again in same session
        session.writeFeatures(prepareFeatureReqForOperations(collectionId, WriteRow(collectionId, Row(id = "feature2"))))
        session.writeFeatures(prepareFeatureReqForOperations(otherCollection, WriteRow(otherCollection, Row(id = "feature3"))))

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
        val featureRow = Row(id = "someId")

        val op1 = InsertRow(collectionId, featureRow)
        val op2 = UpdateRow(collectionId, featureRow)

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
        val writeCollection = WriteFeature(collectionId = collectionId, feature = feature, flags = Flags())
        session.writeCollections(WriteCollections(rows = arrayOf(writeCollection)))
    }

    private fun doesTableExist(session: NakshaSession, tableName: String): Boolean {
        val result = asArray(session.sql.execute("""SELECT EXISTS (
            SELECT FROM pg_tables WHERE tablename  = '$tableName'
            );"""))
        return asMap(result[0])["exists"]!!
    }
}