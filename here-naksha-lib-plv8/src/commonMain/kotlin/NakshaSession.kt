@file:OptIn(ExperimentalJsExport::class)

package com.here.naksha.lib.plv8

import com.here.naksha.lib.jbon.*
import com.here.naksha.lib.plv8.Static.SC_TRANSACTIONS
import com.here.naksha.lib.plv8.Static.nakshaCollectionConfig
import kotlinx.datetime.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic

/**
 * A session linked to a PostgresQL database connection with support for some special table layout and triggers. Its purpose is
 * to support the Naksha `lib-psql`. This implements the code that normally resides inside of Postgres, however, technically
 * it can be run as well in a JVM, but in this case the JVM needs to back the database connection with a JDBC connection.
 *
 * Therefore, in the JVM the code runs the same way it runs inside the PostgresQL database PLV8 extension, but with higher
 * latencies and with triggers being simulated.
 *
 * @property sql Access to database. In PostgresQL bound by the SQL function `naksha_start_session` to the PLV8
 * database connection wrapper. In the JVM bound by the `startSession` call of the `Plv8Env` class.
 * Technically, the `lib-psql` will always use the SQL function, therefore the only reason to use the
 * JVM function `startSession` is, when testing the code to simulate a PLV8 environment.
 * @property schema The database schema of this session.
 * @property storageId The storage-identifier of this session.
 * @param appName The name of the application starting the session, only for debugging purpose.
 * @param streamId The stream-identifier, to be added to the transaction logs for debugging purpose.
 * @param appId The UPM identifier of the application (for audit).
 * @param author The UPM identifier of the user (for audit).
 * @constructor Create a new session.
 */
