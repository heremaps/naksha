import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.*
import com.here.naksha.lib.plv8.GEO_TYPE_NULL
import com.here.naksha.lib.plv8.NKC_DISABLE_HISTORY
import com.here.naksha.lib.plv8.NKC_PARTITION
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.function.BooleanSupplier

class NakshaSessionTest : Plv8TestContainer() {

    @Test
    fun testGetBaseCollectionId() {
        // given
        val session = NakshaSession.get()

        // expect
        assertEquals("foo", session.getBaseCollectionId("foo_p7"))
    }

    @Test
    fun testEnsureHistoryPartition() {
        // given
        val session = NakshaSession.get()
        val collectionId = "foo1"
        createCollection(session = session, collectionId = collectionId, partition = true, disableHistory = false)

        // when
        session.ensureHistoryPartition(collectionId, session.txn())

        // then
        assertTrue(session.collectionConfiguration.contains(collectionId))
        val collectionConfig = session.getCollectionConfig(collectionId)
        val isHistoryDisabled: Boolean = collectionConfig[NKC_DISABLE_HISTORY]!!
        assertFalse(isHistoryDisabled)
        val isPartitioningEnabled: Boolean = collectionConfig[NKC_PARTITION]!!
        assertTrue(isPartitioningEnabled)
        val expectedPartitionName = Static.hstPartitionNameForId(collectionId, session.txn())
        assertTrue(doesTableExist(session, expectedPartitionName))

    }

    private fun createCollection(session: NakshaSession, collectionId: String, partition: Boolean = false, disableHistory: Boolean = true) {
        val collectionJson = """{"id":"$collectionId","type":"NakshaCollection","maxAge":3560,"partition":$partition,"pointsOnly":false,"properties":{},"disableHistory":$disableHistory}"""
        val builder = XyzBuilder.create(65536)
        val op = builder.buildXyzOp(XYZ_OP_CREATE, collectionId, null, "vgrid")
        val feature = builder.buildFeatureFromMap(asMap(env.parse(collectionJson)))
        session.writeCollections(arrayOf(op), arrayOf(feature), arrayOf(GEO_TYPE_NULL), arrayOf(null), arrayOf(null))
    }

    private fun doesTableExist(session: NakshaSession, tableName: String): Boolean {
        val result = asArray(session.sql.execute("""SELECT EXISTS (
            SELECT FROM pg_tables WHERE tablename  = '$tableName'
            );"""))
        return asMap(result[0])["exists"]!!
    }
}