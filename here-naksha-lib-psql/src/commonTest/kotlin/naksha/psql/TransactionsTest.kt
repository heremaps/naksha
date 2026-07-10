package naksha.psql

import naksha.model.Naksha
import naksha.model.Version
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaTx
import naksha.model.request.*
import naksha.psql.PgTest.PgTest_C.TEST_MAP_ID
import kotlin.test.*

class TransactionsTest : PgTestBase() {

    @Test
    fun readTransactionInfo() {
        // given - saved feature in one transaction
        val feature = NakshaFeature("f1")
        val writeOp = Write().createFeature(collection, feature)
        val writeFeatureRequest = WriteRequest().add(writeOp)
        val writeFeatureResponse = executeWrite(writeFeatureRequest)

        // clear tuple cache
        Naksha.cache.clear(storage)

        // This should load the tuple from the storage, as we cleared the cache.
        val writtenFeatures = writeFeatureResponse.features
        assertNotNull(writeFeatureResponse.features)
        assertEquals(1, writtenFeatures.size)
        val writtenFeature = assertNotNull(writtenFeatures.first())
        val writtenVersion = assertNotNull(writtenFeature.properties.xyz.version)
        assertEquals("f1", writtenFeature.id)

        // Transactions are keyed by the VERSION-sentinel form of the version.
        val txVersion = Version(Version.asVersion(writtenVersion.number))
        val readTxRequest = ReadTransactions().readVersion(txVersion)
        val readTxResponse = executeRead(readTxRequest)
        val transactions = assertNotNull(readTxResponse.features)
        assertEquals(1, transactions.size)
        val transaction = assertNotNull(transactions.first()).proxy(NakshaTx::class)
        assertEquals(txVersion.toString(), transaction.id)
        assertEquals(writtenVersion, transaction.properties.xyz.version)
    }

    @Test
    fun updateTrasactionInfoOnMultipleWrites() {
        // given
        val feature1 = NakshaFeature("f2")
        val writeRequest1 = WriteRequest().apply { add(Write().createFeature(collection, feature1)) }

        val feature2 = NakshaFeature("f3")
        val writeRequest2 = WriteRequest().apply { add(Write().createFeature(collection, feature2)) }

        val writeSession = newWriteSession()

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

        val writeSession = newWriteSession()

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