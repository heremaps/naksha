package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.model.Naksha
import naksha.model.objects.NakshaCollection
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TupleNumberPersistenceTest : PgTestBase(NakshaCollection("tuple_persistence_test")) {


    @AfterTest
    fun cleanup() {
        dropCollection()
    }

    @Test
    fun shouldSaveCorrectTxn() {
        // Given
        val feature = ProxyFeatureGenerator.generateRandomFeature()

        // And:
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        // When
        val writeOp = Write().createFeature(collection, feature)
        val persistedTuples = executeWrite(WriteRequest().add(writeOp)).tuples

        // Then:
        assertEquals(1, persistedTuples.size)
        val persistedTuple = persistedTuples[0]!!
        assertEquals(feature.id, persistedTuple.id())

        // And: version stores date information
        val version = persistedTuple.tupleNumber.version
        assertEquals(now.year, version.year())
        assertEquals(now.monthNumber, version.month())
        assertEquals(now.dayOfMonth, version.day())
    }

    @Test
    fun shouldSaveCorrectStoreNumber() {
        // Given
        val feature = ProxyFeatureGenerator.generateRandomFeature()

        // When
        val writeOp = Write().createFeature(collection, feature)
        val persistedTuples = executeWrite(WriteRequest().add(writeOp)).tuples

        // Then: we persisted single tuple correctly
        assertEquals(1, persistedTuples.size)
        val persistedTuple = persistedTuples[0]!!
        assertEquals(feature.id, persistedTuple.id())

        // And: `storeNumber` checks out
        storage.adminConnection().use { conn ->
            val pgMap = storage.adminMap.getPgMapById(conn, collection.mapId)
            require(pgMap != null) { "Missing map ${collection.mapId}" }
            val pgCollection = storage.adminMap.getPgCollectionById(conn, pgMap, collection.id)
            require(pgCollection != null) { "Missing collection ${collection.id}" }
            assertEquals(storage.number, persistedTuple.tupleNumber.storageNumber)
            assertEquals(pgMap.number, persistedTuple.tupleNumber.mapNumber)
            assertEquals(pgCollection.number, persistedTuple.tupleNumber.collectionNumber)
            assertEquals(Naksha.partitionNumber(feature.id), persistedTuple.tupleNumber.partitionNumber)
        }
    }

    @Test
    fun shouldSaveCorrectUuid() {
        // Given
        val features = ProxyFeatureGenerator.generateRandomFeatures(count = 20)

        // When
        val writeRequest = WriteRequest()
        features.forEach { feature ->
            writeRequest.add(
                Write().createFeature(collection, feature)
            )
        }
        val persistedTuples = executeWrite(writeRequest).tuples

        // Then: tuples have been correctly persisted
        assertEquals(20, persistedTuples.size)
        (0..19).forEach { index ->
            val tuple = persistedTuples[index]!!
            assertEquals(index, tuple.tupleNumber.uid)
            assertEquals(features[index].id, tuple.id())
        }
    }
}