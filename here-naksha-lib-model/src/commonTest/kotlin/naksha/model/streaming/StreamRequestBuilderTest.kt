package naksha.model.streaming

import naksha.base.Id
import naksha.base.Version.VersionCompanion.HEAD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class StreamRequestBuilderTest {

    @Test
    fun buildUsesDefaultsAndMandatoryConstructorValues() {
        val databaseId = Id("database")
        val catalogId = Id("catalog")
        val collectionId = Id("collection")

        val request = StreamRequestBuilder(databaseId, catalogId, collectionId).build()

        assertEquals(databaseId, request.databaseId)
        assertEquals(catalogId, request.catalogId)
        assertEquals(collectionId, request.collectionId)
        assertEquals(false, request.sequential)
        assertEquals(HEAD.number, request.version)
        assertTrue(request.queryDeleted)
        assertTrue(request.queryHistory)
        assertEquals(0L, request.minVersion)
        assertFalse(request.ignoreTransactions)
        assertEquals(1000, request.chunkSize)
        assertEquals(5.minutes, request.timeout)
    }

    @Test
    fun stringConstructorConvertsIdentifiers() {
        val request = StreamRequestBuilder("database", "catalog", "collection").build()

        assertEquals(Id("database"), request.databaseId)
        assertEquals(Id("catalog"), request.catalogId)
        assertEquals(Id("collection"), request.collectionId)
    }

    @Test
    fun propertiesAndFluentSettersConfigureBuiltRequest() {
        val builder = StreamRequestBuilder(Id("database"), Id("catalog"), Id("collection"))
        assertSame(builder, builder.withVersion(42))
        assertSame(builder, builder.withQueryDeleted(false))
        assertSame(builder, builder.withMinVersion(7))
        assertSame(builder, builder.withIgnoreTransactions(true))
        assertSame(builder, builder.withTimeout(2.minutes))
        builder.queryHistory = false
        builder.chunkSize = 50

        val request = builder.build()

        assertEquals(42L, request.version)
        assertFalse(request.queryDeleted)
        assertFalse(request.queryHistory)
        assertEquals(7L, request.minVersion)
        assertTrue(request.ignoreTransactions)
        assertEquals(50, request.chunkSize)
        assertEquals(2.minutes, request.timeout)
    }
}
