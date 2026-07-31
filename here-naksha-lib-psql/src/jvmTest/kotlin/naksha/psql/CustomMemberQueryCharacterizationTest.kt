package naksha.psql

import java.sql.PreparedStatement
import naksha.base.NakshaException
import naksha.geo.SpPoint
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.request.ReadFeatures
import naksha.model.request.ops.Equals
import naksha.model.request.ops.Intersects
import naksha.model.request.ops.Op
import naksha.model.request.ops.TagEquals
import naksha.model.request.ops.TagMapHasAllOf
import naksha.model.request.ops.TagMapHasAnyOf
import naksha.model.request.ops.TagMapHasKey
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Locks down the current custom-member query behavior without fixing it. The deliberately failing
 * SQL shapes below are diagnostic evidence for the follow-up production work.
 */
class CustomMemberQueryCharacterizationTest {

    private val collection = testCollection()

    @Test
    fun `numeric comparison metadata is text while the runtime value stays numeric`() {
        val clause = build(Equals("probe_i64", 42L))

        assertEquals("probe_i64=\$1 ", clause.where)
        assertEquals(listOf(PgType.STRING), clause.argTypes)
        assertEquals(42L, clause.argValues.single())
    }

    @Test
    fun `numeric scalar values are bound using their runtime JDBC types`() {
        val statement = mock<PreparedStatement>()
        val query = PsqlQuery("SELECT \$1::int8, \$2::float8", arrayOf("text", "text"))

        query.bindArguments(statement, arrayOf(42L, 2.5))

        verify(statement).setLong(1, 42L)
        verify(statement).setDouble(2, 2.5)
    }

    @Test
    fun `tag map has-key leaves a JDBC question-mark operator unescaped`() {
        val clause = build(TagMapHasKey("probe_tag_map", "region"))
        val jdbcQuery = PsqlQuery("SELECT 1 WHERE ${clause.where}", clause.argTypeNames)

        assertEquals("probe_tag_map::jsonb ? \$1 ", clause.where)
        assertEquals("SELECT 1 WHERE probe_tag_map::jsonb ? ? ", jdbcQuery.sql)
        assertEquals(mapOf(1 to listOf(1)), jdbcQuery.dollarToIndices)
    }

    @Test
    fun `tag map any-of currently binds map keys instead of requested tag keys`() {
        val operation = TagMapHasAnyOf("probe_tag_map", "region", "rank")
        val clause = build(operation)

        assertEquals(operation.keys, clause.argValues.single())
        assertNotEquals(operation.tagKeys, clause.argValues.single())
        assertTrue(operation.tagKeys.containsAll(listOf("region", "rank")))
    }

    @Test
    fun `tag map all-of currently binds map keys instead of requested tag keys`() {
        val operation = TagMapHasAllOf("probe_tag_map", "region", "rank")
        val clause = build(operation)

        assertEquals(operation.keys, clause.argValues.single())
        assertNotEquals(operation.tagKeys, clause.argValues.single())
        assertTrue(operation.tagKeys.containsAll(listOf("region", "rank")))
    }

    @Test
    fun `custom spatial operation currently queries the standard geometry member`() {
        val clause = build(Intersects("probe_spatial", SpPoint(20.0, 20.0)))

        assertTrue(clause.where.contains("naksha_2d(geo)"), clause.where)
        assertFalse(clause.where.contains("naksha_2d(probe_spatial)"), clause.where)
    }

    @Test
    fun `tag value comparison is blocked by unfinished value type detection`() {
        assertFailsWith<NakshaException> {
            build(TagEquals("probe_tag_map", "rank", 3))
        }
    }

    private fun build(operation: Op): PgQueryWhereClause {
        val request = ReadFeatures().apply { queryMembers = operation }
        return assertNotNull(PgQueryWhereBuilder(request, collection).build())
    }

    private fun testCollection(): PgCollection {
        val storage = mock<PgStorage>()
        val catalog = PgCatalog(storage, NakshaCatalog("custom_member_query_map"))
        val definition = NakshaCollection("custom_member_query_collection", catalog.id).withXyzMembers().apply {
            addMember(Member("probe_i64", MemberType.INT64, JsonPath("properties", "i64")))
            addMember(Member("probe_tag_map", MemberType.TAG_MAP, JsonPath("properties", "tag_map")))
            addMember(Member("probe_spatial", MemberType.SPATIAL, JsonPath("properties", "spatial")))
        }
        return PgCollection(catalog, definition)
    }
}
