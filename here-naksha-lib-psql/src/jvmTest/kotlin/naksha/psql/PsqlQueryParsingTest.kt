package naksha.psql

import kotlin.test.Test
import kotlin.test.assertEquals

class PsqlQueryParsingTest {

    @Test
    fun `select $1 from $2 where $3 = $4 or $5 = $4`() {
        runTestCase(
            rawQuery = "select $1 from $2 where $3 = $4 or $5 = $4",
            expectedParsedQuery = "select ? from ? where ? = ? or ? = ?",
            expectedDollarToIndices = mapOf(
                1 to listOf(1),
                2 to listOf(2),
                3 to listOf(3),
                4 to listOf(4, 6),
                5 to listOf(5),
            )
        )
    }

    @Test
    fun `select $1 from collection$with$dollars where $2 = ca$h`() {
        runTestCase(
            rawQuery = "select $1 from collection\$with\$dollars where $2 = ca\$h",
            expectedParsedQuery = "select ? from collection\$with\$dollars where ? = ca\$h",
            expectedDollarToIndices = mapOf(
                1 to listOf(1),
                2 to listOf(2)
            )
        )
    }

    @Test
    fun `select stuff from collection where $2 = $1 or $3 = $1 or $4 = $1`() {
        runTestCase(
            rawQuery = "select stuff from collection where $2 = $1 or $3 = $1 or $4 = $1",
            expectedParsedQuery = "select stuff from collection where ? = ? or ? = ? or ? = ?",
            expectedDollarToIndices = mapOf(
                1 to listOf(2, 4, 6),
                2 to listOf(1),
                3 to listOf(3),
                4 to listOf(5)
            )
        )
    }

    private fun runTestCase(
        rawQuery: String,
        expectedParsedQuery: String,
        expectedDollarToIndices: Map<Int, List<Int>>
    ) {
        // When
        val query = PsqlQuery(query = rawQuery, typeNames = emptyArray())

        // Then
        assertEquals(
            expectedParsedQuery,
            query.sql,
            "Failed to parse query '$rawQuery'"
        )

        // And
        assertEquals(
            expectedDollarToIndices,
            query.dollarToIndices,
            "Incorrect dollar indices for query '$rawQuery'"
        )
    }
}