@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.BigInt64
import com.here.naksha.lib.jbon.Fnv1a32
import com.here.naksha.lib.jbon.Fnv1a64
import com.here.naksha.lib.jbon.Jb
import com.here.naksha.lib.jbon.NakshaTxn
import com.here.naksha.lib.jbon.asArray
import com.here.naksha.lib.jbon.asMap
import com.here.naksha.lib.jbon.div
import com.here.naksha.lib.jbon.getAny
import com.here.naksha.lib.jbon.newMap
import com.here.naksha.lib.jbon.put
import com.here.naksha.lib.jbon.toLong
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.min

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
        nakshaCollectionConfig.put(NKC_GEO_INDEX, null)
        nakshaCollectionConfig.put(NKC_STORAGE_CLASS, null)
        nakshaCollectionConfig.put(NKC_PARTITION, false)
        nakshaCollectionConfig.put(NKC_AUTO_PURGE, false)
        nakshaCollectionConfig.put(NKC_DISABLE_HISTORY, false)
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
    val GEO_INDEX_GIST = "gist"

    /**
     * The constant for the SP-GIST geo-index.
     */
    @JvmStatic
    val GEO_INDEX_SP_GIST = "sp-gist"

    /**
     * The constant for the SP-GIST geo-index.
     */
    @JvmStatic
    val GEO_INDEX_BRIN = "brin"

    /**
     * The constant for the default geo-index (may change over time).
     */
    @JvmStatic
    val GEO_INDEX_DEFAULT = GEO_INDEX_GIST

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
    internal val SC_TRANSACTIONS = "naksha~transactions"

    @JvmStatic
    internal val SC_TRANSACTIONS_ESC = "\"naksha~transactions\""

    /**
     * Special internal value used to create the dictionaries' collection.
     */
    @JvmStatic
    internal val SC_DICTIONARIES = "naksha~dictionaries"

    /**
     * Special internal value used to create the collections' collection.
     */
    @JvmStatic
    internal val SC_COLLECTIONS = "naksha~collections"

    /**
     * Special internal value used to create the indices' collection.
     */
    @JvmStatic
    internal val SC_INDICES = "naksha~indices"

    /**
     * Can be set to true, to enable stack-trace reporting to _elog(INFO)_.
     */
    @JvmStatic
    var PRINT_STACK_TRACES = false

    /**
     * The number of partitions. We use partitioning for tables that are expected to store more than
     * ten million features. With eight partitions we can split 10 million features into partitions
     * of each 1.25 million, 100 million into 12.5 million per partition and for the supported maximum
     * of 1 billion features, each partition holds 125 million features.
     *
     * This value must be a value of 2^n with n between 1 and 8 (2, 4, 8, 16, 32, 64, 128, 256).
     */
    @JvmStatic
    val PARTITION_COUNT = 8

    /**
     * The bitmask to mask the hash for the partition-id.
     */
    @JvmStatic
    val PARTITION_MASK = PARTITION_COUNT - 1

    /**
     * Array to fasten partition id.
     */
    @JvmStatic
    val PARTITION_ID = Array(PARTITION_COUNT) {
        when (PARTITION_COUNT) {
            in 0..9 -> "$it"
            in 10..99 -> if (it < 10) "0$it" else "$it"
            else -> if (it < 10) "00$it" else if (it < 100) "0$it" else "$it"
        }
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
     * Calculate the HERE Tile quad key from the given latitude, longitude, and quad level.
     * @param latitude The latitude.
     * @param longitude The longitude.
     * @param level The quad level.
     * @return The HERE Tile quad key.
     */
    @JvmStatic
    fun calculateHereTileId(latitude: Double, longitude: Double, level: Int = 12): String {
        var x = 0
        if (abs(longitude) != 180.0) {
            val angularWidth: Double = getQuadAngularWidth(level)
            val column = (longitude + 180) / angularWidth

            // In rare occasions, precision issues can cause off-by-one errors, when `column` is rounded to an integer.
            // To prevent this we verify that the coordinate is not outside the quad boundaries.
            if (column % 1 == 0.0 && longitude < angularWidth * column - 180.0) {
                x = column.toInt() - 1
            } else {
                x = column.toInt()
            }
            x = min(getXMax(level), x)
        }

        val angularHeight: Double = getQuadAngularHeight(level)
        val row = (latitude + 90) / angularHeight
        // In rare occasions, precision issues can cause off-by-one errors, when `row` is rounded to an integer.
        // To prevent this we verify that the coordinate is not outside the quad boundaries.
        var y = if (row % 1 == 0.0 && latitude < angularHeight * row - 90.0) {
            row.toInt() - 1
        } else {
            row.toInt()
        }
        y = min(getYMax(level).toDouble(), y.toDouble()).toInt()

        var longKey = convertXYLevelToLongKey(x, y, level)
        longKey = removeLevelIndicator(longKey, level)

        return convertLongKeyToQuadKey(longKey, level)
    }

    @JvmStatic
    private fun getQuadAngularWidth(zoomLevel: Int): Double {
        return 360.0 / (1 shl zoomLevel)
    }

    @JvmStatic
    private fun getXMax(zoomLevel: Int): Int {
        return ((1L shl zoomLevel) - 1).toInt()
    }

    @JvmStatic
    private fun getQuadAngularHeight(level: Int): Double {
        if (level == 0) {
            return 180.0
        }
        return 360.0 / (1L shl level)
    }

    @JvmStatic
    private fun getYMax(zoomLevel: Int): Int {
        return (((1L shl zoomLevel) - 1) / 2).toInt()
    }

    @JvmStatic
    private fun convertXYLevelToLongKey(x: Int, y: Int, level: Int): Long {
        val longKey: Long = convertXYToLongKey(x, y)
        return longKey or (1L shl (level * 2))
    }

    @JvmStatic
    private fun convertXYToLongKey(x: Int, y: Int): Long {
        val X: Long = interleaveToEvenBits(x.toLong())
        val Y: Long = interleaveToEvenBits(y.toLong())
        return X or (Y shl 1)
    }

    @JvmStatic
    private fun interleaveToEvenBits(l: Long): Long {
        var x = l
        x = (x or (x shl 16)) and 0x0000FFFF0000FFFFL
        x = (x or (x shl 8)) and 0x00FF00FF00FF00FFL
        x = (x or (x shl 4)) and 0x0F0F0F0F0F0F0F0FL
        x = (x or (x shl 2)) and 0x3333333333333333L
        x = (x or (x shl 1)) and 0x5555555555555555L
        return x
    }

    @JvmStatic
    private fun removeLevelIndicator(longKey: Long, level: Int): Long {
        return longKey and ((1L shl level * 2) - 1)
    }

    @JvmStatic
    private fun convertLongKeyToQuadKey(longKey: Long, level: Int): String {
        val quadKey = StringBuilder()
        for (i in level - 1 downTo 0) {
            val digit = (longKey shr i * 2) and 3
            quadKey.append(digit)
        }
        return quadKey.toString()
    }

    /**
     * Create all internal collections.
     * @param sql The SQL API of the current session.
     * @param schema The schema in which to create the collections.
     * @param schemaOid The OID of the schema.
     */
    @JvmStatic
    fun createBaseInternalsIfNotExists(sql: IPlv8Sql, schema: String, schemaOid: Int) {
        if (!tableExists(sql, SC_COLLECTIONS, schemaOid)) {
            collectionCreate(sql, SC_COLLECTIONS, schema, schemaOid, SC_COLLECTIONS, GEO_INDEX_DEFAULT, partition = false)
        }
        sql.execute("CREATE SEQUENCE IF NOT EXISTS naksha_txn_seq AS int8; COMMIT;")
    }

    /**
     * Returns the partition number for the given amount of partitions.
     * @param id The feature-id for which to return the partition-id.
     * @param parts The number of partitions to generate.
     * @return The partition number as value between 0 and part (exclusive).
     */
    @JvmStatic
    fun partitionIndex(id: String, parts: Int): Int = (Fnv1a32.string(Fnv1a32.start(), id) and 0x7fff_ffff) % parts

    /**
     * Returns the partition number.
     * @param id The feature-id for which to return the partition-id.
     * @return The partition id as number between 0 and partitionCount.
     */
    @JvmStatic
    fun partitionNumber(id: String): Int = Fnv1a32.string(Fnv1a32.start(), id) and PARTITION_MASK

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
     * @param geoIndex The geo-index to be used.
     * @param history If _true_, then optimized for historic data; otherwise a volatile HEAD table.
     * @param pgTableInfo The table information.
     */
    @JvmStatic
    private fun collectionAddIndices(sql: IPlv8Sql, tableName: String, geoIndex: String, history: Boolean, pgTableInfo: PgTableInfo) {
        val fillFactor = if (history) "100" else "70"
        // https://www.postgresql.org/docs/current/gin-tips.html
        val unique = if (history) "" else "UNIQUE "

        // id
        val qtn = sql.quoteIdent(tableName) // quoted table name
        var qin = sql.quoteIdent("${tableName}_id_idx") // quoted index name
        var query = """CREATE ${unique}INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(id text_pattern_ops DESC) 
WITH (fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE};"""

        // txn, uid
        qin = sql.quoteIdent("${tableName}_txn_uid_idx")
        query += """CREATE UNIQUE INDEX IF NOT EXISTS $qin ON $qtn USING btree 
(txn DESC, COALESCE(uid, 0) DESC) 
WITH (fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE};"""

        // geo, txn
        qin = sql.quoteIdent("${tableName}_geo_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING $geoIndex
(naksha_geometry(flags,geo), txn) 
WITH (buffering=ON,fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE} WHERE geo IS NOT NULL;"""

        // tags, tnx
        qin = sql.quoteIdent("${tableName}_tags_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING gin
(tags_to_jsonb(tags), txn) 
WITH (fastupdate=ON,gin_pending_list_limit=32768) ${pgTableInfo.TABLESPACE};"""

        // grid, txn
        qin = sql.quoteIdent("${tableName}_grid_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(geo_grid DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE};"""

        // app_id, updated_at, txn
        qin = sql.quoteIdent("${tableName}_app_id_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(app_id text_pattern_ops DESC, updated_at DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE};"""

        // author, author_ts, txn
        qin = sql.quoteIdent("${tableName}_author_idx")
        query += """CREATE INDEX IF NOT EXISTS $qin ON $qtn USING btree
(COALESCE(author, app_id) text_pattern_ops DESC, COALESCE(author_ts, updated_at) DESC, txn DESC) 
WITH (fillfactor=$fillFactor) ${pgTableInfo.TABLESPACE};"""

        sql.execute(query)
    }

    /**
     * Low level function to create a (optionally partitioned) collection table set.
     * @param sql The SQL API.
     * @param storageClass The type of storage to be used for the table.
     * @param schema The schema name.
     * @param schemaOid The object-id of the schema to look into.
     * @param id The collection identifier.
     * @param geoIndex The geo-index to be used.
     * @param partition If the collection should be partitioned.
     */
    @JvmStatic
    fun collectionCreate(sql: IPlv8Sql, storageClass: String?, schema: String, schemaOid: Int, id: String, geoIndex: String, partition: Boolean) {
        // We store geometry as TWKB, see:
        // http://www.danbaston.com/posts/2018/02/15/optimizing-postgis-geometries.html
        val pgTableInfo = PgTableInfo(sql, storageClass)

        // HEAD
        var query: String = pgTableInfo.CREATE_TABLE
        val headNameQuoted = sql.quoteIdent(id)
        query += headNameQuoted
        query += pgTableInfo.CREATE_TABLE_BODY
        if (!partition) {
            query += pgTableInfo.STORAGE_PARAMS
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            //collectionOptimizeTable(sql, id, false)
            collectionAddIndices(sql, id, geoIndex, false, pgTableInfo)
        } else {
            if (id == SC_TRANSACTIONS) {
                query += " PARTITION BY RANGE (txn) "
            } else {
                query += " PARTITION BY RANGE (naksha_partition_number(id)) "
            }
            // Partitioned tables must not have storage params
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            for (part in 0..<PARTITION_COUNT) {
                createPartitionById(sql, id, geoIndex, part, pgTableInfo, false)
            }
        }
        if (!DEBUG || id.startsWith("naksha")) collectionAttachTriggers(sql, id, schema, schemaOid)

//        // Create sequence.
//        val sequenceName = id + "_uid_seq";
//        val sequenceNameQuoted = sql.quoteIdent(sequenceName)
//        query = "CREATE SEQUENCE IF NOT EXISTS $sequenceNameQuoted AS int8 START WITH 1 CACHE 100 OWNED BY ${headNameQuoted}.uid"
//        sql.execute(query)

        // For all tables except transactions, we create child-tables.
        if (storageClass != SC_TRANSACTIONS) {
            // DEL.
            val delName = "$id\$del"
            val delNameQuoted = sql.quoteIdent(delName)
            query = pgTableInfo.CREATE_TABLE
            query += delNameQuoted
            query += pgTableInfo.CREATE_TABLE_BODY
            if (!partition) {
                query += pgTableInfo.STORAGE_PARAMS
                query += pgTableInfo.TABLESPACE
                sql.execute(query)
                //collectionOptimizeTable(sql, delName, false)
                collectionAddIndices(sql, delName, geoIndex, false, pgTableInfo)
            } else {
                query += " PARTITION BY RANGE (naksha_partition_number(id)) "
                query += pgTableInfo.TABLESPACE
                sql.execute(query)
                for (part in 0..<PARTITION_COUNT) {
                    createPartitionById(sql, delName, geoIndex, part, pgTableInfo, false)
                }
            }

            // META.
            val metaName = "$id\$meta"
            val metaNameQuoted = sql.quoteIdent(metaName)
            query = pgTableInfo.CREATE_TABLE
            query += metaNameQuoted
            query += pgTableInfo.CREATE_TABLE_BODY
            query += pgTableInfo.STORAGE_PARAMS
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            //collectionOptimizeTable(sql, metaName, false)
            collectionAddIndices(sql, metaName, geoIndex, false, pgTableInfo)

            // HISTORY.
            val hstName = "$id\$hst"
            val hstNameQuoted = sql.quoteIdent(hstName)
            query = pgTableInfo.CREATE_TABLE
            query += hstNameQuoted
            query += pgTableInfo.CREATE_TABLE_BODY
            query += " PARTITION BY RANGE (txn_next) "
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            val year = yearOf(Jb.env.currentMillis())
            createHstPartition(sql, id, year, geoIndex, partition, pgTableInfo)
            createHstPartition(sql, id, year + 1, geoIndex, partition, pgTableInfo)
        }
    }

    /**
     * Extracts the year from the given epoch timestamp in milliseconds.
     * @param epochMillis The epoch milliseconds.
     * @return The UTC year read from the epoch milliseconds.
     */
    @JvmStatic
    fun yearOf(epochMillis: BigInt64): Int =
            Instant.fromEpochMilliseconds(epochMillis.toLong()).toLocalDateTime(TimeZone.UTC).year

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
        require(!id.startsWith("naksha~"))
        val headName = sql.quoteIdent(id)
        val delName = sql.quoteIdent("$id\$del")
        val metaName = sql.quoteIdent("$id\$meta")
        val hstName = sql.quoteIdent("$id\$hst")
        sql.execute("""DROP TABLE IF EXISTS $headName CASCADE;
DROP TABLE IF EXISTS $delName CASCADE;
DROP TABLE IF EXISTS $metaName CASCADE;
DROP TABLE IF EXISTS $hstName CASCADE;""")
    }

    /**
     * Create the history partition, which optionally is sub-partitioned by id.
     * @param sql The SQL API of the session.
     * @param collectionId has to be pure (without _hst suffix).
     * @param year The year for which to create the history partition.
     * @param geoIndex The geo-index to use in the history.
     * @param partition If the history should be sub-partitioned by id.
     * @param pgTableInfo The table info to know storage class and alike.
     */
    @JvmStatic
    private fun createHstPartition(sql: IPlv8Sql, collectionId: String, year: Int, geoIndex: String, partition: Boolean, pgTableInfo: PgTableInfo): String {
        val parentName = "${collectionId}\$hst"
        val parentNameQuoted = sql.quoteIdent(parentName)
        val hstPartName = "${parentName}_${year}"
        val hstPartNameQuoted = sql.quoteIdent(hstPartName)
        val start = NakshaTxn.of(year, 0, 0, NakshaTxn.SEQ_MIN).value
        val end = NakshaTxn.of(year, 12, 31, NakshaTxn.SEQ_MAX).value
        var query = pgTableInfo.CREATE_TABLE
        query += "IF NOT EXISTS $hstPartNameQuoted PARTITION OF $parentNameQuoted FOR VALUES FROM ($start) TO ($end) "
        if (partition) {
            query += "PARTITION BY RANGE (naksha_partition_number(id))"
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            for (subPartition in 0..<PARTITION_COUNT) {
                createPartitionById(sql, hstPartName, geoIndex, subPartition, pgTableInfo, true)
            }
        } else {
            query += pgTableInfo.STORAGE_PARAMS
            query += pgTableInfo.TABLESPACE
            sql.execute(query)
            //collectionOptimizeTable(sql, hstPartName, true)
            collectionAddIndices(sql, hstPartName, geoIndex, true, pgTableInfo)
        }
        return hstPartName
    }

    /**
     * Create a child partition that partitions by id. This is used for huge tables to split the features equally into partitions.
     * @param sql The SQL API of the session.
     * @param parentName The name of the parent table.
     * @param geoIndex The geo-index to use.
     * @param part The partition number.
     * @param pgTableInfo Information about the table.
     * @param history If this is a history partition; otherwise it is
     */
    private fun createPartitionById(sql: IPlv8Sql, parentName: String, geoIndex: String, part: Int, pgTableInfo: PgTableInfo, history: Boolean) {
        require(part in 0..<PARTITION_COUNT) { "Invalid partition number $part" }
        val partitionName = if (parentName.contains('$')) "${parentName}_p$part" else "${parentName}\$p$part"
        val partitionNameQuoted = sql.quoteIdent(partitionName)
        val parentTableNameQuoted = sql.quoteIdent(parentName)
        val query = pgTableInfo.CREATE_TABLE + "IF NOT EXISTS $partitionNameQuoted PARTITION OF $parentTableNameQuoted FOR VALUES FROM ($part) TO (${part + 1}) ${pgTableInfo.STORAGE_PARAMS} ${pgTableInfo.TABLESPACE};"
        sql.execute(query)
        //collectionOptimizeTable(sql, partitionName, history)
        collectionAddIndices(sql, partitionName, geoIndex, history, pgTableInfo)
    }

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

    fun currentMillis(): BigInt64? = if (DEBUG) Jb.env.currentMicros() / 1000 else null
}