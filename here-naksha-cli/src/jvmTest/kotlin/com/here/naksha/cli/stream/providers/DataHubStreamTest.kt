package com.here.naksha.cli.stream.providers

import com.here.naksha.cli.stream.StorageProviders
import com.here.naksha.cli.stream.SyncService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.model.IStreamSession
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.streaming.Stream
import naksha.model.streaming.StreamChunk
import naksha.model.streaming.StreamException
import naksha.model.streaming.StreamRequest
import naksha.model.streaming.StreamRequestBuilder
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataHubStreamTest {
    private val hub = FakeDataHub()
    private val options = SessionOptions.from(NakshaContext.currentContext().withAppId("datahub-test"))

    @AfterTest
    fun stop() = hub.close()

    private fun session(pageSize: Int = 10, token: String? = FakeDataHub.TOKEN, maxRetries: Int = 2): IStreamSession {
        val tokenJson = if (token != null) ""","token":"$token"""" else ""
        val config = """{"url":"${hub.url}","pageSize":$pageSize,"maxRetries":$maxRetries$tokenJson}"""
        return StorageProviders.load().get("datahub").getStorage(config).newStreamSession(options)
    }

    private fun request(space: String, chunkSize: Int = 4): StreamRequest =
        StreamRequestBuilder("datahub", "default", space).withChunkSize(chunkSize).withQueryHistory(false).build()

    private fun drain(stream: Stream, ack: Boolean = true): List<String> {
        val ids = ArrayList<String>()
        while (stream.hasNext()) {
            val chunk = stream.next()
            chunk.tuples.forEach { ids.add(it.id.toString()) }
            if (ack) ackAll(chunk)
        }
        return ids
    }

    private fun ackAll(chunk: StreamChunk) {
        chunk.setTargets(1)
        chunk.acknowledge()
    }

    @Test
    fun readsAllPagesAsChunks() {
        hub.space("s1", 25, globalVersioning = true)
        session().read(request("s1")).use { stream ->
            assertEquals(25L, stream.estimatedTuples)
            val sizes = ArrayList<Int>()
            val ids = ArrayList<String>()
            var version = -2L
            while (stream.hasNext()) {
                val chunk = stream.next()
                sizes.add(chunk.tuples.size)
                chunk.tuples.forEach { ids.add(it.id.toString()); version = it.version }
                ackAll(chunk)
            }
            assertEquals(listOf(4, 4, 2, 4, 4, 2, 4, 1), sizes)
            assertEquals(hub.ids("s1"), ids)
            assertEquals(7L, version)
            assertEquals(25L, stream.acknowledgedTuples)
            assertEquals(true, stream.getRecoveryRequest().getRaw(DataHubStream.END))
        }
        assertEquals(listOf("limit=10", "limit=10&handle=f0010", "limit=10&handle=f0020"), hub.iterateQueries())
    }

    @Test
    fun keepsTheFeatureUntouched() {
        hub.space("s1", 1)
        session().read(request("s1")).use { stream ->
            val chunk = stream.next()
            val feature = chunk.tuples[0].feature as Map<*, *>
            assertEquals("Point", (feature["geometry"] as Map<*, *>)["type"])
            val props = feature["properties"] as Map<*, *>
            assertEquals("name-f0001", props["name"])
            assertEquals("s1", (props[DataHubStream.XYZ_NS] as Map<*, *>)["space"])
            assertEquals(-1L, chunk.tuples[0].version)
            ackAll(chunk)
            assertFalse(stream.hasNext())
        }
    }

    @Test
    fun recoversFromOldestUnacknowledgedPage() {
        hub.space("s1", 25)
        val stream = session().read(request("s1", chunkSize = 5))
        val c1 = stream.next()
        val c2 = stream.next()
        val c3 = stream.next()
        ackAll(c1)
        ackAll(c2)
        assertEquals("f0011", c3.tuples[0].id.toString())
        val recovery = stream.getRecoveryRequest()
        assertEquals("f0010", recovery.getRaw(DataHubStream.HANDLE))

        val rest = session().read(recovery)
        val ids = drain(rest)
        assertEquals(hub.ids("s1").drop(10), ids)
        val end = rest.getRecoveryRequest()
        rest.close()

        session().read(end).use { assertFalse(it.hasNext()) }
    }

    @Test
    fun unacknowledgedFirstPageRestartsFromTheBeginning() {
        hub.space("s1", 12)
        val stream = session().read(request("s1", chunkSize = 5))
        stream.next()
        val recovery = stream.getRecoveryRequest()
        assertNull(recovery.getRaw(DataHubStream.HANDLE))
        assertNull(recovery.getRaw(DataHubStream.END))
    }

    @Test
    fun halvesThePageSizeWhenTheResponseIsTooLarge() {
        hub.space("s1", 9)
        hub.maxLimit = 3
        val ids = session(pageSize = 10).read(request("s1")).use { drain(it) }
        assertEquals(hub.ids("s1"), ids)
        assertEquals(listOf("limit=10", "limit=5", "limit=2", "limit=2&handle=f0002"), hub.iterateQueries().take(4))
    }

    @Test
    fun retriesTransientErrors() {
        hub.space("s1", 3)
        hub.failures.addAll(listOf(503, 429))
        val ids = session().read(request("s1")).use { drain(it) }
        assertEquals(hub.ids("s1"), ids)
    }

    @Test
    fun failsWithRecoveryRequestWhenRetriesAreExhausted() {
        hub.space("s1", 25)
        val stream = session(maxRetries = 1).read(request("s1", chunkSize = 10))
        ackAll(stream.next())
        hub.failures.addAll(listOf(503, 503))
        val e = assertFailsWith<StreamException> { drain(stream) }
        assertEquals(NakshaError.BAD_GATEWAY, e.error.code)
        assertEquals("f0010", e.recoveryRequest.getRaw(DataHubStream.HANDLE))
        assertTrue(stream.isClosed())
    }

    @Test
    fun reportsUnknownSpace() {
        val e = assertFailsWith<NakshaException> { session().read(request("missing")) }
        assertEquals(NakshaError.COLLECTION_NOT_FOUND, e.error.code)
    }

    @Test
    fun reportsWrongToken() {
        hub.space("s1", 1)
        val e = assertFailsWith<NakshaException> { session(token = "wrong").read(request("s1")) }
        assertEquals(NakshaError.UNAUTHORIZED, e.error.code)
    }

    @Test
    fun rejectsVersionsOnSpacesWithoutGlobalVersioning() {
        hub.space("s1", 1)
        val r = StreamRequestBuilder("datahub", "default", "s1").withVersion(3).withQueryHistory(false).build()
        val e = assertFailsWith<NakshaException> { session().read(r) }
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, e.error.code)
    }

    @Test
    fun syncsIntoTheNullTarget() {
        hub.space("s1", 57)
        val target = StorageProviders.load().get("null").getStorage("{}").newStreamSession(options)
        val result = SyncService(1000, {}, {}).sync(session().read(request("s1", chunkSize = 8)), listOf(target))
        assertEquals(57L, assertIs<SyncService.Done>(result).tuples())
    }
}

