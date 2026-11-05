package naksha.psql

import naksha.base.Id
import naksha.base.Id.IdCompanion.ADMIN_CATALOG_QUOTED
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.collectionNotFound
import naksha.psql.PgUtil.PgUtilCompanion.quoteIdent

/**
 * Per-root partition provisioning in the caller's transaction. No root creation, version allocation, retention, scheduling, index repair, commit or outer transaction rollback occurs here. Sweeps isolate each root attempt in a subtransaction. Internal cursors are closed; the caller's connection and cursors are never closed.
 *
 * Requires a permanent standalone RANGE (nv) root and a clean registry installation. Cooperating callers serialize with transaction advisory locks; recursive ACCESS EXCLUSIVE locks protect catalog inspection and provisioning. Callers must roll back on failure, including failures after partial DDL, and should use READ COMMITTED to observe concurrent committed provisioning.
 * @since 3.0
 */
internal object PgPartitions {
  private const val HEAD = 9007199254740991L
  private const val HEAD_CHECK = "(nv = '9007199254740991'::bigint)"
  private val registry = "$ADMIN_CATALOG_QUOTED.\"naksha_partman\""

  /**
   * Installs only the registry table into the existing admin schema. Does not install server functions, triggers or compatibility migrations.
   * @since 3.0
   */
  fun install(conn: PgConnection) {
    transaction(conn)
    conn.execute("""
CREATE TABLE IF NOT EXISTS $registry (
  "schema_name" text NOT NULL CHECK (pg_catalog.octet_length("schema_name") BETWEEN 1 AND 63),
  "table_name" text NOT NULL CHECK (pg_catalog.octet_length("table_name") BETWEEN 1 AND 29),
  "shift" integer NOT NULL CHECK ("shift" BETWEEN 12 AND 41),
  "partitions" integer NOT NULL CHECK ("partitions" BETWEEN 1 AND 256),
  "root_oid" oid NOT NULL UNIQUE,
  "toast_tuple_target" integer NOT NULL,
  "head_fillfactor" integer NOT NULL CHECK ("head_fillfactor" BETWEEN 10 AND 100),
  "history_fillfactor" integer NOT NULL CHECK ("history_fillfactor" BETWEEN 10 AND 100),
  "tablespace_name" text NOT NULL CHECK (pg_catalog.octet_length("tablespace_name") BETWEEN 1 AND 63),
  PRIMARY KEY ("schema_name", "table_name")
)
""").close()
  }

  /**
   * Registers explicit immutable settings for an existing root. A null tablespace resolves to the actual root/database tablespace, not the session default. Repeated registration must match every setting and the root OID; configuration is never updated.
   * @since 3.0
   */
  fun register(
    conn: PgConnection, schema: String, table: String, shift: Int, partitions: Int,
    toastTupleTarget: Int, headFillfactor: Int, historyFillfactor: Int, tablespace: String?
  ) {
    if (shift !in 12..41) throw illegalArg("Partition shift must be in 12..41")
    settings(conn, partitions, toastTupleTarget, headFillfactor, historyFillfactor, tablespace)
    val root = lockRoot(conn, schema, table)
    val space = resolveTablespace(conn, root, tablespace)
    val config = Config(root, shift, partitions, toastTupleTarget, headFillfactor, historyFillfactor, space)
    val existing = registered(conn, schema, table)
    if (existing != null) {
      if (existing != config) throw illegalArg("Conflicting immutable partition registration for $schema.$table")
      return
    }
    conn.execute("""
INSERT INTO $registry
  ("schema_name", "table_name", "shift", "partitions", "root_oid", "toast_tuple_target", "head_fillfactor", "history_fillfactor", "tablespace_name")
VALUES ($1, $2, $3, $4, $5::text::oid, $6, $7, $8, $9)
""", arrayOf(schema, table, shift, partitions, root.toString(), toastTupleTarget, headFillfactor, historyFillfactor, space)).close()
  }

  /**
   * Provisions restricted HEAD plus the finite range containing [version] and up to three subsequent ranges, clipped at HEAD. Validates all retained children and repairs their missing fn leaves, without selecting an anchor or allocating a version. Requires the registered root OID to still match.
   * @since 3.0
   */
  fun maintain(conn: PgConnection, schema: String, table: String, version: Long) {
    if (version < 0 || version >= HEAD) throw illegalArg("Maintenance requires a finite version in 0..HEAD-1")
    val root = lockRoot(conn, schema, table)
    val config = registered(conn, schema, table)
      ?: throw illegalArg("Unregistered partition root $schema.$table")
    maintainLocked(conn, schema, table, version, root, config)
  }

