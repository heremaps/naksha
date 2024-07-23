package naksha.psql

import naksha.geo.GeometryProxy
import naksha.model.request.ReadCollections
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadFeatures.ReadFeaturesCompanion.readIdsOnly
import naksha.model.request.RowOptions
import naksha.model.request.condition.*
import naksha.model.request.condition.Property.PropRefCompanion.id
import naksha.model.request.condition.Property.PropRefCompanion.uid
import naksha.model.request.condition.Property.PropRefCompanion.uuid
import naksha.model.request.condition.QueryNumber.QNumericOpCompanion.LT
import naksha.model.request.condition.QueryOp.QOpCompanion.IS_NOT_NULL
import naksha.model.request.condition.QueryString.QStringOpCompanion.EQUALS
import naksha.psql.read.ReadQueryBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class ReadQueryBuilderTest {

    private val builder = ReadQueryBuilder()

    @Test
    fun testReadNoConditions() {
        // given
        val req = ReadFeatures().addCollectionId("foo")

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """
            SELECT * FROM (
            (SELECT id, type, geo_ref, flags, txn_next, txn, uid, ptxn, puid, version, created_at, updated_at, author_ts, author, app_id, geo_grid, tags, geo, feature FROM "foo")
            ) LIMIT 100000
        """.trimIndent(), sql.trimIndent()
        )
    }

    @Test
    fun testReadNoMeta() {
        // given
        val req = ReadFeatures().addCollectionId("foo").withRowOptions(RowOptions(meta = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags, tags, geo, feature FROM "foo")""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadNoMetaNoTags() {
        // given
        val req = ReadFeatures().addCollectionId("foo").withRowOptions(RowOptions(tags = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags, geo, feature FROM "foo")""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadNoMetaNoTagsNoFeature() {
        // given
        val req = ReadFeatures().addCollectionId("foo").withRowOptions(RowOptions(meta = false, tags = false, feature = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags, geo FROM "foo")""",
            removeLimitWrapper(sql)
        )
    }


    @Test
    fun testReadNoMetaNoTagsNoFeatureNoGeometry() {
        // given
        val req = ReadFeatures()
            .addCollectionId("foo")
            .withRowOptions(RowOptions(meta = false, tags = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo")""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadMultipleCollections() {
        // given
        val req = ReadFeatures()
            .addCollectionId("foo1")
            .addCollectionId("foo2")
            .withRowOptions(RowOptions(meta = false, tags = false, feature = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """
            SELECT * FROM (
            (SELECT id, type, geo_ref, flags FROM "foo1")
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo2")
            ) LIMIT 100000
        """.trimIndent(), sql.trimIndent()
        )
    }

    @Test
    fun testReadById() {
        // given
        val req = readIdsOnly("foo").addId("f1")

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertEquals("f1", params[0])
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE id=$1)""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadWithOr() {
        // given
        val req = readIdsOnly("foo").addIds("f1", "f2")

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(2, params.size)
        assertTrue(arrayOf("f1", "f2") contentEquals params.toTypedArray())
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE (id=$1 OR id=$2))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadWithAnd() {
        // given
        val req = readIdsOnly("foo")
            .withQueryProperties(LOr(Query(id(), EQUALS, "f1"), LAnd(Query(id(), EQUALS, "f2"), Query(uid(), LT, "f1"))))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(3, params.size)
        assertEquals(1111, params[2])
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE (id=$1 OR (id=$2 AND uid<$3)))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadWithHistory() {
        // given
        val req = ReadFeatures()
            .addCollectionId("foo")
            .withQueryHistory()
            .withRowOptions(RowOptions(tags = false, feature = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(
            """
            (SELECT id, type, geo_ref, flags FROM "foo")
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo${'$'}hst")
        """.trimIndent(), removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadWithHistoryAndDel() {
        // given
        val req = ReadFeatures()
            .addCollectionId("foo")
            .withQueryHistory()
            .withQueryDeleted()
            .withQueryProperties(Query(id(), EQUALS, "X"))
            .withRowOptions(RowOptions(meta = false, tags = false, feature = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(3, params.size)
        assertEquals(
            """
            (SELECT id, type, geo_ref, flags FROM "foo" WHERE id=$1)
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo${'$'}del" WHERE id=$2)
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo${'$'}hst" WHERE id=$3)
        """.trimIndent(), removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadByIdIsNotNull() {
        // given
        val req = readIdsOnly("foo").withQueryProperties(Query(id(), IS_NOT_NULL))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE id is not null)""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadBySpatial() {
        // given
        val req = readIdsOnly("foo")
            .withQuerySpatial(SpIntersects(GeometryProxy()))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE ST_Intersects(naksha_geometry(flags,geo), ST_Force3D(naksha_geometry_in_type(3::int2,$1))))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadBySpatialWithBuffer() {
        // given
        val geometryTransformation = SpBuffer(22.2, geography = true)
        val req = readIdsOnly("foo")
            .withQuerySpatial(SpIntersects(GeometryProxy(), geometryTransformation))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE ST_Intersects(naksha_geometry(flags,geo),  ST_Buffer(ST_Force3D(naksha_geometry_in_type(3::int2,$1))::geography ,22.2,E'') ))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadAllCollections() {
        // given
        val req = ReadCollections()

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags, txn_next, txn, uid, ptxn, puid, version, created_at, updated_at, author_ts, author, app_id, geo_grid, tags, geo, feature FROM "naksha~collections")""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadCollectionsById() {
        // given
        val req = ReadCollections()
            .addId("foo")
            .addId("bar")
            .addId("baz")
            .withRowOptions(RowOptions(meta = false, tags = false, feature = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertTrue(arrayOf("foo", "bar", "baz") contentEquals params[0] as Array<String>)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "naksha~collections" WHERE id in $1)""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadDeletedCollections() {
        // given
        val req = ReadCollections()
            .addId("foo")
            .withQueryDeleted()
            .withRowOptions(RowOptions(meta = false, tags = false, feature = false, geometry = false))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(2, params.size)
        assertEquals(
            """
            (SELECT id, type, geo_ref, flags FROM "naksha~collections" WHERE id in $1)
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "naksha~collections${'$'}del" WHERE id in $2)
            """.trimIndent().trimMargin(),
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testUuidQuery() {
        // given
        val uuid = "test_storage:building_delta:feature1:2024:01:23:1:0"

        val req = readIdsOnly("foo")
            .withQueryProperties(Query(uuid(), EQUALS, uuid))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(2, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE (txn=$1 AND uid=$2))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testTagsQuery() {
        // given

        val req = readIdsOnly("foo")
            .withQueryTags(TagExists("tag1"))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE tags_to_jsonb(tags) ? $1)""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testAnyQuery() {
        // given
        val txns = arrayOf("11", "22")
        val req = readIdsBy("foo", POp.POpCompanion.any(TXN, txns))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(1, params.size)
        assertEquals(txns, params[0] as Array<String>)
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE txn=ANY($1))""",
            removeLimitWrapper(sql)
        )

    }

    private fun removeLimitWrapper(sql: String) =
        sql.replace("SELECT * FROM (\n", "")
            .replace(") LIMIT 100000", "")
            .trimIndent()
}
