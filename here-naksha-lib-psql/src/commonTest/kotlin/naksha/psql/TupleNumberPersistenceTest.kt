package naksha.psql

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.Action
import naksha.model.Tuple
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.objects.NakshaFeature
import naksha.model.objects.StandardMembers
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.RandomFeatures
import kotlin.test.*
import kotlin.time.Clock

class TupleNumberPersistenceTest : PgTestBase(collection = null, catalogId = "") {

    @Test
    fun shouldSaveCorrectTxn() {
        testWithCollection("shouldSaveCorrectTxn")

        // Given
        val feature = RandomFeatures.randomFeature()

        // And:
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)

        // When
        val writeOp = Write().createFeature(collection, feature)
        val response = executeWriteAndLoadTuples(WriteRequest().add(writeOp))
        val rs = assertNotNull(response.resultSet)

        // Then:
        assertEquals(1, rs.size)
        val tupleNumber = rs.getTupleNumber(0)

        // And: featureNumber matches
        assertEquals(feature.id.number, tupleNumber.featureNumber)

        // And: version stores date information
        val version = Version(tupleNumber.version)
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
        val response = executeWriteAndLoadTuples(WriteRequest().add(writeOp))
        val rs = assertNotNull(response.resultSet)

        // Then: we persisted single tuple correctly
        assertEquals(1, rs.size)
        val tupleNumber = rs.getTupleNumber(0)

        // Load tuple to verify its contents
        val tuples = arrayOfNulls<Tuple>(1)
        Naksha.cache.load(tuples, arrayOf(tupleNumber))
        assertNotNull(tuples[0])

        // And: `storeNumber` checks out
        storage.adminConnection().use { conn ->
            val pgMap = storage.adminCatalog.getPgCatalogByNumber(conn, collection.catalogId.intValue)
            require(pgMap != null) { "Missing map ${collection.catalogId}" }
            val pgCollection = pgMap.getPgCollectionByNumber(conn, collection.id.intValue)
            require(pgCollection != null) { "Missing collection ${collection.id}" }
            assertEquals(storage.defaultDatabaseId.number, tupleNumber.databaseNumber)
            assertEquals(pgMap.id.number.toInt(), tupleNumber.catalogNumber)
            assertEquals(pgCollection.id.number.toInt(), tupleNumber.collectionNumber)
            assertEquals(feature.id.number, tupleNumber.featureNumber)
            assertEquals(feature.id.partitionNumber, tupleNumber.partitionNumber)
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
            featuresById[feature.id.text] = feature
        }
        val response = executeWriteAndLoadTuples(writeRequest)
        val rs = assertNotNull(response.resultSet)

        // Load all tuples from result set
        val count = rs.size
        assertEquals(20, count)
        val tupleNumbers = Array(count) { rs.getTupleNumber(it) }
        val tuples = arrayOfNulls<Tuple>(count)
        Naksha.cache.load(tuples, tupleNumbers)

        // Then: all created tuples have Action.CREATED encoded in their tuple number
        tuples.forEachIndexed { i, tuple ->
            assertNotNull(tuple)
            val tn = tuple.tupleNumber
            assertNotNull(tn)
            assertEquals(Action.CREATE, tn.action)
            val featureIdText = tuple.getString(StandardMembers.IdMember)
            assertNotNull(featureIdText)
            assertNotNull(featuresById[featureIdText])
        }
    }
}
