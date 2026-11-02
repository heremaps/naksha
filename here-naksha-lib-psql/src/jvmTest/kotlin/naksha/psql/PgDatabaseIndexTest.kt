package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.IdTextType
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgDatabaseIndexTest {
    private val database = PgDatabaseImpl(Mockito.mock(PgStorage::class.java), Id(1L))

    @Test
    fun executesQualifiedRootIndexDdlAndClosesCursors() {
        val conn = Mockito.mock(PgConnection::class.java)
        val statements = mutableListOf<String>()
        val cursors = mutableListOf<PgCursor>()
        Mockito.doAnswer { invocation ->
            statements.add(invocation.getArgument(0))
            Mockito.mock(PgCursor::class.java).also { cursors.add(it) }
        }.`when`(conn).execute(Mockito.anyString(), Mockito.nullable(Array<Any?>::class.java))
        val index = PgIndex("lookup", arrayOf(PgColumn.FnColumn to PgOrder.ASC))
        database.collection_create_index(conn, "My Schema", "root", index)
        database.collection_drop_index(conn, "My Schema", "root", index)
        assertEquals("CREATE INDEX \"root\$lookup\" ON \"My Schema\".\"root\" USING btree (\"fn\" ASC)", statements[0])
        assertEquals("DROP INDEX IF EXISTS \"My Schema\".\"root\$lookup\"", statements[1])
        for (cursor in cursors) Mockito.verify(cursor).close()
        Mockito.verify(conn, Mockito.never()).commit()
        Mockito.verify(conn, Mockito.never()).rollback()
        Mockito.verify(conn, Mockito.never()).close()
    }

    @Test
    fun rejectsInvalidDefinitionsAndTruncatedNamesBeforeDdl() {
        val conn = Mockito.mock(PgConnection::class.java)
        assertFailsWith<NakshaException> {
            database.collection_create_index(conn, "schema", "root", PgIndex("empty", emptyArray()))
        }
        val index = PgIndex("lookup", arrayOf(PgColumn.FnColumn to PgOrder.ASC))
        assertFailsWith<NakshaException> { database.collection_create_index(conn, "schema", "a".repeat(64), index) }
        assertFailsWith<NakshaException> { database.collection_drop_index(conn, "schema", "a".repeat(64), index) }
        Mockito.verifyNoInteractions(conn)
    }

    @Test
    fun aliasesLongAndUnsafeNamesConsistentlyForCreateAndDrop() {
        val conn = Mockito.mock(PgConnection::class.java)
        val statements = mutableListOf<String>()
        Mockito.doAnswer { invocation ->
            statements.add(invocation.getArgument(0))
            Mockito.mock(PgCursor::class.java)
        }.`when`(conn).execute(Mockito.anyString(), Mockito.nullable(Array<Any?>::class.java))
        for (name in listOf("Road Name", "road-west", "\u00e9".repeat(50), "a".repeat(100))) {
            val index = PgIndex(name, arrayOf(PgColumn.FnColumn to PgOrder.ASC))
            val table = "a".repeat(25)
            val physical = "$table\$${index.id.indexName()}"
            database.collection_create_index(conn, "schema", table, index)
            database.collection_drop_index(conn, "schema", table, index)
            assertEquals("CREATE INDEX ${PgUtil.quoteIdent(physical)} ON \"schema\".${PgUtil.quoteIdent(table)} USING btree (\"fn\" ASC)", statements[statements.size - 2])
            assertEquals("DROP INDEX IF EXISTS \"schema\".${PgUtil.quoteIdent(physical)}", statements.last())
        }
    }
}
