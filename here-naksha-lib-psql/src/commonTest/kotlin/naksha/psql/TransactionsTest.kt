package naksha.psql

import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.*
import naksha.psql.base.PgTestBase
import kotlin.test.*

class TransactionsTest : PgTestBase(NakshaCollection("transaction_test")) {

    @Test
    fun readTransactionInfo() {
        // given - saved feature in one transaction
        val feature = NakshaFeature("f1")
        val writeOp = Write().createFeature(collection, feature)
        val writeRequest = WriteRequest().add(writeOp)
        val writeResponse = executeWrite(writeRequest)

        // clear tuple cache
        Naksha.cache.clear(storage)

        // This should load the feature tuple from the storage, as we cleared the cache.
        val writtenFeatures = writeResponse.useFeaturesOnly()
        assertNotNull(writeResponse.features)
        assertEquals(1, writtenFeatures.size)
        val writtenFeature = writtenFeatures.first()
        assertNotNull(writtenFeature)
        val writtenVersion = writtenFeature.properties.xyz.version
        assertNotNull(writtenVersion)
        assertEquals("f1", writtenFeature.id)

        // when - read all transactions
        val readRequest = ReadTransactions()
        readRequest.readVersion(writtenVersion)
        val readResponse = storage.newReadSession().execute(readRequest) as SuccessResponse
        val readFeatures = readResponse.features
        assertNotNull(readFeatures)
        assertEquals(1, readFeatures.size)
        val readFeature = readFeatures.first()
        assertNotNull(readFeature)

        // then
        assertEquals(writtenVersion, readFeature.properties.xyz.version)
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