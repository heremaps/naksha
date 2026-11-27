package naksha.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NakshaErrorTest {
    @Test
    fun reconstructsExpectedAndActualStatesWithoutMaskingActions() {
        for (names in listOf(
            listOf("1234567890123", "23", "42", "1234567890124"),
            listOf("database", "catalog", "collection", "feature"),
            listOf(Id.ADMIN_CATALOG_TEXT, Id.ADMIN_CATALOG_TEXT, Id.COLLECTIONS_COL_TEXT, "feature")
        )) {
            val error = NakshaError(
                NakshaError.CONFLICT, "Version mismatch", names[3], names[2], names[1], names[0]
            )
            error.expectedVersion = 100L
            error.actualVersion = 102L
            assertEquals(
                TupleNumber(Id(names[0]), Id(names[1]), Id(names[2]), Id(names[3]), 100L),
                error.getExpectedTupleNumber()
            )
            assertEquals(
                TupleNumber(Id(names[0]), Id(names[1]), Id(names[2]), Id(names[3]), 102L),
                error.getActualTupleNumber()
            )
        }
    }

    @Test
    fun missingIdentityOrVersionDoesNotFabricateAState() {
        val error = NakshaError(NakshaError.CONFLICT, "Conflict", "4", "3", "2", "1")
        assertNull(error.expectedVersion)
        assertNull(error.actualVersion)
        assertNull(error.getExpectedTupleNumber())
        assertNull(error.getActualTupleNumber())

        error.expectedVersion = 0L
        error.actualVersion = 1L
        assertEquals(0L, error.getExpectedTupleNumber()?.version)
        assertEquals(1L, error.getActualTupleNumber()?.version)
        for (field in listOf("database", "catalog", "collection", "id")) {
            val value = error[field]
            error[field] = null
            assertNull(error.getExpectedTupleNumber())
            assertNull(error.getActualTupleNumber())
            error[field] = value
        }
        error.expectedVersion = null
        assertNull(error.getExpectedTupleNumber())
        assertEquals(1L, error.getActualTupleNumber()?.version)
    }

    @Test
    fun versionPropertiesUseTheErrorPayloadAndAppearInDiagnostics() {
        val error = NakshaError()
        error.expectedVersion = 100L
        error.actualVersion = 102L
        assertEquals(100L, error.getOr("expectedVersion", -1L))
        assertEquals(102L, error.getOr("actualVersion", -1L))
        assertTrue(error.toString().contains("\\\"expectedVersion\\\":100"))
        assertTrue(error.toString().contains("\\\"actualVersion\\\":102"))
        error.actualVersion = null
        assertNull(error.actualVersion)
        assertTrue(error.toString().contains("\\\"actualVersion\\\":null"))
    }
}