/** A minimal stand-in for the DataHub 1.24 REST API: `/spaces/{id}`, `/statistics` and plain `/iterate` ordered by id. */
class FakeDataHub : AutoCloseable {
    companion object {
        const val TOKEN = "test-token"
    }

    private class Space(val ids: List<String>, val globalVersioning: Boolean)

    private val spaces = HashMap<String, Space>()
    private val queries = CopyOnWriteArrayList<String>()

    /** Statuses returned by the next `/iterate` calls before answering normally. */
    val failures: MutableList<Int> = CopyOnWriteArrayList()

    /** Larger `limit` values are answered with 513. */
    @Volatile
    var maxLimit = Int.MAX_VALUE

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/hub") { handle(it) }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}/hub"

    fun space(id: String, count: Int, globalVersioning: Boolean = false) {
        spaces[id] = Space((1..count).map { "f%04d".format(it) }, globalVersioning)
    }

    fun ids(space: String): List<String> = spaces.getValue(space).ids

    fun iterateQueries(): List<String> = queries.toList()

    override fun close() = server.stop(0)

    private fun handle(ex: HttpExchange) {
        ex.use {
            if (ex.requestHeaders.getFirst("Authorization") != "Bearer $TOKEN") {
                return respond(ex, 401, """{"type":"ErrorResponse","error":"Unauthorized","errorMessage":"Bad token"}""")
            }
            val parts = ex.requestURI.path.removePrefix("/hub/spaces/").split("/")
            val space = spaces[parts[0]] ?: return respond(ex, 404, """{"type":"ErrorResponse","error":"NotFound","errorMessage":"The requested resource does not exist."}""")
            when (parts.getOrNull(1)) {
                null -> respond(ex, 200, """{"id":"${parts[0]}","title":"t","enableHistory":${space.globalVersioning},"enableGlobalVersioning":${space.globalVersioning}}""")
                "statistics" -> respond(ex, 200, """{"type":"StatisticsResponse","count":{"value":${space.ids.size},"estimated":true}}""")
                "iterate" -> iterate(ex, parts[0], space)
                else -> respond(ex, 404, "{}")
            }
        }
    }

    private fun iterate(ex: HttpExchange, spaceId: String, space: Space) {
        val rawQuery = ex.requestURI.rawQuery ?: ""
        queries.add(URLDecoder.decode(rawQuery, Charsets.UTF_8))
        if (failures.isNotEmpty()) {
            ex.responseHeaders.add("Retry-After", "0")
            return respond(ex, failures.removeAt(0), """{"type":"ErrorResponse","error":"Unavailable","errorMessage":"try again"}""")
        }
        val q = rawQuery.split("&").filter { it.isNotEmpty() }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }
        val limit = q.getValue("limit").toInt()
        if (limit > maxLimit) return respond(ex, 513, """{"type":"ErrorResponse","error":"ResponseTooLarge","errorMessage":"too large"}""")
        val after = q["handle"]
        val page = space.ids.filter { after == null || it > after }.take(limit)
        val version = if (space.globalVersioning) ""","version":7""" else ""
        val features = page.joinToString(",") {
            """{"type":"Feature","id":"$it","geometry":{"type":"Point","coordinates":[13.4,52.5]},"properties":{"name":"name-$it","@ns:com:here:xyz":{"space":"$spaceId","createdAt":1,"updatedAt":2,"uuid":"u-$it"$version}}}"""
        }
        val handle = if (page.size == limit) ""","handle":"${page.last()}"""" else ""
        respond(ex, 200, """{"type":"FeatureCollection","features":[$features]$handle}""")
    }

    private fun respond(ex: HttpExchange, status: Int, body: String) {
        var bytes = body.toByteArray()
        if (ex.requestHeaders.getFirst("Accept-Encoding")?.contains("gzip") == true) {
            bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
            ex.responseHeaders.add("Content-Encoding", "gzip")
        }
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
}
