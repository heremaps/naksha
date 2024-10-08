package naksha.psql.executors.query

import naksha.geo.SpBoundingBox
import naksha.geo.SpPolygon
import naksha.model.request.ReadFeatures
import naksha.model.request.query.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WhereClauseBuilderTest {

    @Test
    fun shouldCreateComposedWhereClause() {
        // Given
        val req = ReadFeatures().apply {
            query.metadata = MetaOr(
                MetaQuery(TupleColumn.createdAt(), DoubleOp.GT, 100),
                MetaAnd(
                    MetaQuery(TupleColumn.appId(), StringOp.EQUALS, "someApp"),
                    MetaQuery(TupleColumn.author(), StringOp.STARTS_WITH, "someAuthor")
                ),
                MetaNot(MetaQuery(TupleColumn.type(), StringOp.EQUALS, "notTheType"))
            )
            query.spatial = SpAnd(
                SpNot(SpIntersects(SpPolygon(SpBoundingBox(1.0, 2.0, 3.0, 4.0)))),
                SpOr(
                    SpIntersects(SpPolygon(SpBoundingBox(11.0, 12.0, 13.0, 14.0))),
                    SpIntersects(SpPolygon(SpBoundingBox(21.0, 22.0, 23.0, 24.0)))
                )
            )
        }
        // When
        val query = WhereClauseBuilder(req).build()

        // Then
        assertNotNull(query)
        assertLegalSqlSameAs("""
        WHERE ( 
                (
                 created_at > $1 
                 OR (app_id = $2 AND starts_with(author, $3)) 
                 OR NOT (type = $4)
                )
            )
        AND ( 
                (
                 NOT (ST_Intersects(naksha_geometry(geo, flags), ST_GeomFromTWKB($5))) 
                 AND (
                        ST_Intersects(naksha_geometry(geo, flags), ST_GeomFromTWKB($6)) 
                        OR ST_Intersects(naksha_geometry(geo, flags), ST_GeomFromTWKB($7))
                     ) 
                ) 
            )  
        """.trimIndent(),
            query.sql
        )
        assertNotNull(query)
    }

    private fun assertLegalSqlSameAs(expected: String, actual: String){
        assertEquals(noWhiteSpace(expected), noWhiteSpace(actual))
        listOf("AND", "OR", "NOT").forEach { special ->
            val splitted = actual.split(special)
            splitted.take(splitted.size - 1).forEach { assertEquals(' ', it.last()) }
        }
    }

    private fun noWhiteSpace(text: String): String {
        return text.replace("\n", " ").replace(" ", "")
    }
}