  /**
   * Tests physical existence under the caller's collection guard without consulting potentially tombstoned metadata.
   * @since 3.0
   */
  fun exists(conn: PgConnection, schema: String, table: String): Boolean = oid(conn, schema, table) != null

  /**
   * Checks only the HEAD and finite range needed by a write, not the maintainer's full integrity contract. The caller must hold a shared collection guard until transaction completion, or roll back the probe's subtransaction before taking an exclusive guard for maintenance. No DDL or lock upgrade occurs here.
   * @since 3.0
   */
  fun covered(conn: PgConnection, schema: String, table: String, version: Long): Boolean {
    transaction(conn)
    if (version < 0 || version >= HEAD) throw illegalArg("Coverage requires a finite version in 0..HEAD-1")
    val root = oid(conn, schema, table) ?: throw collectionNotFound("Partition root $schema.$table is missing")
    val config = registered(conn, schema, table) ?: throw illegalState("Unregistered partition root $schema.$table")
    if (root != config.root) throw illegalState("Registered root $schema.$table has been replaced")
    // The shared collection guard excludes cooperative DDL; ONLY avoids walking retained history.
    conn.execute("LOCK TABLE ONLY ${quoteIdent(schema)}.${quoteIdent(table)} IN ACCESS SHARE MODE").close()
    if (oid(conn, schema, table) != root) throw illegalState("Partition root $schema.$table changed while acquiring its lock")
    val number = version shr config.shift
    val start = number shl config.shift
    val end = minOf(start + (1L shl config.shift), HEAD)
    val leaves = ArrayList<String>(config.partitions)
    for (slot in 0 until config.partitions) {
      val low = if (slot == 0) "MINVALUE" else "'${Id.partitionIndexMin(slot, config.partitions)}'"
      val high = if (slot == config.partitions - 1) "MAXVALUE" else "'${Id.partitionIndexMin(slot + 1, config.partitions)}'"
      leaves.add("($slot, ${PgUtil.quoteLiteral("FOR VALUES FROM ($low) TO ($high)")})")
    }
    return check(conn, """
WITH expected AS (
  SELECT $2::text || suffix AS name, bound FROM (VALUES
    ('${'$'}head', 'DEFAULT'),
    ('${'$'}' || $3::text, $4::text)
  ) ranges(suffix, bound)
), candidates AS MATERIALIZED (
  SELECT c.oid, c.relname, c.relpartbound, c.relkind, e.bound FROM expected e
  JOIN pg_catalog.pg_namespace n ON n.nspname = $1
  JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid AND c.relname = e.name
  JOIN pg_catalog.pg_inherits i ON i.inhrelid = c.oid AND i.inhparent = $5::text::oid AND NOT i.inhdetachpending
  WHERE c.relispartition
), children AS MATERIALIZED (
  SELECT c.oid, c.relname FROM candidates c
  WHERE pg_catalog.pg_get_expr(c.relpartbound, c.oid) = c.bound
    AND c.relkind = CASE WHEN $6::int = 1 THEN 'r'::pg_catalog."char" ELSE 'p'::pg_catalog."char" END
    AND ($6::int = 1 OR pg_catalog.pg_get_partkeydef(c.oid) = 'RANGE (fn)')
), leaf_candidates AS MATERIALIZED (
  SELECT c.oid AS parent, leaf.oid, leaf.relname, leaf.relpartbound
  FROM children c JOIN pg_catalog.pg_inherits i ON i.inhparent = c.oid AND NOT i.inhdetachpending
  JOIN pg_catalog.pg_class leaf ON leaf.oid = i.inhrelid
  JOIN pg_catalog.pg_namespace n ON n.oid = leaf.relnamespace
  WHERE n.nspname = $1 AND leaf.relkind = 'r' AND leaf.relispartition
)
SELECT (SELECT count(*) FROM children) = 2 AND ($6::int = 1 OR NOT EXISTS (
  SELECT 1 FROM children c CROSS JOIN (VALUES ${leaves.joinToString(",")}) expected_leaf(slot, bound)
  WHERE NOT EXISTS (
    SELECT 1 FROM leaf_candidates leaf
    WHERE leaf.parent = c.oid AND leaf.relname = c.relname || '${'$'}' || slot::text
      AND pg_catalog.pg_get_expr(leaf.relpartbound, leaf.oid) = expected_leaf.bound
  )
)) AS ok
""", arrayOf(schema, table, number.toString(), "FOR VALUES FROM ('$start') TO ('$end')", root.toString(), config.partitions))
  }

