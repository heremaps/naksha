package naksha.psql

import naksha.base.Id
import naksha.base.NakshaException
import naksha.base.Version
import naksha.model.Naksha
import naksha.model.Tuple
import naksha.model.objects.NakshaCatalog
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "NAKSHA_TEST_CATALOG_DB_URL", matches = ".+")
class PgCatalogCreationTest {
  private val registry = "\"naksha~admin\".naksha_partman"

  @Test
  fun createsReservedCollectionsTransactionallyAndResolvesColdSessionDescriptor() {
    val url = System.getenv("NAKSHA_TEST_CATALOG_DB_URL")
    assertEquals(url, System.getenv("NAKSHA_TEST_PSQL_DB_URL"),
      "Catalog creation and storage bootstrap must use the same fresh isolated database")
    DriverManager.getConnection(url).use { jdbc ->
      jdbc.createStatement().use { statement ->
        statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname = 'naksha~admin'").use { rows ->
          assertFalse(rows.next(), "Requires a fresh disposable database; existing schemas are never cleared")
        }
      }
    }
    val config = PgConfig("catalog_creation_probe").apply {
      className = "naksha.psql.PgStorage"
      master = PgInstanceConfig.fromUri(url)
    }
    val storage = PgStorage(config, true, false)
    assertTrue(storage.initialized)
    val database = storage.database
    val catalog = PgCatalog(Tuple.encodeObject(
      NakshaCatalog(storage.id, "Catalog Creation Target"), database.pgAdminCatalogs.naksha, 1L
    ).tuple)
    assertEquals(Id("Catalog Creation Target").number, catalog.id.number)
    assertEquals(0L, catalog.tuple.version) // Version input 1 with CREATE action becomes 0.
    database.cachePgCatalog(catalog)
    val descriptor = storage.newSession(Naksha.useAdminOptions(), false).use { session ->
      assertSame(catalog, session.getPgCatalog(catalog.id.intValue))
      assertNull(session.getTransaction())
      assertNotNull(session.getPgCollection(catalog.id.intValue, Id.COLLECTIONS_COL_ID.intValue))
    }

    storage.newConnection(Naksha.useAdminOptions(), false, PgStorage.SKIP_INIT).use { conn ->
      conn.autoCommit = false
      try {
        conn.execute("SET LOCAL lock_timeout = '5s'; SET LOCAL statement_timeout = '120s'").close()
        assertTrue(database.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text) > 0)
        create(storage, conn, catalog, descriptor)
        assertPhysicalContract(conn, catalog, descriptor)
        val before = snapshot(conn, catalog)
        val registration = registration(conn, catalog)
        val savepoint = conn.jdbc.setSavepoint()
        try {
          assertTrue(database.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text) < 0)
          create(storage, conn, catalog, descriptor)
          assertEquals(before, snapshot(conn, catalog), "Repeated creation must preserve object OIDs and definitions")
          assertEquals(registration, registration(conn, catalog))
          assertFailsWith<NakshaException> {
            create(storage, conn, catalog, descriptor, "Different logical owner")
          }
        } finally {
          conn.jdbc.rollback(savepoint)
          conn.jdbc.releaseSavepoint(savepoint)
        }
        assertEquals(before, snapshot(conn, catalog))
        assertEquals(registration, registration(conn, catalog))
        assertFalse(conn.isClosed())
        assertFalse(conn.autoCommit)
        conn.rollback()
        assertEquals("0", scalar(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = ${literal(catalog.name)}"))
        assertEquals("0", scalar(conn, "SELECT count(*) FROM $registry WHERE schema_name = ${literal(catalog.name)}"))
        assertNull(scalar(conn, "SELECT to_regclass(${literal(descriptor.qualifiedName)})"))

        assertTrue(database.catalog_get_or_create_schema(conn, catalog.name, catalog.id.text) > 0)
        create(storage, conn, catalog, descriptor)
        conn.commit()
      } finally {
        conn.rollback()
      }
    }

