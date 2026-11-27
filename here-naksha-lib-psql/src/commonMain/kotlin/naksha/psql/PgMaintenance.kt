package naksha.psql

import naksha.base.Id
import naksha.base.Action
import naksha.base.Version
import naksha.base.collectionNotFound
import naksha.base.illegalState
import naksha.model.Tuple
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

/**
 * Database-local maintenance discovery using live collection metadata, never a second registry or a possibly stale process cache. Metadata reads and no-op checks release their locks through rollback-only subtransactions. Only roots requiring DDL retain exclusive guards until the caller finishes its transaction.
 * @since 3.0
 */
internal object PgMaintenance {
  /**
   * Enumerates active collections, including reserved descriptors which intentionally have no metadata self-row. Busy metadata tables are skipped and retried on the next sweep.
   * @since 3.0
   */
  fun collections(database: PgDatabase, conn: PgConnection): List<PgCollection> {
    val result = ArrayList<PgCollection>()
    result.add(database.pgAdminCollections)
    result.add(database.pgAdminCatalogs)
    result.add(database.pgAdminTransactions)
    result.add(database.pgAdminBooks)
    val catalogs = read(conn, database.pgAdminCatalogs) ?: return emptyList()
    for (tuple in catalogs) {
      val catalog = PgCatalog(tuple)
      if (catalog.id == Id.ADMIN_CATALOG_ID) continue
      val metadata = catalog.collectionsDescriptor(database.pgAdminCollections.naksha)
      val rows = read(conn, metadata) ?: continue
      result.add(metadata)
      for (row in rows) {
        if (row.id != Id.COLLECTIONS_COL_ID) result.add(PgCollection(catalog.id, row))
      }
    }
    result.sortWith(compareBy({ it.schema.name }, { it.table.name }))
    return result
  }

  /**
   * Reloads one descriptor under its caller's guard without consulting caches. Null means deleted or currently busy metadata; neither authorizes recreating a table.
   * @since 3.0
   */
  fun find(database: PgDatabase, conn: PgConnection, schema: PgSchemaName, table: PgTableName): PgCollection? {
    if (schema == Id.ADMIN_CATALOG_ID.schemaName()) {
      for (collection in arrayOf(database.pgAdminCollections, database.pgAdminCatalogs, database.pgAdminTransactions, database.pgAdminBooks)) {
        if (collection.table == table) return collection
      }
      return null
    }
    val catalogs = read(conn, database.pgAdminCatalogs) ?: return null
    for (tuple in catalogs) {
      val catalog = PgCatalog(tuple)
      if (catalog.schema != schema) continue
      val metadata = catalog.collectionsDescriptor(database.pgAdminCollections.naksha)
      if (metadata.table == table) return metadata
      val rows = read(conn, metadata) ?: return null
      for (row in rows) {
        if (row.id.tableName() == table) return PgCollection(catalog.id, row)
      }
      return null
    }
    return null
  }

  /**
   * Maintains committed metadata targets, skipping busy roots without waiting or rolling back unrelated caller work. New collections are initialized synchronously by creation and become discoverable here once their metadata commits. No-op roots release all attempt-local guards, avoiding write-blocking locks for an already provisioned collection.
   * @since 3.0
   */
  fun maintainAll(database: PgDatabase, conn: PgConnection, version: Long = PgPartitions.currentVersion(conn)): Int {
    if (conn.autoCommit) throw illegalState("Maintenance requires a caller-owned transaction")
    var maintained = 0
    for (target in collections(database, conn)) {
      if (!target.storeHistory) continue
      val schema = target.catalogId.schemaName()
      val table = target.table
      var completed = false
      conn.tryMaintenance {
        if (!PgPartitions.lockCollection(conn, schema, table, tryOnly = true, shared = true)) return@tryMaintenance false
        val current = refresh(database, conn, target) ?: return@tryMaintenance false
        if (!current.storeHistory) return@tryMaintenance false
        owner(conn, current)
        val options = current.creationOptions(database.storage.config, database.maxTupleSize)
        if (PgPartitions.complete(conn, schema, table, version, options)) {
          completed = true
          return@tryMaintenance false
        }
        if (!PgPartitions.lockCollection(conn, schema, table, tryOnly = true)) return@tryMaintenance false
        owner(conn, current)
        PgPartitions.maintain(conn, schema, table, version, options, nowait = true)
        completed = true
        true
      }
      if (completed) maintained++
    }
    return maintained
  }

  private fun owner(conn: PgConnection, collection: PgCollection) {
    val expected = "${quoteIdent(collection.catalogId.text)}.${quoteIdent(collection.id.text)}"
    conn.execute("""
SELECT pg_catalog.obj_description(c.oid, 'pg_class') AS owner
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = $1 AND c.relname = $2
""", arrayOf(collection.catalogId.schemaName(), collection.table.name)).use { cursor ->
      if (!cursor.next()) throw collectionNotFound("Physical collection ${collection.qualifiedName} is missing")
      if (cursor.column("owner") != expected) throw illegalState("Physical collection ${collection.qualifiedName} belongs to another logical identity")
    }
  }

  private fun refresh(database: PgDatabase, conn: PgConnection, target: PgCollection): PgCollection? {
    if (target.catalogId == Id.ADMIN_CATALOG_ID) return find(database, conn, target.schema, target.table)
    val catalogs = read(conn, database.pgAdminCatalogs, target.catalogId) ?: return null
    val tuple = catalogs.firstOrNull() ?: return null
    val catalog = PgCatalog(tuple)
    if (catalog.schema != target.schema) return null
    val metadata = catalog.collectionsDescriptor(database.pgAdminCollections.naksha)
    if (target.id == Id.COLLECTIONS_COL_ID) return metadata
    val rows = read(conn, metadata, target.id) ?: return null
    val current = rows.firstOrNull() ?: return null
    if (current.id.tableName() != target.table) return null
    return PgCollection(catalog.id, current)
  }

  private fun read(conn: PgConnection, metadata: PgCollection, id: Id? = null): List<Tuple>? {
    var result: List<Tuple>? = null
    conn.tryMaintenance {
      if (!PgPartitions.lockCollection(conn, metadata.schema, metadata.table, shared = true, tryOnly = true)) return@tryMaintenance false
      if (!PgPartitions.exists(conn, metadata.schema, metadata.table)) return@tryMaintenance false
      conn.execute("LOCK TABLE ${metadata.qualifiedName} IN ACCESS SHARE MODE NOWAIT").close()
      val rows = PgRows().withCollection(metadata)
      val predicate = if (id == null) "" else " AND fn = $1::bigint"
      val args = if (id == null) null else arrayOf<Any?>(Id.featureNumberToOrdinal(id.number))
      conn.execute("SELECT * FROM ${metadata.qualifiedName} WHERE nv = ${Version.HEAD.number} AND (version & 3) <> ${Action.DELETE.intValue}$predicate ORDER BY fn", args).fetch().use { cursor ->
        rows.readAll(cursor)
      }
      val tuples = ArrayList<Tuple>(rows.size)
      for (i in 0 until rows.size) {
        val tuple = rows[i]
        if (tuple != null) tuples.add(tuple)
      }
      result = tuples
      false // Release metadata locks immediately; these reads have no side effects to retain.
    }
    return result
  }
}
