package naksha.psql

import naksha.model.RandomFeatures.RandomFeatures_C.randomFeatures
import naksha.model.objects.NakshaFeature
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamRequestBuilder
import naksha.model.streaming.StreamTransaction
import naksha.model.streaming.StreamTuple
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PsqlStreamSessionTest : PgTestBase(collection = null) {

    private val created = 20
    private val updated = 5

    private fun prepare(name: String) {
        testWithCollection(name)
        val features = insertFeatures(randomFeatures(created)).features.map { it!! }
        executeWrite(WriteRequest().apply {
            for (f in features.take(updated)) {
                val copy = f.copy<NakshaFeature>(true)
                copy.properties["alias"] = "updated"
                add(Write().updateFeature(collection, copy, true))
            }
        })
        executeWrite(WriteRequest().apply { add(Write().deleteFeatureById(collection, features.last().id)) })
    }

    private fun request(): StreamRequestBuilder = StreamRequestBuilder(storage.id, catalog.id, collection.id).withChunkSize(7)

    private fun readAll(stream: Stream, limit: Int = Int.MAX_VALUE): List<StreamChunk> {
        val chunks = ArrayList<StreamChunk>()
        while (chunks.size < limit && stream.hasNext()) {
            val chunk = stream.next()
            chunk.setTargets(1)
            chunks += chunk
            chunk.acknowledge()
        }
        return chunks
    }

    private fun read(request: StreamRequest): List<StreamChunk> =
        storage.newStreamSession(newSessionOptions()).use { session -> session.read(request).use { readAll(it) } }

    private fun assertPerFeatureOrder(tuples: List<StreamTuple>) {
        val last = HashMap<String, Long>()
        for (t in tuples) {
            val prev = last.put(t.id.text, t.version)
            if (prev != null) assertTrue(prev < t.version, "states of ${t.id} out of order")
        }
    }

    @Test
    fun deliversEveryTransactionOfTheCollectionInOrder() {
        prepare("streamPerTransaction")

        val chunks = read(request().build())

        assertTrue(chunks.all { it is StreamTransaction })
        val versions = chunks.map { (it as StreamTransaction).version }
        assertEquals(versions.sorted(), versions)
        // Creating the collection is a transaction of the collection as well, but without features.
        assertEquals(listOf(0, created, updated, 1), chunks.map { it.tuples.size })
        assertPerFeatureOrder(chunks.flatMap { it.tuples.toList() })
    }

    @Test
    fun withoutDeletedTheDeleteIsSkipped() {
        prepare("streamWithoutDeleted")

        val tuples = read(request().withQueryDeleted(false).build()).flatMap { it.tuples.toList() }

        assertEquals(created + updated, tuples.size)
    }

    @Test
    fun ignoringTransactionsDeliversAllStatesPerFeature() {
        prepare("streamPerFeature")

        val chunks = read(request().withIgnoreTransactions(true).build())

        val tuples = chunks.flatMap { it.tuples.toList() }
        assertEquals(created + updated + 1, tuples.size)
        for (c in chunks) assertEquals(c.tuples.size, c.tuples.map { it.id.text }.toSet().size, "a chunk repeats a feature")
        assertPerFeatureOrder(tuples)
    }

    @Test
    fun continuesFromTheRecoveryRequest() {
        prepare("streamContinue")
        val all = read(request().withIgnoreTransactions(true).build()).flatMap { it.tuples.toList() }

        val recovery = storage.newStreamSession(newSessionOptions()).use { session ->
            val stream = session.read(request().withIgnoreTransactions(true).build())
            readAll(stream, limit = 2)
            stream.closeForRecovery()
        }
        val first = all.take(14)
        val rest = read(recovery).flatMap { it.tuples.toList() }

        val seen = (first + rest).map { "${it.id.text}#${it.version}" }.toSet()
        assertEquals(all.map { "${it.id.text}#${it.version}" }.toSet(), seen, "nothing is missed after continue")
    }

    @Test
    fun configBuilderAsksForTheConnection() {
        val builder = PsqlStorageProvider().newConfigBuilder()
        val asked = ArrayList<String>()
        for (answer in listOf("test", "db-host", "abc", "15432", "naksha", "reader", "secret")) {
            asked += builder.question
            try {
                builder.answer(answer)
            } catch (e: naksha.base.NakshaException) {
                assertTrue(e.message!!.contains("Port must be a number"), e.message)
            }
        }
        assertTrue(builder.isDone)
        assertEquals(listOf("Storage id", "Host", "Port", "Port", "Database", "User", "Password"), asked)
        val config = naksha.model.objects.NakshaStorage.fromJSON(builder.build()).proxy(PgConfig::class)
        assertEquals("test", config.id)
        assertEquals("db-host", config.master.host)
        assertEquals(15432, config.master.port)
        assertEquals("naksha", config.master.db)
        assertEquals("reader", config.master.user)
        assertEquals("secret", config.master.password)
    }
}
