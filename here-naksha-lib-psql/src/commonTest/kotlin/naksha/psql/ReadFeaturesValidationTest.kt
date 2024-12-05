package naksha.psql

import naksha.base.StringList
import naksha.model.NakshaError
import naksha.model.request.ErrorResponse
import naksha.model.request.ReadFeatures
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReadFeaturesValidationTest: PgTestBase() {

    @Test
    fun shouldNotAllowReadingWithoutCollections(){
        // Given
        val readFeatures = ReadFeatures().apply {
            collectionIds = StringList() // empty
            featureIds += "some_id"
        }

        // When
        val resp = useReadSession { session -> session.execute(readFeatures) }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, resp.error.code)
    }

    @Test
    fun shouldNotAllowReadingNotPositiveVersionCount(){
        // Given
        val readFeatures = ReadFeatures().apply {
            collectionIds += "some_collection"
            featureIds += "some_id"
            versions = 0
        }

        // When
        val resp = useReadSession { session -> session.execute(readFeatures) }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, resp.error.code)
    }

    @Test
    fun shouldNotAllowReadingMultipleVersionsWithHstDisabled(){
        // Given
        val readFeatures = ReadFeatures().apply {
            collectionIds += "some_collection"
            featureIds += "some_id"
            versions = 2
            queryHistory = false
        }

        // When
        val resp = useReadSession { session -> session.execute(readFeatures) }

        // Then
        assertIs<ErrorResponse>(resp)
        assertEquals(NakshaError.ILLEGAL_ARGUMENT, resp.error.code)
    }
}