  /**
   * Validates the locked root against its registration and provisions its coverage.
   * @since 3.0
   */
  private fun maintainLocked(conn: PgConnection, schema: String, table: String, version: Long, root: Long, config: Config) {
    if (root != config.root) throw illegalState("Registered root $schema.$table has been replaced")
    settings(conn, config.partitions, config.toast, config.headFill, config.historyFill, config.space)
    provision(conn, schema, table, config, version)
  }

  /**
   * Reads the greater of the server UTC date bits and the installed allocator's last_value, without allocating a version. An absent allocator uses only the server date. Requires a caller-owned transaction and returns a finite version in 0..HEAD-1; no sequence advancement, commit or rollback occurs here.
   * @since 3.0
   */
  fun currentVersion(conn: PgConnection): Long {
    transaction(conn)
    var version = conn.execute("SELECT ((extract(year FROM d)::bigint << 41) | (extract(month FROM d)::bigint << 37) | (extract(day FROM d)::bigint << 32))::text AS version FROM (SELECT (pg_catalog.clock_timestamp() AT TIME ZONE 'UTC')::date AS d) today").fetch().use { cursor ->
      cursor.get<String>("version").toLong()
    }
    val sequence = "$ADMIN_CATALOG_QUOTED.${quoteIdent(NAKSHA_VERSION_SEQ)}"
    val installed = conn.execute("SELECT pg_catalog.to_regclass($1)::text AS name", arrayOf(sequence)).fetch().use { cursor ->
      cursor.column("name") != null
    }
    if (installed) {
      conn.execute("SELECT last_value::text AS version FROM $sequence").fetch().use { cursor ->
        version = maxOf(version, cursor.get<String>("version").toLong())
      }
    }
    if (version < 0 || version >= HEAD) throw illegalArg("Maintenance requires a finite version in 0..HEAD-1")
    return version
  }

  /**
   * Maintains a deterministic registry snapshot at one shared [version], returning only the number of successfully maintained roots. The snapshot cursor is closed before root locking or DDL; registrations committed after the snapshot are deferred to the next sweep.
   *
   * Use READ COMMITTED and a single caller-owned transaction. Each root attempt uses an isolated subtransaction with nonblocking advisory and recursive physical locks. Advisory conflicts, physical lock conflicts (SQLSTATE 55P03), and registrations removed by a committed cooperative drop are skipped, rolling back the attempt and releasing its locks. Successful root locks remain held until transaction completion. Missing roots with retained registrations, changed OIDs, invalid topology and all other failures propagate; the caller must roll back the entire transaction. No sweep-wide mutex or scheduler is used.
   * @since 3.0
   */
  fun maintainAll(conn: PgConnection, version: Long = currentVersion(conn)): Int {
    transaction(conn)
    if (version < 0 || version >= HEAD) throw illegalArg("Maintenance requires a finite version in 0..HEAD-1")
    val roots = ArrayList<Pair<String, String>>()
    conn.execute("""
SELECT "schema_name", "table_name" FROM $registry
ORDER BY "schema_name" COLLATE "C", "table_name" COLLATE "C"
""").use { cursor ->
      while (cursor.next()) roots.add(Pair(cursor["schema_name"], cursor["table_name"]))
    }
    var maintained = 0
    for ((schema, table) in roots) {
      if (conn.tryMaintenance {
        if (!lockCollection(conn, schema, table, tryOnly = true)) return@tryMaintenance false
        val config = registered(conn, schema, table) ?: return@tryMaintenance false
        val root = lockRoot(conn, schema, table, nowait = true)
        maintainLocked(conn, schema, table, version, root, config)
        true
      }) maintained++
    }
    return maintained
  }

  /**
   * Provisions only restricted HEAD and its optional fn leaves for history-OFF, using no registry access. Rejects any finite or unexpected child and never creates or replaces the root.
   * @since 3.0
   */
  fun head(
    conn: PgConnection, schema: String, table: String, partitions: Int,
    toastTupleTarget: Int, headFillfactor: Int, tablespace: String?
  ) {
    settings(conn, partitions, toastTupleTarget, headFillfactor, headFillfactor, tablespace)
    val root = lockRoot(conn, schema, table)
    val space = resolveTablespace(conn, root, tablespace)
    provision(conn, schema, table, Config(root, 12, partitions, toastTupleTarget, headFillfactor, headFillfactor, space), null)
  }

