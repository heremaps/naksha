package naksha.psql

import naksha.base.Id
import naksha.model.NewTuple
import naksha.model.Tuple
import naksha.model.objects.NakshaCatalog
import naksha.model.objects.NakshaCollection
import naksha.model.objects.StoreMode
import kotlin.test.assertEquals

/** Test fixtures publish ordinary metadata; physical assertions query PostgreSQL directly. */
internal object PgPartitionTestMetadata {
  const val ROOTS = """(SELECT n.nspname AS schema_name, c.relname AS table_name, c.oid AS root_oid
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'p' AND NOT c.relispartition AND pg_catalog.obj_description(c.oid, 'pg_class') IS NOT NULL
AND EXISTS (SELECT 1 FROM pg_catalog.pg_inherits i JOIN pg_catalog.pg_class p ON p.oid = i.inhrelid
  WHERE i.inhparent = c.oid AND pg_catalog.pg_get_expr(p.relpartbound, p.oid) <> 'DEFAULT'))"""

  fun publish(database: PgDatabase, conn: PgConnection, schema: String, table: String, partitions: Int = 1, shift: Int = 12) {
    assertEquals(schema, Id(schema).schemaName().name, "Low-level fixtures must use an unaliased logical catalog name")
    assertEquals(table, Id(table).tableName().name, "Low-level fixtures must use an unaliased logical collection name")
    val catalog = PgCatalog(Tuple.encodeObject(NakshaCatalog(database.id, schema), database.pgAdminCatalogs.naksha, 1L).tuple)
    val catalogs = database.pgAdminCatalogs
    val ordinal = Id.featureNumberToOrdinal(catalog.id.number)
    val exists = conn.execute("SELECT 1 AS found FROM ${catalogs.qualifiedName} WHERE fn = $1 AND nv = 9007199254740991", arrayOf(ordinal)).use { it.next() }
    if (!exists) database.rows_insert(conn, catalogs.catalogId, catalogs.id, listOf(NewTuple(catalog.tuple)), catalogs.storeHistory, catalogs.columns)
    val metadata = catalog.collectionsDescriptor(database.pgAdminCollections.naksha)
    database.collection_create_table_if_not_exists(conn, catalog.schema, metadata.table, metadata.columns, metadata.indices,
      metadata.creationOptions(database.storage.config, database.maxTupleSize), schema, metadata.id.text)
    val definition = NakshaCollection(table, schema).apply {
      databaseId = database.id.text
      this.partitions = partitions
      this.shift = shift
      storeHistory = StoreMode.ON
    }
    PgCollection.resolveLayout(definition)
    val tuple = Tuple.encodeObject(definition, metadata.naksha, 1L).tuple
    database.rows_insert(conn, metadata.catalogId, metadata.id, listOf(NewTuple(tuple)), metadata.storeHistory, metadata.columns)
  }
}