@Suppress("UNUSED_PARAMETER", "unused", "LocalVariableName", "MemberVisibilityCanBePrivate", "DuplicatedCode")
@JsExport
class NakshaSession(
        val sql: IPlv8Sql,
        val schema: String,
        val storageId: String,
        appName: String, streamId: String, appId: String, author: String? = null
) : JbSession(appName, streamId, appId, author) {

    /**
     * The [object identifier](https://www.postgresql.org/docs/current/datatype-oid.html) of the schema.
     */
    internal var schemaOid: Int

    /**
     * The cached quoted schema name (double quotes).
     */
    internal val schemaIdent = sql.quoteIdent(schema)

    /**
     * The dictionary manager bound to the global dictionary.
     */
    internal val globalDictManager = NakshaDictManager(this)

    /**
     * The dictionary managers for collections with the key being the identifier of the collection and the value being the manager.
     */
    internal val collectionDictManagers = HashMap<String, NakshaDictManager>()

    /**
     * Returns the dictionary manager for the given collection.
     * @param collectionId The identifier of the collection for which to return the dictionary manager.
     * @return The dictionary manager.
     * @throws IllegalArgumentException If no such collection exists.
     */
    fun getDictManager(collectionId: String? = null): IDictManager {
        // TODO: Implement collection specific managers
        return globalDictManager
    }

    companion object {
        /**
         * Returns the current thread local [NakshaSession].
         * @return The current thread local [NakshaSession].
         * @throws IllegalStateException If the current session is no Naksha session.
         */
        @JvmStatic
        fun get(): NakshaSession = threadLocal.get() as NakshaSession
    }

    /**
     * The current transaction number.
     */
    private var _txn: NakshaTxn? = null

    /**
     * The epoch milliseconds of when the transaction started (`transaction_timestamp()`).
     */
    private var _txts: BigInt64? = null

    /**
     * Keeps transaction's counters.
     */
    var transaction: NakshaTransaction = NakshaTransaction(dictManager = globalDictManager)

    /**
     * A cache to remember collections configuration <collectionId, configMap>
     */
    var collectionConfiguration: IMap

    /**
     * The last error number as SQLState.
     */
    var errNo: String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg: String? = null

    init {
        sql.execute("""
SET SESSION search_path TO $schemaIdent, public, topology;
SET SESSION enable_seqscan = OFF;
""")
        schemaOid = asMap(asArray(sql.execute("SELECT oid FROM pg_namespace WHERE nspname = $1", arrayOf(schema)))[0]).getAny("oid") as Int
        collectionConfiguration = Jb.map.newMap()
        collectionConfiguration.put(NKC_TABLE, nakshaCollectionConfig)
    }

    override fun reset(appName: String, streamId: String, appId: String, author: String?) {
        super.reset(appName, streamId, appId, author)
        clear()
    }

    override fun clear() {
        super.clear()
        _txn = null
        uid = 0
        _txts = null
        errNo = null
        errMsg = null
        collectionConfiguration = Jb.map.newMap()
        collectionConfiguration.put(NKC_TABLE, nakshaCollectionConfig)
        transaction = NakshaTransaction(globalDictManager)
    }

    /**
     * Notify the session of the latest schema-oid detected. This should clear caching, when such a change is detected, which can
     * happen for example, when the client drops and re-creates the schema this session is bound to.
     * @param oid The new schema oid.
     */
    internal fun verifyCache(oid: Int) {
        if (schemaOid != oid) {
            this.schemaOid = oid
            clear()
        }
    }

    private lateinit var gridPlan: IPlv8Plan

    /**
     * Create a GRID ([GeoHash](https://en.wikipedia.org/wiki/Geohash) Reference ID) from the given geometry.
     * The GRID is used for distributed processing of features. This method uses GeoHash at level 14, which uses
     * 34 bits for latitude and 36 bits for longitude (70-bit total). The precision is therefore higher than 1mm.
     *
     * If the feature does not have a geometry, this method creates a pseudo GRID (GeoHash Reference ID) from the
     * given feature-id, this is based upon [Geohash](https://en.wikipedia.org/wiki/Geohash#Textual_representation).
     *
     * See [https://www.movable-type.co.uk/scripts/geohash.html](https://www.movable-type.co.uk/scripts/geohash.html)
     * @param id The feature-id.
     * @param geoType The geometry type.
     * @param geo The feature geometry; if any.
     * @return The GRID (14 character long string).
     */
    internal fun grid(id: String, geoType: Short, geo: ByteArray?): Int {
        // FIXME TODO use point to here tile function after merge
        return 0
//        if (geo == null) return Static.gridFromId(id)
//        if (!this::gridPlan.isInitialized) {
//            gridPlan = sql.prepare("SELECT ST_GeoHash(ST_Centroid(naksha_geometry($1::int2,$2::bytea)),14) as hash", arrayOf(SQL_INT16, SQL_BYTE_ARRAY))
//        }
//        return asMap(asArray(gridPlan.execute(arrayOf(geoType, geo)))[0])["hash"]!!
    }

    /**
     * Calculates the extent, the size of the feature in milliseconds.
     */
    internal fun extent(geo: ByteArray?): BigInt64 = Jb.int64.ZERO()
    // TODO: Implement me!

    private val xyzBuilder = XyzBuilder(Jb.env.newDataView(ByteArray(500)))

    /**
     * Convert the given database row into a JBON encoded XYZ namespace.
     * @param collectionId The collection for which to generate the XYZ namespace.
     * @param row The database row.
     * @return The XYZ namespace JBON encoded.
     */
    fun xyzNsFromRow(collectionId: String, row: IMap): ByteArray {
        val createdAt: BigInt64? = row[COL_CREATED_AT] ?: row[COL_UPDATE_AT]
        check(createdAt != null) { "Missing $COL_CREATED_AT in row" }
        // for transactions update might be null, for features created at might be null
        val updatedAt: BigInt64 = row[COL_UPDATE_AT] ?: row[COL_CREATED_AT]!!
        val txn: BigInt64? = row[COL_TXN]
        check(txn != null) { "Missing $COL_TXN in row" }
        val nakshaTxn = NakshaTxn(txn)
        val uid: Int? = row[COL_UID]
        check(uid != null) { "Missing $COL_UID in row" }
        val uuid = nakshaTxn.newFeatureUuid(storageId, collectionId, uid)
        val ptxn: BigInt64? = row[COL_PTXN]
        val puid: Int? = row[COL_PUID]
        val puuid = if (ptxn != null && puid != null) {
            NakshaTxn(ptxn).newFeatureUuid(storageId, collectionId, puid).toString()
        } else {
            null
        }
        val action: Short = row[COL_ACTION] ?: 0
        val version: Int = row[COL_VERSION] ?: 1
        val authorTs: BigInt64 = row[COL_AUTHOR_TS] ?: updatedAt
        val app_id: String? = row[COL_APP_ID]
        check(app_id != null) { "Missing $COL_APP_ID, please invoke naksha_start_session" }
        val author: String = row[COL_AUTHOR] ?: app_id
        val geo_grid: Int? = row[COL_GEO_GRID]
        check(geo_grid != null) { "Missing $COL_GEO_GRID, please invoke naksha_start_session" }
        return xyzBuilder.buildXyzNs(createdAt, updatedAt, txn, action, version, authorTs, puuid, uuid.toString(), app_id, author, geo_grid)
    }

    /**
     * Session local uid counter.
     */
    private var uid = 0

    /**
     * Create the XYZ namespace for an _INSERT_ operation.
     * @param collectionId The collection into which a feature is inserted.
     * @param NEW The row in which to update the XYZ namespace columns.
     * @return The new XYZ namespace for this feature.
     */
    internal fun xyzInsert(collectionId: String, NEW: IMap) {
        val txn = txn()
        val txnTs = txnTs()
        NEW[COL_TXN] = txn.value
        NEW[COL_TXN_NEXT] = null
        NEW[COL_PTXN] = null
        NEW[COL_PUID] = null
        var geoType: Short? = NEW[COL_GEO_TYPE]
        if (geoType == null) {
            geoType = GEO_TYPE_NULL
            NEW[COL_GEO_TYPE] = GEO_TYPE_NULL
        }
        val geoGrid: Int? = NEW[COL_GEO_GRID]
        if (geoGrid == null) {
            // Only calculate geo-grid, if not given by the client.
            val id: String? = NEW[COL_ID]
            check(id != null) { "Missing id" }
            NEW[COL_GEO_GRID] = grid(id, geoType, NEW[COL_GEOMETRY])
        }
        NEW[COL_ACTION] = null // saving space null means 0 (create)
        NEW[COL_VERSION] = null // saving space null means 1
        if (collectionId == SC_TRANSACTIONS) {
            NEW[COL_CREATED_AT] = txnTs
            NEW[COL_UPDATE_AT] = null // saving space - it is same as created_at at creation,
            NEW[COL_UID] = 0
        } else {
            NEW[COL_CREATED_AT] = null // saving space - it is same as update_at at creation,
            NEW[COL_UPDATE_AT] = txnTs
            NEW[COL_UID] = nextUid()
        }
        NEW[COL_AUTHOR] = author
        NEW[COL_AUTHOR_TS] = null // saving space - only apps are allowed to create features
        NEW[COL_APP_ID] = appId
    }

    /**
     *  Prepares XyzNamespace columns for head table.
     */
    internal fun xyzUpdateHead(collectionId: String, NEW: IMap, OLD: IMap) {
        xyzInsert(collectionId, NEW)
        NEW[COL_ACTION] = ACTION_UPDATE.toShort()
        NEW[COL_CREATED_AT] = OLD[COL_CREATED_AT] ?: OLD[COL_UPDATE_AT]
        if (author == null) {
            NEW[COL_AUTHOR_TS] = OLD[COL_AUTHOR_TS] ?: OLD[COL_UPDATE_AT]
        } else {
            NEW[COL_AUTHOR_TS] = null
        }
        val oldVersion: Int = OLD[COL_VERSION] ?: 1
        NEW[COL_VERSION] = oldVersion + 1
        NEW[COL_PTXN] = OLD[COL_TXN]
        NEW[COL_PUID] = OLD[COL_UID]
    }

    /**
     *  Saves OLD in $hst.
     */
    internal fun saveInHst(collectionId: String, OLD: IMap) {
        if (isHistoryEnabled(collectionId)) {
            // TODO move it outside and run it once
            val collectionIdQuoted = sql.quoteIdent("${collectionId}\$hst")
            val hstInsertPlan = sql.prepare("""INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)""", COL_ALL_TYPES)
            hstInsertPlan.execute(arrayOf(OLD[COL_TXN_NEXT], OLD[COL_TXN], OLD[COL_UID], OLD[COL_PTXN], OLD[COL_PUID], OLD[COL_GEO_TYPE], OLD[COL_ACTION], OLD[COL_VERSION], OLD[COL_CREATED_AT], OLD[COL_UPDATE_AT], OLD[COL_AUTHOR_TS], OLD[COL_AUTHOR], OLD[COL_APP_ID], OLD[COL_GEO_GRID], OLD[COL_ID], OLD[COL_TAGS], OLD[COL_GEOMETRY], OLD[COL_FEATURE], OLD[COL_GEO_REF], OLD[COL_TYPE]))
        }
    }

    /**
     * Prepares row before putting into $del table.
     */
    internal fun xyzDel(OLD: IMap) {
        val txn = txn()
        val txnTs = txnTs()
        OLD[COL_TXN] = txn.value
        OLD[COL_TXN_NEXT] = txn.value
        OLD[COL_ACTION] = ACTION_DELETE.toShort()
        OLD[COL_AUTHOR] = author ?: appId
        if (author != null) {
            OLD[COL_AUTHOR_TS] = txnTs
        }
        if (!OLD.hasCreatedAt()) {
            OLD[COL_CREATED_AT] = OLD[COL_UPDATE_AT]
        }
        OLD[COL_UPDATE_AT] = txnTs
        OLD[COL_APP_ID] = appId
        OLD[COL_UID] = nextUid()
        val currentVersion: Int = OLD[COL_VERSION] ?: 1
        OLD[COL_VERSION] = currentVersion + 1
    }

    internal fun deleteFromDel(collectionId: String, id: String) {
        val collectionIdQuoted = sql.quoteIdent("${collectionId}\$del")
        sql.execute("""DELETE FROM $collectionIdQuoted WHERE id = $1""", arrayOf(id))
    }

    /**
     * Updates xyz namespace and copies feature to $del table.
     */
    internal fun copyToDel(collectionId: String, OLD: IMap) {
        val collectionConfig = getCollectionConfig(collectionId)
        val autoPurge: Boolean? = collectionConfig[NKC_AUTO_PURGE]
        if (autoPurge != true) {
            val collectionIdQuoted = sql.quoteIdent("${collectionId}\$del")
            sql.execute("""INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)""", arrayOf(OLD[COL_TXN_NEXT], OLD[COL_TXN], OLD[COL_UID], OLD[COL_PTXN], OLD[COL_PUID], OLD[COL_GEO_TYPE], OLD[COL_ACTION], OLD[COL_VERSION], OLD[COL_CREATED_AT], OLD[COL_UPDATE_AT], OLD[COL_AUTHOR_TS], OLD[COL_AUTHOR], OLD[COL_APP_ID], OLD[COL_GEO_GRID], OLD[COL_ID], OLD[COL_TAGS], OLD[COL_GEOMETRY], OLD[COL_FEATURE], OLD[COL_GEO_REF], OLD[COL_TYPE]))
        }
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [NkCollectionTrigger.NEW].
     */
    fun triggerBefore(data: PgTrigger) {
        // FIXME
        throw RuntimeException("do not use triggers")
        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
        if (data.TG_OP == TG_OP_INSERT) {
            check(data.NEW != null) { "Missing NEW for INSERT" }
            xyzInsert(collectionId, data.NEW)
        } else if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            xyzUpdateHead(collectionId, data.NEW, data.OLD)
        }
        // We should not be called for delete, in that case do nothing.
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [NkCollectionTrigger.NEW].
     */
    fun triggerAfter(data: PgTrigger) {
        // FIXME
        throw RuntimeException("do not use triggers")
        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
        if (data.TG_OP == TG_OP_DELETE && data.OLD != null) {
            deleteFromDel(collectionId, data.OLD.getAny(COL_ID) as String)
            // save current head in hst
            data.OLD[COL_TXN_NEXT] = data.OLD[COL_TXN]
            saveInHst(collectionId, data.OLD)
            xyzDel(data.OLD)
            copyToDel(collectionId, data.OLD)
            // save del state in hst
            saveInHst(collectionId, data.OLD)
        }
        if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            deleteFromDel(collectionId, data.NEW.getAny(COL_ID) as String)
            data.OLD[COL_TXN_NEXT] = data.NEW[COL_TXN]
            saveInHst(collectionId, data.OLD)
        }
        if (data.TG_OP == TG_OP_INSERT) {
            check(data.NEW != null) { "Missing NEW for INSERT" }
            deleteFromDel(collectionId, data.NEW.getAny(COL_ID) as String)
        }
    }

    /**
     * An internal view to calculate the partition id.
     */
    private lateinit var partView: IDataView

    /**
     * The cached Postgres version.
     */
    private lateinit var pgVersion: XyzVersion

    /**
     * Returns the PostgresQL version.
     * @return The PostgresQL version.
     */
    fun postgresVersion(): XyzVersion {
        if (!this::pgVersion.isInitialized) {
            val r = sql.execute("select version() as version")
            val rows = sql.rows(r)
            check(rows != null)
            val row = asMap(rows[0])
            // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
            val versionString: String = row["version"]!!
            val firstSpace = versionString.indexOf(' ')
            check(firstSpace > 0)
            val secondSpace = versionString.indexOf(' ', firstSpace + 1)
            check(secondSpace > firstSpace)
            val pgv = versionString.substring(firstSpace + 1, secondSpace)
            pgVersion = XyzVersion.fromString(pgv)
        }
        return pgVersion
    }

    internal fun nextUid() = uid++

    /**
     * Returns the current transaction number, if no transaction number is yet generated, generates a new one.
     * @return The current transaction number.
     */
    fun txn(): NakshaTxn {
        if (_txn == null) {
            val query = """
WITH ns as (SELECT oid FROM pg_namespace WHERE nspname = $1),
     txn_seq as (SELECT cls.oid as oid FROM pg_class cls, ns WHERE cls.relname = 'naksha_txn_seq' and cls.relnamespace = ns.oid)
SELECT nextval(txn_seq.oid) as txn, txn_seq.oid as txn_oid, (extract(epoch from transaction_timestamp())*1000)::int8 as time, ns.oid as ns_oid
FROM ns, txn_seq;"""
            val row = asMap(asArray(sql.execute(query, arrayOf(schema)))[0])
            val schemaOid = row.getAny("ns_oid") as Int
            val txnSeqOid = row.getAny("txn_oid") as Int
            val txts = asBigInt64(row["time"])
            var txn = NakshaTxn(asBigInt64(row["txn"]))
            verifyCache(schemaOid)
            _txts = txts
            _txn = txn
            val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
            val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
            if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(Static.TXN_LOCK_ID))
                try {
                    val raw = asBigInt64(asMap((sql.execute("SELECT nextval($1) as v", arrayOf(txnSeqOid)) as Array<Any>)[0])["v"])
                    txn = NakshaTxn(raw)
                    _txn = txn
                    if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                        // Rollover, we update sequence of the day.
                        txn = NakshaTxn.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, BigInt64(0))
                        _txn = txn
                        sql.execute("SELECT setval($1, $2)", arrayOf(txnSeqOid, txn.value + 1))
                    }
                } finally {
                    sql.execute("SELECT pg_advisory_unlock($1)", arrayOf(Static.TXN_LOCK_ID))
                }
            }
        }
        return _txn!!
    }

    /**
     * The start time of the transaction.
     * @return The start time of the transaction.
     */
    fun txnTs(): BigInt64 {
        txn()
        return _txts!!
    }

    private fun handleFeatureException(e: Throwable, table: ITable, id: String?) {
        val err = asMap(e)
        // available fields (only on server): sqlerrcode, schema_name, table_name, column_name, datatype_name, constraint_name, detail, hint, context, internalquery, code
        val errCode: String? = err["sqlerrcode"] ?: err["sqlstate"]
        when {
            errCode != null -> {
                table.returnErr(errCode, e.cause?.message ?: errCode, id)
            }

            else -> {
                if (Static.PRINT_STACK_TRACES)
                    Jb.log.info(e.cause?.message!!)
                table.returnErr(ERR_FATAL, e.cause?.message ?: "Fatal ${e.stackTraceToString()}", id)
            }
        }
    }

    fun writeCollections(
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size),
            geo_type_arr: Array<Short?> = arrayOfNulls(op_arr.size),
            geo_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size),
            tags_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size)
    ): ITable {
        val table = sql.newTable()
        val writer = NakshaFeaturesWriter(NKC_TABLE, this)
        try {
            return writer.writeCollections(op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr, false)
        } catch (e: NakshaException) {
            if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
            table.returnException(e)
        } catch (e: Throwable) {
            handleFeatureException(e, table, null)
        }
        return table;
    }

    private lateinit var featureReader: JbMapFeature
    private lateinit var propertiesReader: JbMap

    /**
     * Extract the id from the given feature.
     * @param feature The feature.
     * @param collectionId The collection-identifier of the collection from which the bytes were read.
     * @return The id or _null_, if the feature does not have a dedicated id.
     * @throws IllegalArgumentException If the no such collection exists.
     */
    fun getFeatureId(feature: ByteArray, collectionId: String? = null): String? {
        if (!this::featureReader.isInitialized) featureReader = JbMapFeature(globalDictManager)
        val reader = featureReader
        reader.dictManager = getDictManager(collectionId)
        reader.mapBytes(feature)
        return reader.id()
    }

    /**
     * Extract the type of the feature by checking _properties.featureType_, _momType_ and _type_ properties in
     * that order. If none of these properties exist, returning _Feature_.
     * @param feature The feature to search in.
     * @param collectionId The collection-identifier from which the feature bytes were read.
     * @return The feature type.
     * @throws IllegalArgumentException If no such collection exists.
     */
    fun getFeatureType(feature: ByteArray, collectionId: String? = null): String {
        if (!this::featureReader.isInitialized) featureReader = JbMapFeature(globalDictManager)
        val reader = featureReader
        reader.dictManager = getDictManager(collectionId)
        reader.mapBytes(feature)
        val root = reader.root()
        if (root.selectKey("properties")) {
            val value = root.value()
            if (value.isMap()) {
                if (!this::propertiesReader.isInitialized) propertiesReader = JbMap()
                val properties = propertiesReader
                properties.mapReader(value)
                if (properties.selectKey("featureType")) {
                    val v = properties.value()
                    if (v.isString()) return v.readString()
                }
            }
        }
        if (root.selectKey("momType") || root.selectKey("type")) {
            val value = root.value()
            if (value.isString()) return value.readString()
        }
        return "Feature"
    }

    /**
     * Returns collectionId without partition part.
     * For `topology_p0` it will return `topology`.
     */
    fun getBaseCollectionId(collectionId: String): String {
        // Note: "topology_p000" is a partition, but we need collection-id.
        //        0123456789012
        // So, in that case we will find an underscore at index 8, so i = length-5!
        val i = collectionId.lastIndexOf('$')
        return if (i >= 0 && i == (collectionId.length - 3) && collectionId[i + 1] == 'p') {
            collectionId.substring(0, i)
        } else {
            collectionId
        }
    }

    fun getCollectionConfig(collectionId: String): IMap {
        return if (collectionConfiguration.containsKey(collectionId)) {
            collectionConfiguration[collectionId]!!
        } else {
            val collectionsSearchRows = asArray(sql.execute("select $COL_FEATURE from $NKC_TABLE_ESC where $COL_ID = $1", arrayOf(collectionId)))
            if (collectionsSearchRows.isEmpty()) {
                throw RuntimeException("collection $collectionId does not exist in $NKC_TABLE_ESC")
            }
            val cols = asMap(collectionsSearchRows[0])
            val jbFeature = JbFeature(getDictManager(collectionId)).mapBytes(cols[COL_FEATURE])
            val featureAsMap = JbMap().mapReader(jbFeature.reader).toIMap()
            collectionConfiguration.put(collectionId, featureAsMap)
            featureAsMap
        }
    }

    internal fun isHistoryEnabled(collectionId: String): Boolean {
        val isDisabled: Boolean? = getCollectionConfig(collectionId)[NKC_DISABLE_HISTORY]
        return isDisabled != true
    }

    /**
     * Single threaded all-or-nothing bulk write operation.
     * As result there is row with success or error returned.
     */
    fun writeFeatures(
            collectionId: String,
            op_arr: Array<ByteArray>,
            feature_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size),
            geo_type_arr: Array<Short?> = arrayOfNulls(op_arr.size),
            geo_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size),
            tags_arr: Array<ByteArray?> = arrayOfNulls(op_arr.size),
            minResult: Boolean = true
    ): ITable {
        val table = sql.newTable()
        val featureWriter = NakshaFeaturesWriter(collectionId, this)
        val xyzBuilder = XyzBuilder.create()
        val transactionWriter = NakshaFeaturesWriter(SC_TRANSACTIONS, this, modifyCounters = false)
        try {
            val op = xyzBuilder.buildXyzOp(XYZ_OP_UPSERT, txn().toUuid(storageId).toString(), null, null)
            transactionWriter.writeFeatures(arrayOf(op), arrayOf(transaction.toBytes()), minResult = true)
            val writeFeaturesResult = featureWriter.writeFeatures(op_arr, feature_arr, geo_type_arr, geo_arr, tags_arr, minResult)
            transactionWriter.writeFeatures(arrayOf(op), arrayOf(transaction.toBytes()), minResult = true)
            return writeFeaturesResult
        } catch (e: NakshaException) {
            if (Static.PRINT_STACK_TRACES) Jb.log.info(e.rootCause().stackTraceToString())
            table.returnException(e)
        } catch (e: Throwable) {
            handleFeatureException(e, table, null)
        }
        return table;
    }

    internal fun select(collectionId: String, ids: List<String>): IMap {
        val collectionIdQuoted = sql.quoteIdent(collectionId)
        val result = sql.execute("SELECT $COL_ID, $COL_TXN, $COL_UID, $COL_ACTION, $COL_VERSION, $COL_CREATED_AT, $COL_UPDATE_AT, $COL_AUTHOR, $COL_AUTHOR_TS FROM $collectionIdQuoted WHERE id = ANY($1)", arrayOf(ids.toTypedArray()))
        val rows = sql.rows(result)
        val retMap = newMap()
        if (rows.isNullOrEmpty())
            return retMap
        for (row in rows) {
            val cols = asMap(row)
            retMap.put(cols[COL_ID]!!, cols)
        }
        return retMap
    }

    internal fun queryForExisting(collectionId: String, idsSmallFetch: List<String>, idsFullFetch: List<String>, wait: Boolean): IMap {
        val waitOp = if (wait) "" else "NOWAIT"
        val collectionIdQuoted = sql.quoteIdent(collectionId)
        val basicQuery = "SELECT $COL_ID,$COL_TXN,$COL_UID,$COL_ACTION,$COL_VERSION,$COL_CREATED_AT,$COL_UPDATE_AT,$COL_AUTHOR,$COL_AUTHOR_TS,$COL_GEO_GRID FROM $collectionIdQuoted WHERE id = ANY($1) FOR UPDATE $waitOp"
        val result = if (idsFullFetch.isEmpty()) {
            sql.execute(basicQuery, arrayOf(idsSmallFetch.toTypedArray()))
        } else {
            val complexQuery = """
                with 
                small as ($basicQuery),
                remaining as (SELECT $COL_ID, $COL_TXN_NEXT,$COL_PTXN,$COL_PUID,$COL_GEO_TYPE,$COL_APP_ID,$COL_TAGS,$COL_GEOMETRY,$COL_FEATURE,$COL_GEO_REF,$COL_TYPE FROM $collectionIdQuoted WHERE id = ANY($2))
                select * from small s left join remaining r on s.$COL_ID = r.$COL_ID 
            """.trimIndent()
            sql.execute(complexQuery, arrayOf(idsSmallFetch.toTypedArray(), idsFullFetch.toTypedArray()))
        }
        val rows = sql.rows(result)

        val retMap = newMap()

        if (rows.isNullOrEmpty())
            return retMap

        for (row in rows) {
            val cols = asMap(row)
            retMap.put(cols[COL_ID]!!, cols)
        }
        return retMap
    }
}