package naksha.psql

import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import naksha.model.Naksha
import naksha.model.NakshaCache
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.ReadFeatures
import naksha.model.request.SuccessResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionsTest : PgTestBase(NakshaCollection("transaction_test")) {

    @Test
    fun readTransactionInfo() {
        // given - saved feature in one transaction
        val feature = NakshaFeature("f1")
        val writeOp = Write().createFeature(map = null, collection!!.id, feature)
        val writeRequest = WriteRequest().add(writeOp)

        val savedTuples = executeWrite(writeRequest).tuples
        // clear tuple cache
        NakshaCache.tupleCache(storage.id).clear()

        val readSession = storage.newReadSession()
        readSession.fetchTuples(savedTuples.asList())
        val savedFeatureVersion = savedTuples[0]?.tuple?.meta?.version

        // when - read all transactions
        val readRequest = ReadFeatures(Naksha.VIRT_TRANSACTIONS)
        readRequest.featureIds.add(savedFeatureVersion?.txn.toString())
        val readResponse = storage.newReadSession().execute(readRequest) as SuccessResponse
        readSession.fetchTuples(readResponse.tuples)

        // then
        assertEquals(savedFeatureVersion, readResponse.tuples[0]?.tuple?.meta?.version)
    }
}