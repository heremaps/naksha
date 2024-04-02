@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.Fnv1a32
import com.here.naksha.lib.jbon.Fnv1a64
import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.NakshaTxn
import com.here.naksha.lib.jbon.asArray
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.get
import com.here.naksha.lib.jbon.getAny
import com.here.naksha.lib.jbon.newMap
import com.here.naksha.lib.jbon.put
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

    /**
     * Config for naksha_collection
     */
    @JvmStatic
    internal val nakshaCollectionConfig = newMap()

    init {
        nakshaCollectionConfig.put(NKC_PARTITION, false)
        nakshaCollectionConfig.put(NKC_AUTO_PURGE, false)
        nakshaCollectionConfig.put(NKC_POINTS_ONLY, false)
        nakshaCollectionConfig.put(NKC_DISABLE_HISTORY, false)
    }

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
        naksha.Static.collectionCreate(sql, null, $schemaJsQuoted, $schemaOid, "naksha_collections", false, false);
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
     * Used to debug.
     */
    @JvmStatic
    val DEBUG = false

    /**
     * The constant for the GIST geo-index.
     */
    @JvmStatic
    val GEO_INDEX_GIST = false // "gist"

    /**
     * The constant for the SP-GIST geo-index.
     */
    @JvmStatic
    val GEO_INDEX_SP_GIST = true // "sp-gist"

    /**
     * The constant for the default geo-index (may change over time).
     */
    @JvmStatic
    val GEO_INDEX_DEFAULT = GEO_INDEX_GIST

    /**
     * Can be set to true, to enable stack-trace reporting to _elog(INFO)_.
     */
    @JvmStatic
    var PRINT_STACK_TRACES = false

    /**
     * Array to fasten partition id.
     */
    @JvmStatic
    val PARTITION_ID = Array(8) {
        "$it"
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
        // = Fnv1a32.string(Fnv1a32.start(), id) and 0x7fff_ffff
        val sb = StringBuilder()
        var hash = Fnv1a32.string(Fnv1a32.start(), id)
        var i = 0
        sb.append(Static.BASE32[id[0].code and 31])
        while (i++ < 6) {
            val b32 = hash and 31
            sb.append(Static.BASE32[b32])
            hash = hash ushr 5
        }
        hash = Fnv1a32.stringReverse(Fnv1a32.start(), id)
        i = 0
        sb.append(Static.BASE32[id[0].code and 31])
        while (i++ < 6) {
            val b32 = hash and 31
            sb.append(Static.BASE32[b32])
            hash = hash ushr 5
        }
        return sb.toString()
    }

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and partitionCount.
     */
    @JvmStatic
    fun partitionNumber(id: String): Int = Fnv1a32.string(Fnv1a32.start(), id) and 0x7

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
     * @param tablespaceQueryPart to create indices in, should be an empty string or full query part: " TABLESPACE tablespace_name"
     */
    @JvmStatic
    private fun collectionAddIndices(sql: IPlv8Sql, tableName: String, spGist: Boolean, history: Boolean, tablespaceQueryPart: String) {
        val fillFactor = if (history) "100" else "50"
        // https://www.postgresql.org/docs/current/gin-tips.html
        val geoIndexType = if (spGist) "sp-gist" else "gist"
        val unique = if (history) "" else "UNIQUE "

        // quoted table name
        val qtn = sql.quoteIdent(tableName)
        // quoted index name
        var qin = sql.quoteIdent("${tableName}_id_idx")
        var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(id COLLATE "C" text_pattern_ops DESC) WITH (fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        // txn, uid
        qin = sql.quoteIdent("${tableName}_txn_uid_idx")
        query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(txn DESC, uid DESC) WITH (fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        // geo
        qin = sql.quoteIdent("${tableName}_geo_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndexType
(naksha_geometry(geo_type,geo), txn) WITH (buffering=ON,fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        // tags
        qin = sql.quoteIdent("${tableName}_tags_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin
(tags_to_jsonb(tags), txn) WITH (fastupdate=ON,gin_pending_list_limit=32768) $tablespaceQueryPart;
"""

        // grid
        qin = sql.quoteIdent("${tableName}_grid_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(geo_grid COLLATE "C" text_pattern_ops DESC, txn DESC) WITH (fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        // app_id
        qin = sql.quoteIdent("${tableName}_app_id_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(app_id COLLATE "C" DESC, updated_at DESC, txn DESC) WITH (fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        // author
        qin = sql.quoteIdent("${tableName}_author_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(author COLLATE "C" DESC, author_ts DESC, txn DESC) WITH (fillfactor=$fillFactor) $tablespaceQueryPart;
"""

        sql.execute(query)
    }

    /**
     * Low level function to create a (optionally partitioned) collection table set.
     * @param sql The SQL API.
     * @param temporary should be treated as temporary or not.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     * @param id The collection identifier.
     * @param geoIndex If SP-GIST index should be used, which is better only for point geometry.
     * @param partition If the collection should be partitioned.
     */
    @JvmStatic
    fun collectionCreate(sql: IPlv8Sql, temporary: Boolean, schema: String, schemaOid: Int, id: String, geoIndex: Boolean, partition: Boolean) {
        val tableConfig = TableConfig(temporary)
        // We store geometry as TWKB, see:
        // http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html
        // TODO: Optimize this by generating a complete query as one string and then execute it at ones!
        // TODO: We need Postgres 16, then we can create the table with STORAGE MAIN!
        val CREATE_TABLE = if (!DEBUG) """CREATE ${tableConfig.unlogged()} TABLE {table} (
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
) """ else """CREATE ${tableConfig.unlogged()} TABLE {table} (
    txn_next    int8,
    txn         int8,
    uid         int4,
    version     int4,
    created_at  int8, -- to_timestamp(created_at / 1000)
    updated_at  int8, -- to_timestamp(updated_at / 1000)
    author_ts   int8, -- to_timestamp(author_ts / 1000)
    action      int2,
    geo_type    int2,
    puid        int4,
    ptxn        int8,
    author      text,
    app_id      text,
    geo_grid    text,
    id          text COLLATE "C",
    tags        bytea COMPRESSION lz4,
    geo         bytea COMPRESSION lz4,
    feature     bytea COMPRESSION lz4
) """
        var query: String

        // HEAD
        val headNameQuoted = sql.quoteIdent(id)
        query = CREATE_TABLE.replace("{table}", headNameQuoted)
        query = query.replace("{condition}", "= 0")

        if (!partition) {
            query += tableConfig.tablespaceQueryPart()
            sql.execute(query)
            collectionOptimizeTable(sql, id, false)
            collectionAddIndices(sql, id, geoIndex, false, tableConfig.tablespaceQueryPart())
        } else {
            query += "PARTITION BY RANGE (naksha_partition_number(id))"
            query += tableConfig.tablespaceQueryPart()
            sql.execute(query)
            var i = 0
            while (i < PARTITION_COUNT) {
                val partName = id + "_p" + PARTITION_ID[i]
                val partNameQuoted = sql.quoteIdent(partName)
                query = "CREATE ${tableConfig.unlogged()} TABLE $partNameQuoted PARTITION OF $headNameQuoted FOR VALUES FROM ($i) "
                query += "TO (${i+1})"
                query += tableConfig.tablespaceQueryPart()
                sql.execute(query)
                collectionOptimizeTable(sql, partName, false)
                collectionAddIndices(sql, partName, geoIndex, false, tableConfig.tablespaceQueryPart())
                i++
            }
        }
        if (!DEBUG) collectionAttachTriggers(sql, id, schema, schemaOid)

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
        query += tableConfig.tablespaceQueryPart()
        sql.execute(query)
        collectionOptimizeTable(sql, delName, false)
        collectionAddIndices(sql, delName, geoIndex, false, tableConfig.tablespaceQueryPart())

        // META.
        val metaName = id + "_meta"
        val metaNameQuoted = sql.quoteIdent(metaName)
        query = CREATE_TABLE.replace("{table}", metaNameQuoted)
        query = query.replace("{condition}", "= 0")
        query += tableConfig.tablespaceQueryPart()
        sql.execute(query)
        collectionOptimizeTable(sql, metaName, false)
        collectionAddIndices(sql, metaName, geoIndex, false, tableConfig.tablespaceQueryPart())

        // HISTORY.
        val hstName = id + "_hst"
        val hstNameQuoted = sql.quoteIdent(hstName)
        query = CREATE_TABLE.replace("{table}", hstNameQuoted)
        query = query.replace("{condition}", ">= 0")
        query += "PARTITION BY RANGE (txn_next)"
        query += tableConfig.tablespaceQueryPart()
        sql.execute(query)
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

    /**
     * @param collectionId has to be pure (without _hst suffix).
     */
    @JvmStatic
    fun createHstPartition(sql: IPlv8Sql, temporary: Boolean, collectionId: String, txnNext: NakshaTxn, geoIndex: Boolean, partition: Boolean?): String {
        val hstPartName = hstPartitionNameForId(collectionId, txnNext)
        val partNameQuoted = sql.quoteIdent(hstPartName)
        val headNameQuoted = sql.quoteIdent(hstHeadNameForId(collectionId))
        val start = NakshaTxn.of(txnNext.year(), 0, 0, NakshaTxn.SEQ_MIN).value
        val end = NakshaTxn.of(txnNext.year(), 12, 31, NakshaTxn.SEQ_MAX).value
        val tableConfig = TableConfig(temporary)
        val tablespaceQueryPart = tableConfig.tablespaceQueryPart()
        var query = "CREATE ${tableConfig.unlogged()} TABLE IF NOT EXISTS $partNameQuoted PARTITION OF $headNameQuoted FOR VALUES FROM ($start) TO ($end) "
        if (partition == true) {
            query += "PARTITION BY RANGE (naksha_partition_number(id)) $tablespaceQueryPart"
            sql.execute(query)

            for (subPartition in 0..<PARTITION_COUNT) {
                createHstSubPartition(sql, hstPartName, geoIndex, subPartition, tableConfig)
            }
        } else {
            query += tablespaceQueryPart
            sql.execute(query)
            collectionOptimizeTable(sql, partNameQuoted, true)
            collectionAddIndices(sql, partNameQuoted, geoIndex, true, tablespaceQueryPart)
        }

        return hstPartName
    }

    private fun createHstSubPartition(sql: IPlv8Sql, hstPartName: String, geoIndex: Boolean, subPartition: Int, tableConfig: TableConfig) {
        val hstSubPartName = "${hstPartName}_$subPartition"
        val subPartNameQuoted = sql.quoteIdent(hstSubPartName)
        val hstNameQuoted = sql.quoteIdent(hstPartName)
        val query = "CREATE ${tableConfig.unlogged()} TABLE IF NOT EXISTS $subPartNameQuoted PARTITION OF $hstNameQuoted FOR VALUES FROM ($subPartition) TO (${subPartition + 1}) ${tableConfig.tablespaceQueryPart()};"
        sql.execute(query)
        collectionOptimizeTable(sql, hstSubPartName, true)
        collectionAddIndices(sql, hstSubPartName, geoIndex, true, tableConfig.tablespaceQueryPart())
    }

    /**
     * Returns full history partition name i.e. `foo_hst_2023_02_12` for given `foo`.
     *
     * @param collectionId head collectionId i.e `topology`
     * @param txnNext txn to retrieve suffix from
     */
    @JvmStatic
    fun hstPartitionNameForId(collectionId: String, txnNext: NakshaTxn): String = "${hstHeadNameForId(collectionId)}_${txnNext.historyPostfix()}"

    /**
     * Returns full history head name i.e. `foo_hst`.
     *
     * @param collectionId head collectionId i.e `topology`
     */
    @JvmStatic
    fun hstHeadNameForId(collectionId: String): String = "${collectionId}_hst"

    /**
     * Queries the database for the schema **oid** (object id). The result should be cached as calling this method is expensive.
     * @param sql The SQL API.
     * @param schema The name of the schema.
     * @return The object-id of the schema or _null_, if no such schema was found.
     */
    @Suppress("UNCHECKED_CAST")
    fun getSchemaOid(sql: IPlv8Sql, schema: String): Int? {
        val result = sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema))
        if (result is Array<*>) {
            val array = result as Array<Any>
            if (array.isNotEmpty()) {
                val row = asMap(array[0])
                val oid = row.getAny("oid")
                if (oid is Int) return oid
            }
        }
        return null
    }

    /**
     * Tests if the given **id** is a valid collection identifier.
     * @param id The collection identifier.
     * @return _true_ if the collection identifier is valid; _false_ otherwise.
     */
    fun isValidCollectionId(id: String?): Boolean {
        if (id.isNullOrEmpty() || "naksha" == id || id.length > 32) return false
        var i = 0
        var c = id[i++]
        // First character must be a-z
        if (c.code < 'a'.code || c.code > 'z'.code) return false
        while (i < id.length) {
            c = id[i++]
            when (c.code) {
                in 'a'.code..'z'.code -> continue
                in '0'.code..'9'.code -> continue
                '_'.code, ':'.code, '-'.code -> continue
                else -> return false
            }
        }
        return true
    }
}