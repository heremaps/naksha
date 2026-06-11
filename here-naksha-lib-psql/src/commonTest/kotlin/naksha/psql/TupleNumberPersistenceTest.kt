package naksha.psql

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.model.Action
import naksha.model.Naksha
import naksha.model.Naksha.NakshaCompanion.featureNumber
import naksha.model.Naksha.NakshaCompanion.partitionNumber
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.RandomFeatures
import kotlin.test.*

class TupleNumberPersistenceTest : PgTestBase(collection = null, mapId = "") {

    @Test
    fun shouldSaveCorrectTxn() {
        testWithCollection("shouldSaveCorrectTxn")

        // Given
        val feature = RandomFeatures.randomFeature()

        // And:
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)

        // When
        val writeOp = Write().createFeature(collection, feature)
        val response = executeWrite(WriteRequest().add(writeOp))
        val featureTuples = response.featureTupleList
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
        testWithCollection("shouldSaveCorrectStoreNumber")

        // Given
        val feature = RandomFeatures.randomFeature()

        // When
        val writeOp = Write().createFeature(collection, feature)
        val response = executeWrite(WriteRequest().add(writeOp))
        val featureTuples = response.featureTupleList
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
    fun shouldSaveCorrectAction() {
        testWithCollection("shouldSaveCorrectAction")

        // Given
        val features = RandomFeatures.randomFeatures(count = 20)

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
        val featureTuples = response.featureTupleList
        Naksha.cache.load(featureTuples)

        // Then: all created tuples have Action.CREATED encoded in their tuple number
        assertEquals(20, featureTuples.size)
        featureTuples.filterNotNull().forEach { featureTuple ->
            val tuple = featureTuple.tuple
            assertNotNull(tuple)
            assertEquals(featuresById[featureTuple.id]?.id, tuple.getStringMember(naksha.model.objects.StandardMembers.Id))
            assertEquals(Action.CREATED, featureTuple.tupleNumber.action)
        }
    }
}