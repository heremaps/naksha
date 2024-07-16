@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.*
import naksha.model.NakshaCollectionProxy
import naksha.model.NakshaCollectionProxy.Companion.DEFAULT_GEO_INDEX
import naksha.model.NakshaCollectionProxy.Companion.PARTITION_COUNT_NONE
import naksha.model.Txn
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.math.absoluteValue

/**
 * To be called once per storage to initialize a storage. This is normally only done from the Java code that invokes
 * the _JvmPlv8Env.install(conn,version,schema,storageId)_ method. The purpose of this method is to create all the
 * tables that are essentially needed by the [PgSession], so the table for the transactions, the table for the
 * global dictionaries and the table for the collection management.
 */
@JsExport
object PgStatic {
    /**
     * Quotes a string literal, so a custom string. For PostgresQL database this means to replace all single quotes
     * (`'`) with two single quotes (`''`). This encloses the string with quotation characters, when needed.
     * @param parts The literal parts to merge and quote.
     * @return The quoted literal.
     */
    fun quote_literal(vararg parts: String): String {
        val sb = StringBuilder()
        sb.append("E'")
        for (part in parts) {
            for (c in part) {
                when (c) {
                    '\'' -> sb.append('\'').append('\'')
                    '\\' -> sb.append('\\').append('\\')
                    else -> sb.append(c)
                }
            }
        }
        sb.append('\'')
        return sb.toString()
    }