  /**
   * Immutable registry values, also used for unregistered HEAD-only provisioning.
   * @since 3.0
   */
  private data class Config(
    val root: Long, val shift: Int, val partitions: Int, val toast: Int,
    val headFill: Int, val historyFill: Int, val space: String
  )

  /**
   * Requires locks and DDL to remain in the caller's transaction.
   * @since 3.0
   */
  private fun transaction(conn: PgConnection) {
    if (conn.autoCommit) throw illegalState("Partition provisioning requires a caller-owned transaction")
  }

  /**
   * Validates UTF-8 identifier limits without PostgreSQL's silent truncation.
   * @since 3.0
   */
  private fun name(value: String, limit: Int) {
    if (value.isEmpty() || '\u0000' in value || value.encodeToByteArray().size > limit) {
      throw illegalArg("Physical name must contain 1..$limit UTF-8 bytes without NUL")
    }
  }

  /**
   * Locks a catalog for the caller's transaction, exclusively for catalog creation/drop or shared for collection operations. Returns false only when a nonblocking attempt conflicts.
   * @since 3.0
   */
  internal fun lockCatalog(conn: PgConnection, schema: String, shared: Boolean = false, tryOnly: Boolean = false): Boolean {
    transaction(conn)
    name(schema, 63)
    if (!shared && !tryOnly) {
      val upgrading = conn.execute("""
SELECT EXISTS (
  SELECT 1 FROM pg_catalog.pg_locks
  WHERE pid = pg_catalog.pg_backend_pid() AND locktype = 'advisory' AND mode = 'ShareLock' AND granted
    AND objsubid = 1
    AND classid::bigint = ((pg_catalog.hashtextextended($1, 0) >> 32) & 4294967295)
    AND objid::bigint = (pg_catalog.hashtextextended($1, 0) & 4294967295)
) AS upgrading
""", arrayOf("naksha:schema:$schema")).fetch().use { cursor -> cursor.get<Boolean>("upgrading") }
      if (upgrading) {
        if (!lockCatalog(conn, schema, tryOnly = true)) throw illegalState("Catalog lock upgrade is busy; retry the transaction with catalog-exclusive work first")
        return true
      }
    }
    val function = "pg_${if (tryOnly) "try_" else ""}advisory_xact_lock${if (shared) "_shared" else ""}"
    val sql = "SELECT pg_catalog.$function(pg_catalog.hashtextextended($1, 0)) AS locked"
    val args = arrayOf<Any?>("naksha:schema:$schema")
    if (tryOnly) return conn.execute(sql, args).fetch().use { cursor -> cursor.get<Boolean>("locked") }
    conn.execute(sql, args).close()
    return true
  }

  /**
   * Takes a shared catalog guard followed by a shared write guard or exclusive maintenance/drop guard. Returns false on a nonblocking conflict; roll back the attempt's subtransaction to release any catalog guard already acquired. Upgrades retained from earlier requests are nonblocking to avoid upgrade deadlocks.
   * @since 3.0
   */
  fun lockCollection(conn: PgConnection, schema: String, table: String, tryOnly: Boolean = false, shared: Boolean = false): Boolean {
    transaction(conn)
    name(schema, 63)
    name(table, 29)
    if (!lockCatalog(conn, schema, shared = true, tryOnly = tryOnly)) return false
    if (!shared && !tryOnly) {
      val upgrading = conn.execute("""
SELECT EXISTS (
  SELECT 1 FROM pg_catalog.pg_locks
  WHERE pid = pg_catalog.pg_backend_pid() AND locktype = 'advisory' AND mode = 'ShareLock' AND granted
    AND objsubid = 1
    AND classid::bigint = ((pg_catalog.hashtextextended(pg_catalog.format('naksha:partman:%I.%I', $1::text, $2::text), 0) >> 32) & 4294967295)
    AND objid::bigint = (pg_catalog.hashtextextended(pg_catalog.format('naksha:partman:%I.%I', $1::text, $2::text), 0) & 4294967295)
) AS upgrading
""", arrayOf(schema, table)).fetch().use { cursor -> cursor.get<Boolean>("upgrading") }
      if (upgrading) {
        if (!lockCollection(conn, schema, table, tryOnly = true)) throw illegalState("Collection lock upgrade is busy; retry the transaction with exclusive work first")
        return true
      }
    }
    val function = "pg_${if (tryOnly) "try_" else ""}advisory_xact_lock${if (shared) "_shared" else ""}"
    val sql = "SELECT pg_catalog.$function(pg_catalog.hashtextextended(pg_catalog.format('naksha:partman:%I.%I', $1::text, $2::text), 0)) AS locked"
    val args = arrayOf<Any?>(schema, table)
    if (tryOnly) return conn.execute(sql, args).fetch().use { cursor -> cursor.get<Boolean>("locked") }
    conn.execute(sql, args).close()
    return true
  }

