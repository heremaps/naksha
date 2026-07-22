package naksha.psql

import naksha.base.NakshaError
import naksha.model.objects.NakshaCollection
import naksha.model.request.ErrorResponse
import naksha.model.request.Write
import naksha.model.request.WriteRequest
import naksha.model.RandomFeatures.RandomFeatures_C.randomFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PsqlErrorMappingTest : PgTestBase() {

    @Test
    fun shouldReturnMissingCollectionError() {
        // Given
        val writeFeatureToMissingCollection = WriteRequest().add(
            Write().createFeature(catalog.id, "missing_collection", randomFeature())
        )

        // When
        val resp = newWriteSession().use { session ->
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
            Write().createCollection(NakshaCollection(collection.id, collection.catalogId))
        )

        // When
        val resp = newWriteSession().use { session ->
            session.execute(createAlreadyExistingCollection)
        }

        // Then
        assertIs<ErrorResponse>(resp)
        assertTrue(resp.error.isConflict())
        assertEquals(NakshaError.COLLECTION_EXISTS, resp.error.code)
    }

    @Test
    fun shouldReturnConflictOnExistingFeature() {
        // Given
        val feature = randomFeature()
        insertFeature(feature)

        // And
        val writeFeatureWithConflictingId = WriteRequest().add(
            Write().createFeature(collection, randomFeature(feature.id))
        )

        // When
        val resp = newWriteSession().use { session ->
            session.execute(writeFeatureWithConflictingId)
        }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.CONFLICT, resp.error.code)
    }
}