package naksha.plv8.read

import com.here.naksha.lib.plv8.JvmPlv8Sql
import naksha.model.request.ReadFeatures
import naksha.model.request.ReadFeatures.Companion.readIdsBy
import naksha.model.request.condition.LOp.Companion.and
import naksha.model.request.condition.LOp.Companion.or
import naksha.model.request.condition.POp.Companion.eq
import naksha.model.request.condition.POp.Companion.lt
import naksha.model.request.condition.PRef.ID
import naksha.model.request.condition.PRef.UID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadQueryBuilderTest {

    private val sql = JvmPlv8Sql(null)
    private val builder = ReadQueryBuilder(sql)

    @Test
    fun testReadNoConditions() {
        // given
        val req = ReadFeatures(collectionIds = arrayOf("foo"))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(0, params.size)
        assertEquals(
            """
            SELECT * FROM (
            (SELECT id, type, geo_ref, flags, txn_next, txn, uid, ptxn, puid, action, version, created_at, updated_at, author_ts, author, app_id, geo_grid, tags, geo, feature FROM "foo")
            ) LIMIT 100000
        """.trimIndent(), sql.trimIndent()
        )
    }

    @Test
    fun testReadNoMeta() {
        // given
        val req = ReadFeatures(collectionIds = arrayOf("foo"), noMeta = true)

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
        val req = ReadFeatures(collectionIds = arrayOf("foo"), noMeta = true, noTags = true)

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
        val req = ReadFeatures(collectionIds = arrayOf("foo"), noMeta = true, noTags = true, noFeature = true)

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
        val req = ReadFeatures(
            collectionIds = arrayOf("foo"),
            noMeta = true,
            noTags = true,
            noFeature = true,
            noGeometry = true
        )

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
        val req = ReadFeatures(
            collectionIds = arrayOf("foo1", "foo2"),
            noMeta = true,
            noTags = true,
            noFeature = true,
            noGeometry = true
        )

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
        val req = readIdsBy("foo", eq(ID, "f1"))

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
        val req = readIdsBy("foo", or(eq(ID, "f1"), eq(ID, "f2")))

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(2, params.size)
        Assertions.assertArrayEquals(arrayOf("f1", "f2"), params.toTypedArray())
        assertEquals(
            """(SELECT id, type, geo_ref, flags FROM "foo" WHERE (id=$1 OR id=$2))""",
            removeLimitWrapper(sql)
        )
    }

    @Test
    fun testReadWithAnd() {
        // given
        val req = readIdsBy("foo", or(eq(ID, "f1"), and(eq(ID, "f2"), lt(UID, 1111))))

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
        val req = ReadFeatures(
            collectionIds = arrayOf("foo"),
            noMeta = true,
            noTags = true,
            noFeature = true,
            noGeometry = true,
            queryHistory = true
        )

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
        val req = ReadFeatures(
            collectionIds = arrayOf("foo"),
            noMeta = true,
            noTags = true,
            noFeature = true,
            noGeometry = true,
            queryHistory = true,
            queryDeleted = true,
            op = eq(ID, "X")
        )

        // when
        val (sql, params) = builder.build(req)

        // then
        assertEquals(
            """
            (SELECT id, type, geo_ref, flags FROM "foo" WHERE id=$1)
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo${'$'}del" WHERE id=$1)
            UNION ALL
            (SELECT id, type, geo_ref, flags FROM "foo${'$'}hst" WHERE id=$1)
        """.trimIndent(), removeLimitWrapper(sql)
        )
    }

    private fun removeLimitWrapper(sql: String) =
        sql.replace("SELECT * FROM (\n", "")
            .replace(") LIMIT 100000", "")
            .trimIndent()
}