    // Cache only the catalog: no self-row or reserved collection cache entry is needed.
    database.cachePgCatalog(catalog)
    storage.newSession(Naksha.useAdminOptions(), false).use { session ->
      assertSame(catalog, session.getPgCatalog(catalog.id.intValue))
      assertNull(session.getTransaction())
      val cold = assertNotNull(session.getPgCollection(catalog.id.intValue, Id.COLLECTIONS_COL_ID.intValue))
      assertEquals(catalog.id, cold.catalogId)
      assertEquals(catalog.id.text, cold.naksha.catalogId)
      assertEquals(catalog.id.intValue, cold.tupleNumber.catalogNumber)
      assertEquals(catalog.id.intValue, cold.naksha.catalogNumber)
      assertEquals(descriptor.id, cold.id)
      assertEquals(0L, cold.tuple.version)
      assertEquals(descriptor.tupleNumber, cold.tupleNumber)
      assertEquals(descriptor.qualifiedName, cold.qualifiedName)
      assertEquals(descriptor.joinColumns(), cold.joinColumns())
      assertEquals(descriptor.indices.single().definition(), cold.indices.single().definition())
      assertNull(session.getTransaction(), "Cache-only descriptor synthesis must not allocate a transaction")
      session.useConnNonNull(false) { conn ->
        assertPhysicalContract(conn, catalog, cold)
        assertEquals("0", scalar(conn, "SELECT count(*) FROM ${cold.qualifiedName} WHERE fn = ${cold.id.number}"),
          "The reserved collection must not require a persisted self-row")
      }
    }
  }

  // Mirrors the private writer helper's physical calls, not the legacy writer mutation path.
  private fun create(storage: PgStorage, conn: PgConnection, catalog: PgCatalog, collection: PgCollection,
    owner: String = collection.id.text) {
    val database = storage.database
    database.collection_create_table_if_not_exists(
      conn = conn, schema = catalog.name, table = collection.name,
      columns = collection.columns, indices = collection.indices,
      options = collection.creationOptions(storage.config, database.maxTupleSize),
      logicalCatalog = catalog.id.text, logicalCollection = owner
    )
  }

  private fun assertPhysicalContract(conn: PgConnection, catalog: PgCatalog, collection: PgCollection) {
    val root = literal(collection.qualifiedName) + "::regclass"
    assertEquals("0", scalar(conn, "SELECT count(*) FROM ${collection.qualifiedName}"))
    assertEquals("p", scalar(conn, "SELECT relkind FROM pg_class WHERE oid = $root"))
    assertEquals("RANGE (nv)", scalar(conn, "SELECT pg_get_partkeydef($root)"))
    assertEquals("${PgUtil.quoteIdent(catalog.id.text)}.${PgUtil.quoteIdent(collection.id.text)}",
      scalar(conn, "SELECT obj_description($root, 'pg_class')"))
    assertEquals(collection.columns.size.toString(), scalar(conn,
      "SELECT count(*) FROM pg_attribute WHERE attrelid = $root AND attnum > 0 AND NOT attisdropped"))
    for (column in collection.columns) {
      assertEquals(column.name, scalar(conn,
        "SELECT col_description(attrelid, attnum) FROM pg_attribute WHERE attrelid = $root AND attname = ${literal(column.columnName)}"))
    }
    assertEquals("3", scalar(conn, """
      SELECT count(*) FROM pg_attribute WHERE attrelid = $root AND attname IN ('fn', 'version', 'nv')
        AND atttypid = 'bigint'::regtype AND attnotnull
    """))
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM pg_index i JOIN pg_constraint c ON c.conindid = i.indexrelid
      WHERE i.indrelid = $root AND c.contype = 'p' AND i.indisvalid AND i.indisready
        AND i.indnkeyatts = 3 AND pg_get_indexdef(i.indexrelid, 1, true) LIKE 'nv%'
        AND pg_get_indexdef(i.indexrelid, 2, true) LIKE 'fn%'
        AND pg_get_indexdef(i.indexrelid, 3, true) LIKE 'version%'
    """))
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM pg_index i WHERE i.indrelid = $root AND i.indisunique AND i.indisvalid
        AND i.indnkeyatts = 2 AND i.indnatts = 3
        AND pg_get_indexdef(i.indexrelid, 1, true) LIKE 'fn%'
        AND pg_get_indexdef(i.indexrelid, 2, true) LIKE 'nv%'
        AND pg_get_indexdef(i.indexrelid, 3, true) LIKE 'version%'
    """))
    val tags = collection.indices.single()
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
      JOIN pg_am a ON a.oid = c.relam WHERE i.indrelid = $root
        AND c.relname = ${literal(tags.id.indexName(collection.name))} AND a.amname = 'gin'
        AND i.indisvalid AND i.indisready AND i.indnkeyatts = 1 AND i.indnatts = 1
    """))
    for (suffix in listOf("\$id", "\$version", "\$head")) {
      assertNotNull(scalar(conn, "SELECT to_regclass(${literal("${catalog.quotedName}.${PgUtil.quoteIdent(collection.name + suffix)}")})"))
    }
    assertEquals("5", scalar(conn, "SELECT count(*) FROM pg_partition_tree($root) WHERE level = 1"))
    assertEquals("4", scalar(conn, """
      SELECT count(*) FROM pg_partition_tree($root) t JOIN pg_class c ON c.oid = t.relid
      WHERE t.level = 1 AND c.relname <> ${literal(collection.name + "\$head")}
    """))
    assertEquals("1", scalar(conn, """
      SELECT count(*) FROM $registry WHERE schema_name = ${literal(catalog.name)}
        AND table_name = ${literal(collection.name)}
    """))
    assertEquals("0", scalar(conn, """
      SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
      WHERE n.nspname = 'naksha~admin' AND starts_with(p.proname, 'naksha_partman_')
    """), "Physical creation must use shared Kotlin maintenance, not legacy server methods")
    assertTrue(collection.storeHistory)
    assertTrue(assertNotNull(scalar(conn,
      "SELECT pg_get_expr(adbin, adrelid) FROM pg_attrdef WHERE adrelid = $root AND adnum = " +
        "(SELECT attnum FROM pg_attribute WHERE attrelid = $root AND attname = 'nv')")).contains(Version.HEAD.number.toString()))
  }

  private fun snapshot(conn: PgConnection, catalog: PgCatalog): String? = scalar(conn, """
    SELECT string_agg(ROW(c.oid, c.relname, c.relkind, c.reloptions,
      obj_description(c.oid, 'pg_class'), pg_get_expr(c.relpartbound, c.oid))::text, '|' ORDER BY c.oid)
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = ${literal(catalog.name)}
  """)

  private fun registration(conn: PgConnection, catalog: PgCatalog): String? = scalar(conn,
    "SELECT row_to_json(r)::text FROM $registry r WHERE schema_name = ${literal(catalog.name)}")

  private fun scalar(conn: PgConnection, sql: String): String? {
    conn.jdbc.createStatement().use { statement ->
      statement.executeQuery(sql).use { rows ->
        assertTrue(rows.next(), "Expected one result row")
        val value = rows.getString(1)
        assertFalse(rows.next(), "Expected only one result row")
        return value
      }
    }
  }

  private fun literal(value: String) = PgUtil.quoteLiteral(value)
}