    /**
     * Quotes an identifier, so a database internal name. For PostgresQL database this means to replace all double quotes
     * (`"`) with two double quotes (`""`). This encloses the string with quotation characters, when needed.
     */
    fun quote_ident(vararg parts: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (part in parts) {
            for (c in part) {
                when (c) {
                    '"' -> sb.append('"').append('"')
                    '\\' -> sb.append('\\').append('\\')
                    else -> sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Config for naksha_collection
     */
    @JvmStatic
    internal val nakshaCollectionConfig by lazy {
        NakshaCollectionProxy(
            storageClass = null,
            partitions = PARTITION_COUNT_NONE,
            autoPurge = false,
            disableHistory = false,
            id = NKC_TABLE
        )
    }

    /**
     * Array to create a pseudo GeoHash, which is BASE-32 encoded.
     */
    @JvmStatic
    internal val BASE32 = arrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g',
        'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    )

    /**
     * Used to debug.
     */
    @JvmStatic
    val DEBUG = false

    /**
     * The storage class for collections that should be consistent.
     */
    @JvmStatic
    val SC_CONSISTENT = "consistent"

    /**
     * The storage class for collections that need to be ultra-fast, but where data loss is acceptable in worst case scenario.
     */
    @JvmStatic
    val SC_BRITTLE = "brittle"

    /**
     * The storage class for collections that should be ultra-fast and only live for the current session.
     */
    @JvmStatic
    val SC_TEMPORARY = "temporary"

    /**
     * Default storage class.
     */
    @JvmStatic
    val SC_DEFAULT = SC_CONSISTENT

    /**
     * Special internal value used to create the transaction table.
     */
    @JvmStatic
    internal val TRANSACTIONS_COL = "naksha~transactions"

    /**
     * Special internal value used to create the dictionaries' collection.
     */
    @JvmStatic
    internal val DICTIONARIES_COL = "naksha~dictionaries"

    /**
     * Special internal value used to create the collections' collection.
     */
    @JvmStatic
    internal val COLLECTIONS_COL = "naksha~collections"

    /**
     * Special internal value used to create the indices' collection.
     */
    @JvmStatic
    internal val INDICES_COL = "naksha~indices"

    /**
     * Can be set to true, to enable stack-trace reporting to _elog(INFO)_.
     */
    @JvmStatic
    var PRINT_STACK_TRACES = false

    @JvmStatic
    val MAX_PARTITION_COUNT = 128

    /**
     * Array to fasten partition id.
     */
    @JvmStatic
    val PARTITION_ID = Array(MAX_PARTITION_COUNT) {
        when (MAX_PARTITION_COUNT) {
            in 0..9 -> "$it"
            in 10..99 -> if (it < 10) "0$it" else "$it"
            else -> if (it < 10) "00$it" else if (it < 100) "0$it" else "$it"
        }
    }

    /**
     * The lock-id for the transaction number sequence.
     */
    @JvmStatic
    val TXN_LOCK_ID by lazy { lockId("naksha_txn_seq") }

    /**
     * Returns the lock-id for the given name.
     * @param name The name to query the lock-id for.
     * @return The 64-bit FNV1a hash.
     */
    @JvmStatic
    fun lockId(name: String): Int64 = Fnv1a64.string(Fnv1a64.start(), name)

    /**
     * Calculate the pseudo geo-reference-id from the given feature id.
     * @param id The feature id.
     * @return The pseudo geo-reference-id.
     */
    @JvmStatic
    fun gridFromId(id: String): String {
        // = Fnv1a32.string(Fnv1a32.start(), id) and 0x7fff_ffff
        val BASE32 = PgStatic.BASE32
        val sb = StringBuilder()
        var hash = Fnv1a32.string(Fnv1a32.start(), id)
        var i = 0
        sb.append(BASE32[id[0].code and 31])
        while (i++ < 6) {
            val b32 = hash and 31
            sb.append(BASE32[b32])
            hash = hash ushr 5
        }
        hash = Fnv1a32.stringReverse(Fnv1a32.start(), id)
        i = 0
        sb.append(BASE32[id[0].code and 31])
        while (i++ < 6) {
            val b32 = hash and 31
            sb.append(BASE32[b32])
            hash = hash ushr 5
        }
        return sb.toString()
    }

    /**
     * Create all internal collections.
     * @param sql The SQL API of the current session.
     * @param schema The schema in which to create the collections.
     * @param schemaOid The OID of the schema.
     */
    @JvmStatic
    fun createBaseInternalsIfNotExists(sql: PgConnection, schema: String, schemaOid: Int) {
        if (!tableExists(sql, COLLECTIONS_COL, schemaOid)) {
            collectionCreate(
                sql,
                COLLECTIONS_COL,
                schema,
                schemaOid,
                COLLECTIONS_COL,
                DEFAULT_GEO_INDEX,
                partitionCount = PARTITION_COUNT_NONE
            )
        }
        sql.execute("CREATE SEQUENCE IF NOT EXISTS naksha_txn_seq AS int8; COMMIT;")
    }

    // get_byte(digest('hellox','md5'),0)
    /**
     * Returns the partition number for the given amount of partitions.
     * @param id the feature-id for which to return the partition-id.
     * @param partitionCount the number of partitions to generate.
     * @return The partition number as value between 0 and part (exclusive).
     */
    @JvmStatic
    fun partitionIndex(id: String, partitionCount: Int): Int = (Fnv1a32.string(Fnv1a32.start(), id) and 0x7fff_ffff) % partitionCount

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @param partitionCount number of partitions on specific collection.
     * @return The partition id as number between 0 and partitionCount.
     */
    @JvmStatic
    fun partitionNumber(id: String, partitionCount: Int): Int = Fnv1a32.string(Fnv1a32.start(), id).absoluteValue % (partitionCount)

    /**
     * Returns the partition id as three digit string.
     * @param id The feature-id for which to return the partition-id.
     * @param partitionCount number of partitions on specific collection.
     * @return The partition id as three digit string.
     */
    @JvmStatic
    fun partitionNameForId(id: String, partitionCount: Int): String = PARTITION_ID[partitionNumber(id, partitionCount)]

    /**
     * Tests if specific database table (in the Naksha session schema) exists already
     * @param sql The SQL API.
     * @param name The table name.
     * @param schemaOid The object-id of the schema to look into.
     * @return _true_ if a table with this name exists; _false_ otherwise.
     */
    @JvmStatic
    fun tableExists(sql: PgConnection, name: String, schemaOid: Int): Boolean {
        return sql.execute("SELECT oid FROM pg_class WHERE relname = $1 AND relnamespace = $2", arrayOf(name, schemaOid)).fetch().isRow()
    }

    /**
     * Optimize the table storage configuration.
     * @param sql The SQL API.
     * @param tableName The table name.
     * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
     */
    @JvmStatic
    private fun collectionOptimizeTable(sql: PgConnection, tableName: String, history: Boolean) {
        val quotedTableName = PgUtil.quoteIdent(tableName)
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
     * @param geoIndex The geo-index to be used.
     * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
     * @param nakshaCollection The table information.
     */
    @JvmStatic
    private fun collectionAddIndices(
        sql: PgConnection,
        tableName: String,
        geoIndex: String,
        history: Boolean,
        nakshaCollection: PgTableStmt
    ) {
        val fillFactor = if (history) "100" else "70"
        // https://www.postgresql.org/docs/current/gin-tips.html
        val unique = if (history) "" else "UNIQUE "

        // id
        val qtn = PgUtil.quoteIdent(tableName) // quoted table name
        var qin = PgUtil.quoteIdent("${tableName}_id_idx") // quoted index name
        var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(id text_pattern_ops DESC) 
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

        // txn, uid
        qin = PgUtil.quoteIdent("${tableName}_txn_uid_idx")
        query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(txn DESC, COALESCE(uid, 0) DESC) 
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

        // geo, txn
        qin = PgUtil.quoteIdent("${tableName}_geo_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndex
(naksha_geometry(flags,geo), txn) 
WITH (buffering=ON,fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE} WHERE geo IS NOT NULL;"""

        // tags, tnx
        qin = PgUtil.quoteIdent("${tableName}_tags_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin
(tags_to_jsonb(tags), txn) 
WITH (fastupdate=ON,gin_pending_list_limit=32768) ${nakshaCollection.TABLESPACE};"""

        // grid, txn
        qin = PgUtil.quoteIdent("${tableName}_grid_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(geo_grid DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

        // app_id, updated_at, txn
        qin = PgUtil.quoteIdent("${tableName}_app_id_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(app_id text_pattern_ops DESC, updated_at DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

        // author, author_ts, txn
        qin = PgUtil.quoteIdent("${tableName}_author_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(COALESCE(author, app_id) text_pattern_ops DESC, COALESCE(author_ts, updated_at) DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${nakshaCollection.TABLESPACE};"""

        sql.execute(query)
    }

    /**
     * Low level function to create a (optionally partitioned) collection table set.
     * @param conn The SQL API.
     * @param storageClass The type of storage to be used for the table.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     * @param id The collection identifier.
     * @param geoIndex The geo-index to be used.
     * @param partitionCount Number of partitions, possible values: 0 (no partitioning), 2, 4, 8, 16, 32, 64, 128, 256)
     */
    @JvmStatic
    fun collectionCreate(
        conn: PgConnection,
        storageClass: String?,
        schema: String,
        schemaOid: Int,
        id: String,
        geoIndex: String,
        partitionCount: Int
    ) {
        // We store geometry as TWKB, see:
        // http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html
        //val nakshaCollection = PgTableStmt(conn.schemaInfo(), JsEnum.get(storageClass, PgStorageClass::class), partitionCount, false)
        val nakshaCollection: PgTableStmt? = null
        if (nakshaCollection == null) TODO("Implement me!")

        // HEAD
        var query: String = nakshaCollection.CREATE_TABLE
        val headNameQuoted = PgUtil.quoteIdent(id)
        query += headNameQuoted
        query += nakshaCollection.TABLE_BODY
        if (!partitionCount.isPartitioningEnabled()) {
            query += nakshaCollection.WITH
            query += nakshaCollection.TABLESPACE
            conn.execute(query)
            //collectionOptimizeTable(sql, id, false)
            collectionAddIndices(conn, id, geoIndex, false, nakshaCollection)
        } else {
            if (id == TRANSACTIONS_COL) {
                query += " PARTITION BY RANGE (txn) "
            } else {
                query += " PARTITION BY RANGE (naksha_partition_number(id, $partitionCount)) "
            }
            // Partitioned tables must not have storage params
            query += nakshaCollection.TABLESPACE
            conn.execute(query)
            for (part in 0..<partitionCount) {
                createPartitionById(conn, id, geoIndex, part, nakshaCollection, false)
            }
        }
        if (!DEBUG || id.startsWith("naksha")) collectionAttachTriggers(conn, id, schema, schemaOid)

//        // Create sequence.
//        val sequenceName = id + "_uid_seq";
//        val sequenceNameQuoted = PgUtil.quoteIdent(sequenceName)
//        query = "CREATE SEQUENCE IF NOT EXISTS $sequenceNameQuoted AS int8 START WITH 1 CACHE 100 OWNED BY ${headNameQuoted}.uid"
//        sql.execute(query)

        // For all tables except transactions, we create child-tables.
        if (storageClass != TRANSACTIONS_COL) {
            // DEL.
            val delName = "$id\$del"
            val delNameQuoted = PgUtil.quoteIdent(delName)
            query = nakshaCollection.CREATE_TABLE
            query += delNameQuoted
            query += nakshaCollection.TABLE_BODY
            if (!partitionCount.isPartitioningEnabled()) {
                query += nakshaCollection.WITH
                query += nakshaCollection.TABLESPACE
                conn.execute(query)
                //collectionOptimizeTable(sql, delName, false)
                collectionAddIndices(conn, delName, geoIndex, false, nakshaCollection)
            } else {
                query += " PARTITION BY RANGE (naksha_partition_number(id, $partitionCount)) "
                query += nakshaCollection.TABLESPACE
                conn.execute(query)
                for (part in 0..<partitionCount) {
                    createPartitionById(conn, delName, geoIndex, part, nakshaCollection, false)
                }
            }

            // META.
            val metaName = "$id\$meta"
            val metaNameQuoted = PgUtil.quoteIdent(metaName)
            query = nakshaCollection.CREATE_TABLE
            query += metaNameQuoted
            query += nakshaCollection.TABLE_BODY
            query += nakshaCollection.WITH
            query += nakshaCollection.TABLESPACE
            conn.execute(query)
            //collectionOptimizeTable(sql, metaName, false)
            collectionAddIndices(conn, metaName, geoIndex, false, nakshaCollection)

            // HISTORY.
            val hstName = "$id\$hst"
            val hstNameQuoted = PgUtil.quoteIdent(hstName)
            query = nakshaCollection.CREATE_TABLE
            query += hstNameQuoted
            query += nakshaCollection.TABLE_BODY
            query += " PARTITION BY RANGE (txn_next) "
            query += nakshaCollection.TABLESPACE
            conn.execute(query)
            val year = yearOf(Platform.currentMillis())
            createHstPartition(conn, id, year, geoIndex, nakshaCollection)
            createHstPartition(conn, id, year + 1, geoIndex, nakshaCollection)
        }
    }

    /**
     * Extracts the year from the given epoch timestamp in milliseconds.
     * @param epochMillis The epoch milliseconds.
     * @return The UTC year read from the epoch milliseconds.
     */
    @JvmStatic
    fun yearOf(epochMillis: Int64): Int =
        Instant.fromEpochMilliseconds(epochMillis.toLong()).toLocalDateTime(TimeZone.UTC).year

    /**
     * Add the before and after triggers.
     * @param sql The SQL API.
     * @param id The collection identifier.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     */
    @JvmStatic
    private fun collectionAttachTriggers(sql: PgConnection, id: String, schema: String, schemaOid: Int) {
        var triggerName = id + "_before"
        var rows = sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid))
        if (rows.isRow()) {
            val schemaQuoted = PgUtil.quoteIdent(schema)
            val tableNameQuoted = PgUtil.quoteIdent(id)
            val triggerNameQuoted = PgUtil.quoteIdent(triggerName)
            sql.execute(
                """CREATE TRIGGER $triggerNameQuoted BEFORE INSERT OR UPDATE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_before();"""
            )
        }

        triggerName = id + "_after"
        rows = sql.execute("SELECT tgname FROM pg_trigger WHERE tgname = $1 AND tgrelid = $2", arrayOf(triggerName, schemaOid))
        if (rows.isRow()) {
            val schemaQuoted = PgUtil.quoteIdent(schema)
            val tableNameQuoted = PgUtil.quoteIdent(id)
            val triggerNameQuoted = PgUtil.quoteIdent(triggerName)
            sql.execute(
                """CREATE TRIGGER $triggerNameQuoted AFTER INSERT OR UPDATE OR DELETE ON ${schemaQuoted}.${tableNameQuoted}
FOR EACH ROW EXECUTE FUNCTION naksha_trigger_after();"""
            )
        }
    }

    /**
     * Deletes the collection with the given identifier.
     * @param pgConnection The SQL API.
     * @param id The collection identifier.
     */
    @JvmStatic
    fun collectionDrop(pgConnection: PgConnection, id: String) {
        require(!id.startsWith("naksha~"))
        val headName = PgUtil.quoteIdent(id)
        val delName = PgUtil.quoteIdent("$id\$del")
        val metaName = PgUtil.quoteIdent("$id\$meta")
        val hstName = PgUtil.quoteIdent("$id\$hst")
        pgConnection.execute(
            """DROP TABLE IF EXISTS $headName CASCADE;
DROP TABLE IF EXISTS $delName CASCADE;
DROP TABLE IF EXISTS $metaName CASCADE;
DROP TABLE IF EXISTS $hstName CASCADE;"""
        )
    }

    /**
     * Create the history partition, which optionally is sub-partitioned by id.
     * @param pgConnection The SQL API of the session.
     * @param collectionId has to be pure (without _hst suffix).
     * @param year The year for which to create the history partition.
     * @param geoIndex The geo-index to use in the history.
     * @param nakshaCollection The table info to know storage class and alike.
     */
    @JvmStatic
    private fun createHstPartition(
        pgConnection: PgConnection,
        collectionId: String,
        year: Int,
        geoIndex: String,
        nakshaCollection: PgTableStmt
    ):
            String {
        val parentName = "${collectionId}\$hst"
        val parentNameQuoted = PgUtil.quoteIdent(parentName)
        val hstPartName = "${parentName}_${year}"
        val hstPartNameQuoted = PgUtil.quoteIdent(hstPartName)
        val start = Txn.of(year, 0, 0, Txn.SEQ_MIN).value
        val end = Txn.of(year, 12, 31, Txn.SEQ_MAX).value
        var query = nakshaCollection.CREATE_TABLE
        query += "IF NOT EXISTS $hstPartNameQuoted PARTITION OF $parentNameQuoted FOR VALUES FROM ($start) TO ($end) "
        if (nakshaCollection.partitions.isPartitioningEnabled()) {
            query += "PARTITION BY RANGE (naksha_partition_number(id, ${nakshaCollection.partitions}))"
            query += nakshaCollection.TABLESPACE
            pgConnection.execute(query)
            for (subPartition in 0..<nakshaCollection.partitions) {
                createPartitionById(pgConnection, hstPartName, geoIndex, subPartition, nakshaCollection, true)
            }
        } else {
            query += nakshaCollection.WITH
            query += nakshaCollection.TABLESPACE
            pgConnection.execute(query)
            //collectionOptimizeTable(sql, hstPartName, true)
            collectionAddIndices(pgConnection, hstPartName, geoIndex, true, nakshaCollection)
        }
        return hstPartName
    }

    /**
     * Create a child partition that partitions by id. This is used for huge tables to split the features equally into partitions.
     * @param pgConnection The SQL API of the session.
     * @param parentName The name of the parent table.
     * @param geoIndex The geo-index to use.
     * @param part The partition number.
     * @param nakshaCollection Information about the table.
     * @param history If this is a history partition; otherwise it is
     */
    private fun createPartitionById(
        pgConnection: PgConnection, parentName: String, geoIndex: String, part: Int, nakshaCollection:
        PgTableStmt, history: Boolean
    ) {
        require(part in 0..<nakshaCollection.partitions) { "Invalid partition number $part" }
        val partString = PARTITION_ID[part]
        val partitionName = if (parentName.contains('$')) "${parentName}_p$partString" else "${parentName}\$p$partString"
        val partitionNameQuoted = PgUtil.quoteIdent(partitionName)
        val parentTableNameQuoted = PgUtil.quoteIdent(parentName)
        val query =
            nakshaCollection.CREATE_TABLE + "IF NOT EXISTS $partitionNameQuoted PARTITION OF $parentTableNameQuoted FOR VALUES FROM ($part) TO (${part + 1}) ${nakshaCollection.WITH} ${nakshaCollection.TABLESPACE};"
        pgConnection.execute(query)
        //collectionOptimizeTable(sql, partitionName, history)
        collectionAddIndices(pgConnection, partitionName, geoIndex, history, nakshaCollection)
    }

    /**
     * Queries the database for the schema **oid** (object id). The result should be cached as calling this method is expensive.
     * @param pgConnection The SQL API.
     * @param schema The name of the schema.
     * @return The object-id of the schema or _null_, if no such schema was found.
     */
    fun getSchemaOid(pgConnection: PgConnection, schema: String): Int? {
        val cursor = pgConnection.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)).fetch()
        val oid = cursor.column("oid")
        return if (oid is Int) oid else null
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

    private fun Int.isPartitioningEnabled() = this >= 2
}