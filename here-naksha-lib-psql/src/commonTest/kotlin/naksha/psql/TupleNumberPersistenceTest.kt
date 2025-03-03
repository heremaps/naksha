package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.AtomicInt
import naksha.model.Naksha
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator
import kotlin.test.*

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
        val response = executeWrite(WriteRequest().add(writeOp))
        val featureTuples = response.useFeatureTupleList()
        Naksha.cache.load(featureTuples)

        // Then:
        assertEquals(1, featureTuples.size)
        val persistedTuple = featureTuples[0]
        assertNotNull(persistedTuple)
        assertEquals(feature.id, persistedTuple.id)

        // And: version stores date information
        val version = persistedTuple.tupleNumber.version
        assertEquals(now.year, version.year)
        assertEquals(now.monthNumber, version.month)
        assertEquals(now.dayOfMonth, version.day)
    }

    @Test
    fun shouldSaveCorrectStoreNumber() {
        // Given
        val feature = ProxyFeatureGenerator.generateRandomFeature()

        // When
        val writeOp = Write().createFeature(collection, feature)
        val response = executeWrite(WriteRequest().add(writeOp))
        val featureTuples = response.useFeatureTupleList()
        Naksha.cache.load(featureTuples)

        // Then: we persisted single tuple correctly
        assertEquals(1, featureTuples.size)
        val persistedTuple = featureTuples[0]
        assertNotNull(persistedTuple)
        assertEquals(feature.id, persistedTuple.id)
        val tuple = persistedTuple.tuple
        assertNotNull(tuple)

        // And: `storeNumber` checks out
        storage.adminConnection().use { conn ->
            val pgMap = storage.adminMap.getPgMapById(conn, collection.mapId!!)
            require(pgMap != null) { "Missing map ${collection.mapId}" }
            val pgCollection = pgMap.getPgCollectionById(conn, collection.id)
            require(pgCollection != null) { "Missing collection ${collection.id}" }
            assertEquals(storage.number, persistedTuple.tupleNumber.storageNumber)
            assertEquals(pgMap.number, persistedTuple.tupleNumber.mapNumber)
            assertEquals(pgCollection.number, persistedTuple.tupleNumber.collectionNumber)
            assertEquals(featureNumber(feature.id), persistedTuple.tupleNumber.featureNumber)
            assertEquals(partitionNumber(featureNumber(feature.id)), persistedTuple.tupleNumber.partitionNumber)
        }
    }

    @Test
    fun shouldSaveCorrectUuid() {
        // Given
        val features = ProxyFeatureGenerator.generateRandomFeatures(count = 20)

        // When
        val writeRequest = WriteRequest()
        val featuresById = mutableMapOf<String, NakshaFeature>()
        features.forEach { feature ->
            writeRequest.add(
                Write().createFeature(collection, feature)
            )
            featuresById[feature.id] = feature
        }
        val response = executeWrite(writeRequest)
        val featureTuples = response.useFeatureTupleList()
        Naksha.cache.load(featureTuples)

        // We know that we get UIDs between 0 and 19, but the order is not guaranteed
        assertEquals(20, featureTuples.size)
        val expectedUids = mutableMapOf<Int, Boolean>()
        for (i in 0 until featureTuples.size) {
            expectedUids[i] = true
        }
        // Then: tuples have been correctly persisted, and have UIDs between 0 and 19
        featureTuples.filterNotNull().sortedBy { it.tupleNumber.uid }.forEach { featureTuple ->
            val tuple = featureTuple.tuple
            assertNotNull(tuple)
            val id = featureTuple.id
            assertEquals(id, tuple.meta.id)
            val requested = featuresById[id]
            assertNotNull(requested)
            val inserted = expectedUids.remove(tuple.tupleNumber.uid)
            assertTrue(inserted == true)
        }
        // We expect that every UID is encountered exactly ones!
        assertTrue(expectedUids.isEmpty())
    }
}