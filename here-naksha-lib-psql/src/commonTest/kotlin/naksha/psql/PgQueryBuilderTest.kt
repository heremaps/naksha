package naksha.psql

import naksha.geo.PointCoord
import naksha.geo.SpGeometry
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.RequestQuery
import naksha.model.request.query.*
import naksha.model.request.query.StringOp.QStringOpCompanion.EQUALS
import naksha.psql.base.PgTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class PgQueryBuilderTest : PgTestBase() {

    private val session = storage.newReadSession() as PgSession

    @Test
    fun testReadNoConditions() {
        // given
        val req = ReadFeatures().apply { collectionIds += "foo" }

        // when

        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(0, query.argValues.size)
        assertEquals(
            """
            SELECT gzip(bytea_agg(tuple_number)) AS rs FROM (SELECT tuple_number FROM (
            	(SELECT tuple_number, id FROM foo)
            ) ORDER BY id, tuple_number) LIMIT 1000000;
        """.trimIndent(), query.sql.trimIndent()
        )
    }

    @Test
    fun testReadMultipleCollections() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo1"
            collectionIds += "foo2"
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(0, query.argValues.size)
        assertEquals(
            """
            SELECT gzip(bytea_agg(tuple_number)) AS rs FROM (SELECT tuple_number FROM (
            	(SELECT tuple_number, id FROM foo1) UNION ALL
            	(SELECT tuple_number, id FROM foo2)
            ) ORDER BY id, tuple_number) LIMIT 1000000;
        """.trimIndent(), query.sql.trimIndent()
        )
    }

    @Test
    fun testReadById() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            featureIds += "f1"
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(1, query.argValues.size)
        assertEquals("f1", (query.argValues[0] as Array<String>)[0])
        assertEquals(
            """(SELECT tuple_number, id FROM foo WHERE id = ANY($1))""",
            removeLimitWrapper(query.sql)
        )
    }

    @Test
    fun testReadWithOr() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            featureIds += "f1"
            featureIds += "f2"
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(1, query.argValues.size)
        assertTrue(arrayOf("f1", "f2") contentEquals (query.argValues[0] as Array<String>))
        assertEquals(
            """(SELECT tuple_number, id FROM foo WHERE id = ANY($1))""",
            removeLimitWrapper(query.sql)
        )
    }

    // TODO FIXME uncomment me once property read is ready (CASL-473).
    //    @Test
    fun testReadWithAnd() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            query = RequestQuery().apply {
                properties = POr(
                    PQuery(Property(MetaColumn.ID), EQUALS, "f1"),
                    PAnd(
                        PQuery(Property(MetaColumn.ID), EQUALS, "f2"),
                        PQuery(Property(MetaColumn.UID), DoubleOp.LT, 2.0)
                    )
                )
            }
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(0, query.argValues.size)
        assertEquals(
            """((SELECT tuple_number, id FROM foo WHERE (id=$1 OR (id=$2 AND uid<$3)))""",
            removeLimitWrapper(query.sql)
        )
    }

    @Test
    fun testReadHistory() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            queryHistory = true
        }

        // when
        val query = PgQueryBuilder(session, req).build()


        // then
        assertEquals(
            """
            (SELECT tuple_number, id FROM foo) UNION ALL
            (SELECT tuple_number, id FROM "foo${'$'}hst")
        """.trimIndent(), removeLimitWrapper(query.sql)
        )
    }

    @Test
    fun testReadWithHistoryAndDel() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            featureIds += "f1"
            queryHistory = true
            queryDeleted = true
        }

        // when
        val query = PgQueryBuilder(session, req).build()


        // then
        assertEquals(
            """
            (SELECT tuple_number, id FROM foo WHERE id = ANY(${'$'}1)) UNION ALL
            (SELECT tuple_number, id FROM "foo${'$'}del" WHERE id = ANY($1)) UNION ALL
            (SELECT tuple_number, id FROM "foo${'$'}hst" WHERE id = ANY($1))
        """.trimIndent(), removeLimitWrapper(query.sql)
        )
    }


    @Test
    fun testReadBySpatial() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            query = RequestQuery().apply {
                spatial = SpIntersects(SpGeometry(PointCoord(1.0, 1.0, 1.0)))
            }
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(
            """(SELECT tuple_number, id FROM foo WHERE  (ST_Intersects(naksha_geometry(geo, flags), naksha_geometry($1, 0))))""",
            removeLimitWrapper(query.sql)
        )
    }

    @Test
    fun testReadBySpatialWithBuffer() {
        // given
        val geometryTransformation = SpBuffer(22.2, geography = true)
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            query = RequestQuery().apply {
                spatial = SpIntersects(SpGeometry(PointCoord(1.0, 1.0, 1.0)), geometryTransformation)
            }
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(
            """(SELECT tuple_number, id FROM foo WHERE  (ST_Intersects(naksha_geometry(geo, flags), ST_Buffer(naksha_geometry($1, 0)::geography, $2))))""",
            removeLimitWrapper(query.sql)
        )
    }

    @Test
    fun testReadAllCollections() {
        // given
        val req = ReadCollections()

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(0, query.argValues.size)
        // TODO: We need to adjust this query!
        //assertEquals("(SELECT tuple_number, id FROM \"$VIRT_COLLECTIONS\")", removeLimitWrapper(query.sql))
    }

    @Test
    fun testTagsQuery() {
        // given
        val req = ReadFeatures().apply {
            collectionIds += "foo"
            query = RequestQuery().apply {
                tags = TagExists("stg")
            }
        }

        // when
        val query = PgQueryBuilder(session, req).build()

        // then
        assertEquals(1, query.argValues.size)
        assertEquals(
            """(SELECT tuple_number, id FROM foo WHERE  (naksha_tags(tags, flags) ?? $1))""",
            removeLimitWrapper(query.sql)
        )
    }


    private fun removeLimitWrapper(sql: String) =
        sql.replace("SELECT gzip(bytea_agg(tuple_number)) AS rs FROM (SELECT tuple_number FROM (\n", "")
            .replace("\n) ORDER BY id, tuple_number) LIMIT 1000000;", "")
            .trimIndent()
}
