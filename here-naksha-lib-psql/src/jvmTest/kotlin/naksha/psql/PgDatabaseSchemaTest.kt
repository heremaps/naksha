package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PgDatabaseSchemaTest {
    private val database = PgDatabaseImpl(Mockito.mock(PgStorage::class.java), Id(1L))

    @Test
    fun looksUpUnsignedOidsAndClosesCursors() {
        for (oid in listOf(null, "2147483648", "4294967295")) {
            val cursor = Mockito.mock(PgCursor::class.java)
            Mockito.`when`(cursor.next()).thenReturn(oid != null)
            if (oid != null) Mockito.`when`(cursor.get<String>("oid")).thenReturn(oid)
            val conn = Mockito.mock(PgConnection::class.java, Mockito.RETURNS_DEEP_STUBS)
            Mockito.doAnswer { invocation ->
                assertEquals("SELECT oid::text AS oid FROM pg_catalog.pg_namespace WHERE nspname = $1", invocation.getArgument<String>(0))
                assertEquals("Mixed Name", invocation.getArgument<Array<Any?>>(1)[0])
                cursor
            }.`when`(conn).execute(Mockito.anyString(), Mockito.any())
            assertEquals(oid?.toLong() ?: -1L, database.catalog_get_schema_oid(conn, "Mixed Name"))
            Mockito.verify(cursor).close()
        }
    }

    @Test
    fun createsOrReusesSchemaWithoutCommittingCallerTransaction() {
        for (exists in listOf(false, true)) {
            val statements = mutableListOf<String>()
            val cursors = mutableListOf<PgCursor>()
            var lookups = 0
            val conn = Mockito.mock(PgConnection::class.java)
            Mockito.`when`(conn.autoCommit).thenReturn(false)
            Mockito.doAnswer { invocation ->
                val sql = invocation.getArgument<String>(0)
                statements.add(sql)
                val cursor = Mockito.mock(PgCursor::class.java)
                Mockito.`when`(cursor.fetch()).thenReturn(cursor)
                Mockito.`when`(cursor.get<Boolean>("upgrading")).thenReturn(false)
                cursors.add(cursor)
                if (sql.startsWith("SELECT oid")) {
                    Mockito.`when`(cursor.next()).thenReturn(exists || lookups++ >= 2)
                    Mockito.`when`(cursor.get<String>("oid")).thenReturn("4294967295")
                }
                cursor
            }.`when`(conn).execute(Mockito.anyString(), Mockito.nullable(Array<Any?>::class.java))
            val oid = database.catalog_get_or_create_schema(conn, "Mixed Name", "logical catalog")
            assertEquals(if (exists) -4294967295L else 4294967295L, oid)
            assertEquals("SELECT oid::text AS oid FROM pg_catalog.pg_namespace WHERE nspname = $1", statements[0])
            val lockPosition = if (exists) 1 else 2
            if (!exists) assertTrue(statements[1].contains("pg_locks"))
            assertEquals("SELECT pg_catalog.pg_advisory_xact_lock${if (exists) "_shared" else ""}(pg_catalog.hashtextextended($1, 0)) AS locked", statements[lockPosition])
            assertEquals(statements[0], statements[lockPosition + 1])
            if (exists) assertEquals(3, statements.size) else {
                assertEquals("CREATE SCHEMA \"Mixed Name\"", statements[4])
                assertEquals("COMMENT ON SCHEMA \"Mixed Name\" IS E'logical catalog'", statements[5])
                assertEquals(7, statements.size)
            }
            for (cursor in cursors) Mockito.verify(cursor).close()
            Mockito.verify(conn, Mockito.never()).commit()
            Mockito.verify(conn, Mockito.never()).rollback()
            Mockito.verify(conn, Mockito.never()).close()
        }
    }

    @Test
    fun rejectsNamesThatWouldBeTruncatedBeforeExecutingSql() {
        val conn = Mockito.mock(PgConnection::class.java)
        for (name in listOf("", "a\u0000b", "a".repeat(64), "\u00e9".repeat(32))) {
            assertFailsWith<NakshaException> { database.catalog_get_or_create_schema(conn, name, "logical") }
        }
        Mockito.verifyNoInteractions(conn)
    }
}
