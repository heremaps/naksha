package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransactionsTest : PgTestBase(NakshaCollection("transaction_test")) {

    @Test
    fun readTransactionInfo() {
        // given - saved feature in one transaction
        val feature = NakshaFeature("f1")
        val writeOp = Write().createFeature(collection, feature)
        val writeRequest = WriteRequest().add(writeOp)

        val savedTuples = executeWrite(writeRequest).tuples
        // clear tuple cache
        Naksha.cache.clear(storage)

        val readSession = storage.newReadSession()
        readSession.loadTuples(savedTuples.asList())
        val savedFeatureVersion = savedTuples[0]?.tuple?.meta?.version

        // when - read all transactions
        val readRequest = ReadTransactions()
        readRequest.featureIds.add(savedFeatureVersion?.txn.toString())
        val readResponse = storage.newReadSession().execute(readRequest) as SuccessResponse
        readSession.loadTuples(readResponse.tuples)

        // then
        assertEquals(savedFeatureVersion, readResponse.tuples[0]?.tuple?.meta?.version)
    }

    @Test
    fun updateTrasactionInfoOnMultipleWrites() {
        // given
        val feature1 = NakshaFeature("f2")
        val writeRequest1 = WriteRequest().apply { add(Write().createFeature(collection, feature1)) }

        val feature2 = NakshaFeature("f3")
        val writeRequest2 = WriteRequest().apply { add(Write().createFeature(collection, feature2)) }

        val writeSession = env.storage.newWriteSession(null)

        // when
        assertIs<SuccessResponse>(writeSession.execute(writeRequest1))

        // then
        assertEquals(1, writeSession.useTransaction().featuresModified)

        // when
        val value = writeSession.execute(writeRequest2)
        assertIs<SuccessResponse>(value)

        // then
        assertEquals(2, writeSession.useTransaction().featuresModified)
    }

    @Test
    fun shouldBeAbleToTagTransaction() {
        // given
        val feature = NakshaFeature("f40")
        val writeRequest = WriteRequest().apply { add(Write().createFeature(collection, feature)) }

        val writeSession = env.storage.newWriteSession(null)

        // when
        writeSession.useTransaction().properties.xyz.tags.addTag("sth", false)
        assertIs<SuccessResponse>(writeSession.execute(writeRequest))
        val transactionId = writeSession.useTransaction().id
        writeSession.commit()

        // then
        val readRequest = ReadTransactions().apply {
            featureIds += transactionId
        }
        val readResponse = storage.newReadSession().execute(readRequest) as SuccessResponse
        assertTrue(readResponse.features[0]!!.properties.xyz.tags.contains("sth"))
    }
}