  /**
   * Validates block-size-dependent TOAST and explicit partition/storage settings.
   * @since 3.0
   */
  private fun settings(conn: PgConnection, partitions: Int, toast: Int, headFill: Int, historyFill: Int, space: String?) {
    transaction(conn)
    if (partitions !in 1..256 || headFill !in 10..100 || historyFill !in 10..100) {
      throw illegalArg("Partition count must be in 1..256 and fillfactors in 10..100")
    }
    if (space != null) name(space, 63)
    conn.execute("SELECT pg_catalog.current_setting('block_size') AS size").fetch().use { cursor ->
      if (toast !in 128..(cursor.get<String>("size").toInt() - 32)) {
        throw illegalArg("TOAST tuple target must be in 128..block_size-32")
      }
    }
  }

  /**
   * Resolves a relation without narrowing an unsigned PostgreSQL OID.
   * @since 3.0
   */
  private fun oid(conn: PgConnection, schema: String, table: String): Long? {
    conn.execute("SELECT c.oid::text AS oid FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = $1 AND c.relname = $2",
      arrayOf(schema, table)).use { cursor ->
      return if (cursor.next()) cursor.get<String>("oid").toLong() else null
    }
  }

  /**
   * Serializes a collection, recursively locks descendants (optionally NOWAIT) and checks the mandatory root contract.
   * @since 3.0
   */
  private fun lockRoot(conn: PgConnection, schema: String, table: String, nowait: Boolean = false): Long {
    lockCollection(conn, schema, table)
    val root = oid(conn, schema, table) ?: throw illegalState("Partition root $schema.$table is missing")
    // Without ONLY, PostgreSQL locks all existing descendants as well.
    conn.execute("LOCK TABLE ${quoteIdent(schema)}.${quoteIdent(table)} IN ACCESS EXCLUSIVE MODE${if (nowait) " NOWAIT" else ""}").close()
    if (oid(conn, schema, table) != root) throw illegalState("Partition root $schema.$table changed while acquiring its lock")
    if (!check(conn, """
WITH keys AS (
  SELECT
    (SELECT attnum FROM pg_catalog.pg_attribute WHERE attrelid = $1::text::oid AND attname = 'fn' AND atttypid = 'pg_catalog.int8'::pg_catalog.regtype AND attnotnull AND NOT attisdropped AND attgenerated = '') AS fn,
    (SELECT attnum FROM pg_catalog.pg_attribute WHERE attrelid = $1::text::oid AND attname = 'nv' AND atttypid = 'pg_catalog.int8'::pg_catalog.regtype AND attnotnull AND NOT attisdropped AND attgenerated = '') AS nv,
    (SELECT attnum FROM pg_catalog.pg_attribute WHERE attrelid = $1::text::oid AND attname = 'version' AND atttypid = 'pg_catalog.int8'::pg_catalog.regtype AND attnotnull AND NOT attisdropped AND attgenerated = '') AS version
)
SELECT EXISTS (
  SELECT 1 FROM pg_catalog.pg_class WHERE oid = $1::text::oid AND relkind = 'p' AND relpersistence = 'p' AND NOT relispartition
) AND pg_catalog.pg_get_partkeydef($1::text::oid) = 'RANGE (nv)'
AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid = $1::text::oid)
AND fn IS NOT NULL AND nv IS NOT NULL AND version IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint WHERE conrelid = $1::text::oid AND contype = 'c' AND (NOT convalidated OR connoinherit))
AND EXISTS (
  SELECT 1 FROM pg_catalog.pg_index i JOIN pg_catalog.pg_constraint c ON c.conindid = i.indexrelid
  WHERE i.indrelid = $1::text::oid AND c.conrelid = i.indrelid AND c.contype = 'p' AND NOT c.condeferrable
    AND i.indisvalid AND i.indisready AND i.indnkeyatts = 3 AND i.indexprs IS NULL AND i.indpred IS NULL
    AND ARRAY[i.indkey[0], i.indkey[1], i.indkey[2]] = ARRAY[keys.nv, keys.fn, keys.version]
) AND EXISTS (
  SELECT 1 FROM pg_catalog.pg_index i WHERE i.indrelid = $1::text::oid
    AND i.indisunique AND i.indisvalid AND i.indisready AND i.indimmediate AND i.indnkeyatts = 2 AND i.indnatts = 3
    AND i.indexprs IS NULL AND i.indpred IS NULL
    AND ARRAY[i.indkey[0], i.indkey[1], i.indkey[2]] = ARRAY[keys.fn, keys.nv, keys.version]
) AS ok FROM keys
""", arrayOf(root.toString()))) {
      throw illegalState("Root $schema.$table must be permanent standalone RANGE (nv) with bigint NOT NULL fn/nv/version, inheritable validated checks, PRIMARY KEY (nv, fn, version) and UNIQUE (fn, nv) INCLUDE (version)")
    }
    return root
  }

