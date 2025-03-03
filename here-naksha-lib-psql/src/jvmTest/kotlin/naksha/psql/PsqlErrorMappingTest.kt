package naksha.psql

import naksha.model.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.request.ErrorResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.psql.base.PgTestBase
import naksha.psql.util.ProxyFeatureGenerator.generateRandomFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PsqlErrorMappingTest : PgTestBase(NakshaCollection("error_mapping_c")) {

    @Test
    fun shouldReturnMissingCollectionError() {
        // Given
        val writeFeatureToMissingCollection = WriteRequest().add(
            Write().createFeature(env.mapId, "missing_collection", generateRandomFeature())
        )

        // When
        val resp = env.storage.newWriteSession().use { session ->
            session.execute(writeFeatureToMissingCollection)
        }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.COLLECTION_NOT_FOUND, resp.error.code)
    }

    @Test
    fun shouldReturnConflictingCollectionError() {
        // Given
        val createAlreadyExistingCollection = WriteRequest().add(
            Write().createCollection(NakshaCollection(collection.id))
        )

        // When
        val resp = env.storage.newWriteSession().use { session ->
            session.execute(createAlreadyExistingCollection)
        }

        // Then
        assertIs<ErrorResponse>(resp)
        assertTrue(resp.error.isConflict())
        assertEquals(NakshaError.MAP_EXISTS, resp.error.code)
    }

    @Test
    fun shouldReturnConflictOnExistingFeature() {
        // Given
        val feature = generateRandomFeature()
        insertFeature(feature)

        // And
        val writeFeatureWithConflictingId = WriteRequest().add(
            Write().createFeature(collection, generateRandomFeature().withId(feature.id))
        )

        // When
        val resp = env.storage.newWriteSession().use { session ->
            session.execute(writeFeatureWithConflictingId)
        }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.CONFLICT, resp.error.code)
    }
}