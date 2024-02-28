@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * To be called once per storage to initialize a storage. This is normally only done from the Java code that invokes
 * the _JvmPlv8Env.install(conn,version,schema,storageId)_ method. The purpose of this method is to create all the
 * tables that are essentially needed by the [NakshaSession], so the table for the transactions, the table for the
 * global dictionaries and the table for the collection management.
 */
@JsExport
object Static {
    @JvmStatic
    fun initStorage(sql: IPlv8Sql, schema: String) {
        val schemaOid: Int = asMap(asArray(sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))[0])["oid"]!!
        val schemaIdentQuoted = sql.quoteIdent(schema)
        val schemaJsQuoted = Jb.env.stringify(schema)
        val query = """
SET SESSION search_path TO $schemaIdentQuoted, public, topology;
CREATE TABLE IF NOT EXISTS naksha_global (
    id          text        PRIMARY KEY NOT NULL,
    data        bytea       NOT NULL
);
CREATE SEQUENCE IF NOT EXISTS naksha_txn_seq AS int8;
CREATE TABLE IF NOT EXISTS naksha_txn (
    txn         int8         PRIMARY KEY NOT NULL,
    ts          timestamptz  NOT NULL DEFAULT transaction_timestamp(),
    xact_id     xid8         NOT NULL DEFAULT pg_current_xact_id(),
    app_id      text         COMPRESSION lz4 NOT NULL,
    author      text         COMPRESSION lz4 NOT NULL,
    seq_id      int8,
    seq_ts      timestamptz,
    version     int8,
    details     bytea       COMPRESSION lz4,
    attachment  bytea       COMPRESSION lz4
) PARTITION BY RANGE (txn);
CREATE INDEX IF NOT EXISTS naksha_txn_ts_idx ON naksha_txn USING btree ("ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_app_id_ts_idx ON naksha_txn USING btree ("app_id" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_author_ts_idx ON naksha_txn USING btree ("author" ASC, "ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_id_idx ON naksha_txn USING btree ("seq_id" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_seq_ts_idx ON naksha_txn USING btree ("seq_ts" ASC);
CREATE INDEX IF NOT EXISTS naksha_txn_version_idx ON naksha_txn USING btree ("version" ASC);
-- Create the table for the collection management as normal collection.
-- This is a chicken-egg problem:
-- We need to create this collection before we can create collections, because
-- when a collection is created, this table will be checked. So we need to ensure
-- that this collection is created without this check, but as a valid collection.
-- Note: We do not allow deletion of naksha tables!
do $$
    var commonjs2_init = plv8.find_function("commonjs2_init");
    commonjs2_init();
    let naksha = require("naksha");
    naksha.JsPlv8Env.Companion.initialize();
    let sql = new naksha.JsPlv8Sql();
    if (!naksha.Static.tableExists(sql, "naksha_collections", $schemaOid)) {
        naksha.Static.collectionCreate(sql, $schemaJsQuoted, $schemaOid, "naksha_collections", false, false);
    }
$$ LANGUAGE 'plv8';
"""
        sql.execute(query)
    }

    /**
     * Array to create a pseudo GeoHash, which is BASE-32 encoded.
     */
    @JvmStatic
    internal val BASE32 = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z')

    /**
     * Can be set to true, to enable stack-trace reporting to _elog(INFO)_.
     */
    @JvmStatic
    var PRINT_STACK_TRACES = false

    /**
     * Array to fasten partition id.
     */
    @JvmStatic
    val PARTITION_ID = Array(256) {
        if (it < 10) "00$it" else if (it < 100) "0$it" else "$it"
    }

    /**
     * The lock-id for the transaction number sequence.
     */
    @JvmStatic
    val TXN_LOCK_ID = lockId("naksha_txn_seq")

    /**
     * Returns the lock-id for the given name.
     * @param name The name to query the lock-id for.
     * @return The 64-bit FNV1a hash.
     */
    @JvmStatic
    fun lockId(name: String): BigInt64 = Fnv1a64.string(Fnv1a64.start(), name)

    /**
     * Calculate the pseudo geo-reference-id from the given feature id.
     * @param id The feature id.
     * @return The pseudo geo-reference-id.
     */
    @JvmStatic
    fun gridFromId(id: String): String {
        val sb = StringBuilder()
        var hash = Fnv1a32.string(Fnv1a32.start(), id)
        var i = 0
        sb.append(BASE32[id[0].code and 31])
        while (i++ < 6) {
            val b32 = hash and 31
            sb.append(BASE32[b32])
            hash = hash ushr 5
        }
        return sb.toString()
    }

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and 255.
     */
    @JvmStatic
    fun partitionNumber(id: String): Int = Fnv1a32.string(Fnv1a32.start(), id) and 0xff

    /**
     * Returns the partition id as three digit string.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as three digit string.
     */
    @JvmStatic
    fun partitionNameForId(id: String): String = PARTITION_ID[partitionNumber(id)]

    /**
     * Tests if specific database table (in the Naksha session schema) exists already
     * @param sql The SQL API.
     * @param name The table name.
     * @param schemaOid The object-id of the schema to look into.
     * @return _true_ if a table with this name exists; _false_ otherwise.
     */
    @JvmStatic
    fun tableExists(sql: IPlv8Sql, name: String, schemaOid: Int): Boolean {
        val rows = asArray(sql.execute("SELECT oid FROM pg_class WHERE relname = $1 AND relnamespace = $2", arrayOf(name, schemaOid)))
        return rows.isNotEmpty()
    }

    /**
     * Optimize the table storage configuration.
     * @param sql The SQL API.
     * @param tableName The table name.
     * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
     */
    @JvmStatic
    private fun collectionOptimizeTable(sql: IPlv8Sql, tableName: String, history: Boolean) {
        val quotedTableName = sql.quoteIdent(tableName)
        var query = """ALTER TABLE $quotedTableName
ALTER COLUMN feature SET STORAGE MAIN,
ALTER COLUMN geo SET STORAGE MAIN,
ALTER COLUMN tags SET STORAGE MAIN,
ALTER COLUMN author SET STORAGE PLAIN,
ALTER COLUMN app_id SET STORAGE PLAIN,
ALTER COLUMN geo_grid SET STORAGE PLAIN,
ALTER COLUMN id SET STORAGE PLAIN,
SET (toast_tuple_target=8160"""
        query += if (history) ",fillfactor=100,autovacuum_enabled=OFF,toast.autovacuum_enabled=OFF"
        else """,fillfactor=50
-- Specifies the minimum number of updated or deleted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_threshold=10000, toast.autovacuum_vacuum_threshold=10000
-- Specifies the number of inserted tuples needed to trigger a VACUUM in any one table.
,autovacuum_vacuum_insert_threshold=10000, toast.autovacuum_vacuum_insert_threshold=10000
-- Specifies a fraction of the table size to add to autovacuum_vacuum_threshold when deciding whether to trigger a VACUUM.
,autovacuum_vacuum_scale_factor=0.1, toast.autovacuum_vacuum_scale_factor=0.1
-- Specifies a fraction of the table size to add to autovacuum_analyze_threshold when deciding whether to trigger an ANALYZE.
,autovacuum_analyze_threshold=10000, autovacuum_analyze_scale_factor=0.1"""
        query += ")"
        sql.execute(query)
    }

    /**
     * Creates all the indices needed for a collection.
     * @param sql The SQL API.
     * @param tableName The table name.
     * @param spGist If SP-GIST index should be used, which is better only for point geometry.
     * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
     */
    @JvmStatic
    private fun collectionAddIndices(sql: IPlv8Sql, tableName: String, spGist: Boolean, history: Boolean) {
        val fillFactor = if (history) "100" else "50"
        // https://www.postgresql.org/docs/current/gin-tips.html
        val geoIndexType = if (spGist) "sp-gist" else "gist"
        val unique = if (history) "" else "UNIQUE "

        // quoted table name
        val qtn = sql.quoteIdent(tableName)
        // quoted index name
        var qin = sql.quoteIdent("${tableName}_id_idx")
        var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(id COLLATE "C" text_pattern_ops DESC) WITH (fillfactor=$fillFactor);
"""

        // txn, uid
        qin = sql.quoteIdent("${tableName}_txn_uid_idx")
        query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(txn DESC, uid DESC) WITH (fillfactor=$fillFactor);
"""

        // geo
        qin = sql.quoteIdent("${tableName}_geo_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndexType
(naksha_geometry(geo_type,geo), txn) WITH (buffering=ON,fillfactor=$fillFactor);
"""

        // tags
        qin = sql.quoteIdent("${tableName}_tags_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin
(tags_to_jsonb(tags), txn) WITH (fastupdate=ON,gin_pending_list_limit=32768);
"""

        // grid
        qin = sql.quoteIdent("${tableName}_grid_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(geo_grid COLLATE "C" DESC, txn DESC) WITH (fillfactor=$fillFactor);
"""

        // app_id
        qin = sql.quoteIdent("${tableName}_app_id_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(app_id COLLATE "C" DESC, updated_at DESC, txn DESC) WITH (fillfactor=$fillFactor);
"""

        // author
        qin = sql.quoteIdent("${tableName}_author_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(author COLLATE "C" DESC, author_ts DESC, txn DESC) WITH (fillfactor=$fillFactor);
"""

        sql.execute(query)
    }

    /**
     * Low level function to create a (optionally partitioned) collection table set.
     * @param sql The SQL API.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     * @param id The collection identifier.
     * @param spGist If SP-GIST index should be used, which is better only for point geometry.
     * @param partition If the collection should be partitioned.
     */
    @JvmStatic
    fun collectionCreate(sql: IPlv8Sql, schema: String, schemaOid: Int, id: String, spGist: Boolean, partition: Boolean) {
        // We store geometry as TWKB, see:
        // http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html
        // TODO: Optimize this by generating a complete query as one string and then execute it at ones!
        // TODO: We need Postgres 16, then we can create the table with STORAGE MAIN!
        val CREATE_TABLE = """CREATE TABLE {table} (
    txn_next    int8 NOT NULL CHECK(txn_next {condition}),
    txn         int8 NOT NULL,
    uid         int4 NOT NULL,
    version     int4 NOT NULL,
    created_at  int8 NOT NULL, -- to_timestamp(created_at / 1000)
    updated_at  int8 NOT NULL, -- to_timestamp(updated_at / 1000)
    author_ts   int8 NOT NULL, -- to_timestamp(author_ts / 1000)
    action      int2 NOT NULL,
    geo_type    int2 NOT NULL,
    puid        int4,
    ptxn        int8,
    author      text NOT NULL,
    app_id      text NOT NULL,
    geo_grid    text NOT NULL,
    id          text COLLATE "C" NOT NULL,
    tags        bytea COMPRESSION lz4,
    geo         bytea COMPRESSION lz4,
    feature     bytea COMPRESSION lz4 NOT NULL
) """
        var query: String

        // HEAD
        val headNameQuoted = sql.quoteIdent(id)
        query = CREATE_TABLE.replace("{table}", headNameQuoted)
        query = query.replace("{condition}", "= 0")
        if (!partition) {
            sql.execute(query)
            collectionOptimizeTable(sql, id, false)
            collectionAddIndices(sql, id, spGist, false)
        } else {
            query += "PARTITION BY RANGE (naksha_partition_number(id))"
            sql.execute(query)
            var i = 0
            while (i < 256) {
                val partName = id + "_p" + PARTITION_ID[i]
                val partNameQuoted = sql.quoteIdent(partName)
                query = "CREATE TABLE $partNameQuoted PARTITION OF $headNameQuoted FOR VALUES FROM ($i) "
                i++
                query += "TO ($i)"
                sql.execute(query)
                collectionOptimizeTable(sql, partName, false)
                collectionAddIndices(sql, partName, spGist, false)
            }
        }

        // Create sequence.
        val sequenceName = id + "_uid_seq";
        val sequenceNameQuoted = sql.quoteIdent(sequenceName)
        query = "CREATE SEQUENCE IF NOT EXISTS $sequenceNameQuoted AS int8 START WITH 1 CACHE 100 OWNED BY ${headNameQuoted}.uid"
        sql.execute(query)

        // DEL.
        val delName = id + "_del"
        val delNameQuoted = sql.quoteIdent(delName)
        query = CREATE_TABLE.replace("{table}", delNameQuoted)
        query = query.replace("{condition}", "= 0")
        sql.execute(query)
        collectionOptimizeTable(sql, delName, false)
        collectionAddIndices(sql, delName, spGist, false)

        // META.
        val metaName = id + "_meta"
        val metaNameQuoted = sql.quoteIdent(metaName)
        query = CREATE_TABLE.replace("{table}", metaNameQuoted)
        query = query.replace("{condition}", "= 0")
        sql.execute(query)
        collectionOptimizeTable(sql, metaName, false)
        collectionAddIndices(sql, metaName, spGist, false)

        // HISTORY.
        val hstName = id + "_hst"
        val hstNameQuoted = sql.quoteIdent(hstName)
        query = CREATE_TABLE.replace("{table}", hstNameQuoted)
        query = query.replace("{condition}", "> 0")
        query += "PARTITION BY RANGE (txn_next)"
        sql.execute(query)

        // Optimizations are done on the history partitions!
        collectionAttachTriggers(sql, id, schema, schemaOid)
    }

    /**
     * Add the before and after triggers.
     * @param sql The SQL API.
     * @param id The collection identifier.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     */
    @JvmStatic
    private fun collectionAttachTriggers(sql: IPlv8Sql, id: String, schema: String, schemaOid: Int) {
        var triggerName = id + "_before"
        var rows = asArray(sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid)))
        if (rows.isEmpty()) {
            val schemaQuoted = sql.quoteIdent(schema)
            val tableNameQuoted = sql.quoteIdent(id)
            val triggerNameQuoted = sql.quoteIdent(triggerName)
            sql.execute("""CREATE TRIGGER $triggerNameQuoted BEFORE INSERT OR UPDATE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_before();""")
        }

        triggerName = id + "_after"
        rows = asArray(sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid)))
        if (rows.isEmpty()) {
            val schemaQuoted = sql.quoteIdent(schema)
            val tableNameQuoted = sql.quoteIdent(id)
            val triggerNameQuoted = sql.quoteIdent(triggerName)
            sql.execute("""CREATE TRIGGER $triggerNameQuoted AFTER INSERT OR UPDATE OR DELETE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_after();""")
        }
    }

    /**
     * Deletes the collection with the given identifier.
     * @param sql The SQL API.
     * @param id The collection identifier.
     */
    @JvmStatic
    fun collectionDrop(sql: IPlv8Sql, id: String) {
        require(!id.startsWith("naksha_"))
        val headName = sql.quoteIdent(id)
        val delName = sql.quoteIdent(id + "_del")
        val metaName = sql.quoteIdent(id + "_meta")
        val hstName = sql.quoteIdent(id + "_hst")
        sql.execute("""DROP TABLE IF EXISTS $headName CASCADE;
DROP TABLE IF EXISTS $delName CASCADE;
DROP TABLE IF EXISTS $metaName CASCADE;
DROP TABLE IF EXISTS $hstName CASCADE;""")
    }
}