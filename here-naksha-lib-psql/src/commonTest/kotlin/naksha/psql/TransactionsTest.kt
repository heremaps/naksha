package naksha.psql

import naksha.model.Naksha
import naksha.model.NakshaCache
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionsTest : PgTestBase(NakshaCollection("transaction_test")) {

    @Test
    fun readTransactionInfo() {
        // given - saved feature in one transaction
        val feature = NakshaFeature("f1")
        val writeOp = Write().createFeature(map = null, collection!!.id, feature)
        val writeRequest = WriteRequest().add(writeOp)

        var savedTuples: ResultTupleList? = null
        storage.newWriteSession().use { session ->
            savedTuples = (session.execute(writeRequest) as SuccessResponse).tuples
            session.commit()
        }
        // clear tuple cache
        NakshaCache.tupleCache(storage.id).clear()

        val readSession = storage.newReadSession()
        readSession.fetchTuples(savedTuples!!.asList())
        val savedFeatureVersion = savedTuples!![0]?.tuple?.meta?.version

        // when - read all transactions
        val readRequest = ReadFeatures(Naksha.VIRT_TRANSACTIONS)
        readRequest.featureIds.add(savedFeatureVersion?.txn.toString())
        val readResponse = storage.newReadSession().execute(readRequest) as SuccessResponse
        readSession.fetchTuples(readResponse.tuples)

        // then
        assertEquals(savedFeatureVersion, readResponse.tuples[0]?.tuple?.meta?.version)

    }
}