package naksha.psql

import naksha.base.Id
import naksha.base.IdTextType
import naksha.base.NakshaException
import naksha.model.objects.Member
import naksha.model.objects.MemberType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertSame

class PgNamingTest {
    @Test
    fun quotesIdentifiersWithoutChangingTheirMeaning() {
        for (name in listOf("roads", "Roads", "select", "1road", "road.west", "a b", "a\\b", "naksha~admin")) {
            assertEquals("\"$name\"", PgUtil.quoteIdent(name))
        }
        assertEquals("\"a\"\"b\"", PgUtil.quoteIdent("a\"b"))
        assertEquals("\"roads_p0\"", PgUtil.quoteIdent("roads", "_p0"))
        assertEquals("\"\"", PgUtil.quoteIdent(""))
    }

    @Test
    fun mapsRootsAndSchemasUsingTheSameAliasPolicy() {
        for (text in listOf("roads", "Roads", "1road", "roads-west", "roads.v1", "roads~old")) {
            val id = Id(text)
            assertEquals(text, id.catalogName())
            assertEquals(text, id.collectionName())
        }
        for (text in listOf("a".repeat(26), "road west", "caf\u00e9")) {
            val id = Id(text)
            assertEquals(id.textAlias(IdTextType.URL_SAFE, 25, "s"), id.catalogName())
            assertEquals(id.textAlias(IdTextType.URL_SAFE, PG_ROOT_NAME_BYTES, "t"), id.collectionName())
        }
        assertEquals("a".repeat(25), Id("a".repeat(25)).collectionName())
        for (text in listOf("naksha~admin", "naksha~collections", "naksha~${"a".repeat(25)}")) {
            assertEquals(text, Id(text).catalogName())
            assertEquals(Id(text).textAlias(IdTextType.RESERVED, PG_ROOT_NAME_BYTES, "t"), Id(text).collectionName())
        }
        assertFailsWith<NakshaException> { Id("a'b").collectionName() }
    }

    @Test
    fun budgetsFullPartitionAndIndexNamesWithoutOverflow() {
        assertEquals(29, PG_ROOT_NAME_BYTES)
        val root = "a".repeat(PG_ROOT_NAME_BYTES)
        assertEquals(root, Id(root).collectionName())
        assertTrue(Id("a".repeat(PG_ROOT_NAME_BYTES + 1)).collectionName().startsWith("t\$"))
        val id = Id("long index name", Int.MIN_VALUE.toLong(), skipVerification = true)
        val alias = id.indexName()
        assertEquals("i\$_2147483648", alias)
        assertEquals(PG_INDEX_ALIAS_BYTES, alias.encodeToByteArray().size)
        val maximumLeaf = "$root\$${"9".repeat(PG_HISTORY_SUFFIX_BYTES - 1)}\$255"
        assertEquals(PG_IDENTIFIER_BYTES, id.indexName(maximumLeaf).encodeToByteArray().size)
        assertFailsWith<NakshaException> { id.indexName(maximumLeaf + "0") }
        for (shift in 12..41) {
            val leaf = historyTableName(root, naksha.base.Version.HEAD.number - 1, shift, 255)
            assertTrue(id.indexName(leaf).encodeToByteArray().size <= PG_IDENTIFIER_BYTES)
        }
        assertEquals("foo\$2026\$15", historyTableName("foo", 2026L shl 41, 41, 15))
    }

    @Test
    fun mapsDatabaseNamesByUtf8BudgetAndUnsafeAcceptance() {
        for (text in listOf("Roads", "road west", "caf\u00e9", "a".repeat(42), "\u00e9".repeat(21))) {
            assertEquals(text, Id(text).databaseName())
        }
        for (text in listOf("a".repeat(43), "\u00e9".repeat(22), "naksha~admin", "d\$123")) {
            val id = Id(text)
            assertEquals(id.textAlias(IdTextType.UNSAFE, 42, "d"), id.databaseName())
        }
        assertFailsWith<NakshaException> { Id("a'b").databaseName() }
    }

    @Test
    fun keepsLogicalMembersSeparateFromPhysicalColumnLabels() {
        for (name in listOf("RoadName", "road-name", "a".repeat(43))) {
            val member = Member(name, MemberType.STRING)
            val column = PgColumn(3, member)
            val expected = Id(name).textAlias(IdTextType.SAFE, 42, "m")
            assertEquals(name, column.name)
            assertEquals(name, column.member.name)
            assertEquals(name, column.id.text)
            assertSame(member.id, column.id)
            assertEquals(Id(name).number, column.id.number)
            assertEquals(expected, column.columnName)
            assertEquals(PgUtil.quoteIdent(expected), column.ident)
            assertTrue(column.sql.startsWith(column.ident))
            val rows = PgRows().addColumn(column).addColumn(column, "result_alias")
            rows.setColumn(name, 0, "value")
            assertEquals("value", rows.getString(0, name))
            assertEquals(expected, rows.columns[0].sqlAlias)
            assertEquals("result_alias", rows.columns[1].sqlAlias)
            assertEquals("${PgUtil.quoteIdent(expected)}, \"result_alias\"", rows.aliases())
            assertTrue(rows.decodedColumns().contains(column.ident))
        }
        assertEquals("a".repeat(42), Id("a".repeat(42)).columnName())
        assertEquals("fn", PgColumn.FnColumn.columnName)
        assertEquals("nv", PgColumn.NextVersionColumn.columnName)
    }
}