  /**
   * Reads registry values through portable text representations for numeric results.
   * @since 3.0
   */
  private fun registered(conn: PgConnection, schema: String, table: String): Config? {
    conn.execute("""
SELECT "root_oid"::text AS root, "shift"::text AS shift, "partitions"::text AS partitions,
  "toast_tuple_target"::text AS toast, "head_fillfactor"::text AS head_fill,
  "history_fillfactor"::text AS history_fill, "tablespace_name" AS space
FROM $registry WHERE "schema_name" = $1 AND "table_name" = $2
""", arrayOf(schema, table)).use { cursor ->
      if (!cursor.next()) return null
      return Config(cursor.get<String>("root").toLong(), cursor.get<String>("shift").toInt(),
        cursor.get<String>("partitions").toInt(), cursor.get<String>("toast").toInt(),
        cursor.get<String>("head_fill").toInt(), cursor.get<String>("history_fill").toInt(), cursor["space"])
    }
  }

  /**
   * Resolves an explicit physical tablespace, rejecting unsupported shared storage.
   * @since 3.0
   */
  private fun resolveTablespace(conn: PgConnection, root: Long, requested: String?): String {
    conn.execute("""
SELECT t.spcname AS space FROM pg_catalog.pg_tablespace t
WHERE t.oid = CASE WHEN $2::text IS NULL THEN (
  SELECT CASE WHEN c.reltablespace = 0 THEN d.dattablespace ELSE c.reltablespace END
  FROM pg_catalog.pg_class c JOIN pg_catalog.pg_database d ON d.datname = pg_catalog.current_database()
  WHERE c.oid = $1::text::oid
) ELSE (SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = $2::text) END
AND t.spcname <> 'pg_global'
""", arrayOf(root.toString(), requested)).use { cursor ->
      if (!cursor.next()) throw illegalArg("Missing or unsupported tablespace $requested")
      return cursor["space"]
    }
  }

  /**
   * Evaluates a catalog predicate, treating SQL NULL as a failed contract.
   * @since 3.0
   */
  private fun check(conn: PgConnection, sql: String, args: Array<Any?>): Boolean {
    conn.execute(sql, args).fetch().use { cursor -> return cursor.column("ok") == true }
  }

  /**
   * Snapshots child names before further SQL so no internal cursor spans DDL.
   * @since 3.0
   */
  private fun children(conn: PgConnection, parent: Long): List<Pair<String, String>> {
    val result = ArrayList<Pair<String, String>>()
    conn.execute("""
SELECT n.nspname AS schema, c.relname AS name FROM pg_catalog.pg_inherits i
JOIN pg_catalog.pg_class c ON c.oid = i.inhrelid JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE i.inhparent = $1::text::oid ORDER BY c.relname
""", arrayOf(parent.toString())).use { cursor ->
      while (cursor.next()) result.add(Pair(cursor["schema"], cursor["name"]))
    }
    return result
  }

  /**
   * Validates topology, payload, checks and native index attachment without repairing changed objects.
   * @since 3.0
   */
  private fun child(conn: PgConnection, schema: String, table: String, parent: Long, bound: String, partitioned: Boolean): Long {
    val child = oid(conn, schema, table) ?: throw illegalState("Missing partition $schema.$table")
    if (!check(conn, """
SELECT EXISTS (
  SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_inherits i ON i.inhrelid = c.oid
  WHERE c.oid = $1::text::oid AND i.inhparent = $2::text::oid AND NOT i.inhdetachpending
    AND c.relispartition AND c.relpersistence = 'p'
    AND c.relkind = CASE WHEN $4::boolean THEN 'p'::pg_catalog."char" ELSE 'r'::pg_catalog."char" END
    AND pg_catalog.pg_get_expr(c.relpartbound, c.oid) = $3
    AND (NOT $4::boolean OR (pg_catalog.pg_get_partkeydef(c.oid) = 'RANGE (fn)' AND c.reloptions IS NULL))
) AND (SELECT pg_catalog.count(*) FROM pg_catalog.pg_inherits WHERE inhrelid = $1::text::oid) = 1
AND ($4::boolean OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits WHERE inhparent = $1::text::oid))
AND NOT EXISTS (
  (SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated, attstorage, attcompression
    FROM pg_catalog.pg_attribute WHERE attrelid = $1::text::oid AND attnum > 0 AND NOT attisdropped
   EXCEPT
   SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated, attstorage, attcompression
    FROM pg_catalog.pg_attribute WHERE attrelid = $2::text::oid AND attnum > 0 AND NOT attisdropped)
  UNION ALL
  (SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated, attstorage, attcompression
    FROM pg_catalog.pg_attribute WHERE attrelid = $2::text::oid AND attnum > 0 AND NOT attisdropped
   EXCEPT
   SELECT attname, atttypid, atttypmod, attcollation, attnotnull, attgenerated, attstorage, attcompression
    FROM pg_catalog.pg_attribute WHERE attrelid = $1::text::oid AND attnum > 0 AND NOT attisdropped)
)
AND NOT EXISTS (
  SELECT 1 FROM pg_catalog.pg_constraint p WHERE p.conrelid = $2::text::oid AND p.contype = 'c'
    AND (NOT p.convalidated OR p.connoinherit OR NOT EXISTS (
      SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid = $1::text::oid AND c.contype = 'c'
        AND c.conname = p.conname AND c.convalidated AND NOT c.connoinherit AND c.coninhcount > 0
        AND pg_catalog.pg_get_expr(c.conbin, c.conrelid) = pg_catalog.pg_get_expr(p.conbin, p.conrelid)
    ))
)
AND NOT EXISTS (
  SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid = $1::text::oid AND c.contype = 'c'
    AND c.coninhcount = 0 AND NOT ($3 = 'DEFAULT' AND c.convalidated AND NOT c.connoinherit
      AND pg_catalog.pg_get_expr(c.conbin, c.conrelid) = $5)
)
AND ($3 <> 'DEFAULT' OR EXISTS (
  SELECT 1 FROM pg_catalog.pg_constraint WHERE conrelid = $1::text::oid AND contype = 'c'
    AND convalidated AND NOT connoinherit AND pg_catalog.pg_get_expr(conbin, conrelid) = $5
))
AND NOT EXISTS (
  SELECT 1 FROM pg_catalog.pg_index p WHERE p.indrelid = $2::text::oid
    AND (NOT p.indisvalid OR NOT p.indisready OR NOT EXISTS (
      SELECT 1 FROM pg_catalog.pg_inherits h JOIN pg_catalog.pg_index c ON c.indexrelid = h.inhrelid
      WHERE h.inhparent = p.indexrelid AND c.indrelid = $1::text::oid AND NOT h.inhdetachpending
        AND c.indisvalid AND c.indisready
    ))
) AS ok
""", arrayOf(child.toString(), parent.toString(), bound, partitioned, HEAD_CHECK))) {
      throw illegalState("Changed partition topology, payload, checks or inherited indexes on $schema.$table (expected $bound)")
    }
    return child
  }

  /**
   * Checks explicit physical leaf options and the actual (rather than session-default) tablespace.
   * @since 3.0
   */
  private fun storage(conn: PgConnection, schema: String, table: String, leaf: Long, config: Config, fill: Int) {
    if (!check(conn, """
SELECT EXISTS (
  SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_database d ON d.datname = pg_catalog.current_database()
  JOIN pg_catalog.pg_tablespace t ON t.oid = CASE WHEN c.reltablespace = 0 THEN d.dattablespace ELSE c.reltablespace END
  WHERE c.oid = $1::text::oid AND t.spcname = $2 AND t.spcname <> 'pg_global'
    AND c.reloptions @> ARRAY['fillfactor=' || $3::text, 'toast_tuple_target=' || $4::text]
) AS ok
""", arrayOf(leaf.toString(), config.space, fill.toString(), config.toast.toString()))) {
      throw illegalState("Changed leaf storage on $schema.$table")
    }
  }

  /**
   * Creates an absent child with native inheritance; all interpolated values are quoted identifiers or validated integers/bounds because PostgreSQL utility statements do not accept value bindings here.
   * @since 3.0
   */
  private fun create(
    conn: PgConnection, schema: String, table: String, parent: String, ddlBound: String,
    isHead: Boolean, partitioned: Boolean, config: Config, fill: Int
  ) {
    if (oid(conn, schema, table) != null) return
    val constraint = if (isHead) "(CONSTRAINT ${quoteIdent(table + "\$check")} CHECK (\"nv\" = $HEAD::bigint))" else ""
    val options = if (partitioned) "PARTITION BY RANGE (\"fn\")" else
      "WITH (fillfactor = $fill, toast_tuple_target = ${config.toast}) TABLESPACE ${quoteIdent(config.space)}"
    conn.execute("CREATE TABLE ${quoteIdent(schema)}.${quoteIdent(table)} PARTITION OF ${quoteIdent(schema)}.${quoteIdent(parent)} $constraint $ddlBound $options").close()
  }

  /**
   * Performs a validation-only pass before creating coverage and repairing all retained children.
   * @since 3.0
   */
  private fun provision(conn: PgConnection, schema: String, table: String, config: Config, version: Long?) {
    if (config.shift !in 12..41) throw illegalState("Invalid registered partition shift")
    resolveTablespace(conn, config.root, config.space)
    val width = 1L shl config.shift
    for (pass in 0..1) {
      if (pass == 1) {
        create(conn, schema, table + "\$head", table, "DEFAULT", true, config.partitions > 1, config, config.headFill)
        child(conn, schema, table + "\$head", config.root, "DEFAULT", config.partitions > 1)
        if (version != null) {
          val first = version shr config.shift
          for (slot in 0..3) {
            val start = (first + slot) * width
            if (start >= HEAD) continue
            val end = minOf(start + width, HEAD)
            val childName = table + "$" + (first + slot)
            create(conn, schema, childName, table, "FOR VALUES FROM ($start) TO ($end)", false, config.partitions > 1, config, config.historyFill)
            child(conn, schema, childName, config.root, "FOR VALUES FROM ('$start') TO ('$end')", config.partitions > 1)
          }
        }
      }
      for ((childSchema, childName) in children(conn, config.root)) {
        if (childSchema != schema) throw illegalState("Unexpected cross-schema child $childSchema.$childName")
        val isHead = childName == table + "\$head"
        var bound = "DEFAULT"
        val fill = if (isHead) config.headFill else config.historyFill
        if (!isHead) {
          val prefix = table + "$"
          if (version == null || !childName.startsWith(prefix)) throw illegalState("Unexpected nv child $childName")
          val suffix = childName.substring(prefix.length)
          val number = suffix.toLongOrNull()
          if (number == null || number < 0 || suffix != number.toString() || number > ((HEAD - 1) shr config.shift)) {
            throw illegalState("Unexpected or out-of-domain nv child $childName")
          }
          val start = number * width
          val end = minOf(start + width, HEAD)
          bound = "FOR VALUES FROM ('$start') TO ('$end')"
        }
        val child = child(conn, schema, childName, config.root, bound, config.partitions > 1)
        if (config.partitions == 1) {
          storage(conn, schema, childName, child, config, fill)
          continue
        }
        val expected = HashSet<String>()
        for (slot in 0 until config.partitions) expected.add(childName + "$" + slot)
        for ((leafSchema, leafName) in children(conn, child)) {
          if (leafSchema != schema || leafName !in expected) throw illegalState("Unexpected fn leaf $leafSchema.$leafName")
        }
        for (slot in 0 until config.partitions) {
          val low = if (slot == 0) "MINVALUE" else Id.partitionIndexMin(slot, config.partitions).toString()
          val high = if (slot == config.partitions - 1) "MAXVALUE" else Id.partitionIndexMin(slot + 1, config.partitions).toString()
          val expectedLow = if (slot == 0) low else "'$low'"
          val expectedHigh = if (slot == config.partitions - 1) high else "'$high'"
          val leafName = childName + "$" + slot
          if (pass == 0 && oid(conn, schema, leafName) == null) continue
          if (pass == 1) create(conn, schema, leafName, childName, "FOR VALUES FROM ($low) TO ($high)", false, false, config, fill)
          val leaf = child(conn, schema, leafName, child, "FOR VALUES FROM ($expectedLow) TO ($expectedHigh)", false)
          storage(conn, schema, leafName, leaf, config, fill)
        }
      }
    }
  }
}
