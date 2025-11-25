@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("LeakingThis")

package naksha.psql

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.AtomicMap
import naksha.base.Int64
import naksha.model.NakshaVersion
import naksha.base.Platform.PlatformCompanion.logger
import naksha.jbon.IDictReader
import naksha.jbon.JbDictionary
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion.ADMIN_MAP
import naksha.model.Naksha.NakshaCompanion.ADMIN_MAP_NUMBER
import naksha.model.Naksha.NakshaCompanion.MAPS_COL_NUMBER
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_ARGUMENT
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.objects.NakshaMap
import naksha.psql.PgColumn.PgColumnCompanion.allColumns
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The admin-map of the storage, requires a platform specific implementation.
 * @property storage the
 * @property schemaOid the `OID` of the admin-map schema.
 * @since 3.0.0
 */
@JsExport
abstract class PgAdminMap internal constructor(
    /**
     * The storage to which this admin-map belongs.
     * @since 3.0.0
     */
    storage: PgStorage,

    /**
     * The configuration as required.
     * @since 3.0.0
     */
    config: PgConfig,

    /**
     * If not _null_, overrides [NakshaStorage.create][naksha.model.objects.NakshaStorage.create].
     * @since 3.0.0
     */
    create: Boolean?,

    /**
     * If not _null_, overrides [NakshaStorage.upgrade][naksha.model.objects.NakshaStorage.upgrade].
     * @since 3.0.0
     */
    upgrade: Boolean?
) : PgMap(storage, NakshaMap().withStorageId(storage.id).withId(ADMIN_MAP)), IDictReader {

    /**
     * The page-size of the database (`current_setting('block_size')`).
     * @since 3.0.0
     */
    val pageSize: Int

    /**
     * The maximum size of a tuple (row).
     * @since 3.0.0
     */
    val maxTupleSize: Int

    /**
     * If there is a special tablespace for temporary tables.
     * @since 3.0.0
     */
    val tempTableSpace: String?

    /**
     * The `OID` of the temporary tablespace.
     * @since 3.0.0
     */
    val tempTableSpaceOid: Int?

    /**
     * If there is a special tablespace for brittle tables.
     * @since 3.0.0
     */
    val brittleTableSpace: String?

    /**
     * The `OID` of the brittle tablespace.
     * @since 3.0.0
     */
    val brittleTableSpaceOid: Int?

    /**
     * If there is a special tablespace for ephemeral tables.
     * @since 3.0.0
     */
    val ephemeralTableSpace: String?

    /**
     * The `OID` of the ephemeral tablespace.
     * @since 3.0.0
     */
    val ephemeralTableSpaceOid: Int?

    /**
     * If the [pgsql-gzip][https://github.com/pramsey/pgsql-gzip] extension is installed, therefore PostgresQL supported `gzip`/`gunzip` as standalone SQL function by the database. Note, that if this is not the case, we're installing code that is implemented in JavaScript.
     * @since 3.0.0
     */
    val gzipExtension: Boolean

    /**
     * The PostgresQL database version.
     * @since 3.0.0
     */
    val postgresVersion: NakshaVersion

    /**
     * The OID of the transaction sequence.
     * @since 3.0.0
     */
    val txnSequenceOid: Int

    /**
     * The OID of the map-number sequence.
     * @since 3.0.0
     */
    //val mapNumberSequenceOid: Int

    /**
     * The `OID` of the admin-map aka admin schema.
     * @since 3.0.0
     */
    val schemaOid: Int

    /**
     * The transactions' collection _(`naksha~transactions` aka `1`)_.
     * @since 3.0.0
     */
    val transactions: PgNakshaTransactions

    /**
     * The maps' collection _(`naksha~maps` aka `2`)_.
     * @since 3.0.0
     */
    val maps: PgNakshaMaps

    /**
     * The dictionaries' collection _(`naksha~dictionaries` aka `3`)_.
     * @since 3.0.0
     */
    val dictionaries: PgNakshaDictionaries

    // Called from invokeInitStorage->initStorage, so within a lock!
    init {
        val id = config.id // storageId
        val number = config.number // storageNumber
        val doOverride = config.override == true
        val doCreate = create ?: config.create
        val doUpgrade = upgrade ?: config.upgrade
        val temp_spcname: String = config.temp_tablespace ?: "temp"
        val brittle_spcname: String = config.brittle_tablespace ?: "brittle"
        val ephemeral_spcname: String = config.ephemeral_tablespace ?: "ephemeral"
        val config_version = config.version
        val psql_version = if (config_version != null) NakshaVersion.of(config_version) else adminVersion

        // Switch to admin context.
        val conn = storage.newConnection(Naksha.adminOptions, false)
        conn.use {
            logger.info("Start initStorage of database {}", conn.toUri())
            conn.autoCommit = false

            logger.info("Query basic database information")
            val has_naksha_version: Boolean?
            val has_naksha_storage_id: Boolean?
            val has_naksha_storage_number: Boolean?
            val admin_schema_oid: Int?
            conn.execute(
                """
WITH basics AS (SELECT 
current_setting('block_size')::int4 AS bs, 
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$temp_spcname') AS temp_oid,
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$brittle_spcname') AS brittle_oid,
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$ephemeral_spcname') AS ephemeral_oid,
(SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'naksha~admin') AS admin_oid,
(SELECT oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') AS gzip_oid,
version() AS version
), procs AS (SELECT 
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_version') AS has_naksha_version,
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_id') AS has_naksha_storage_id,
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_number') AS has_naksha_storage_number
)
SELECT basics.*, procs.* FROM basics, procs;
"""
            ).fetch().use { cursor ->
                pageSize = cursor["bs"]
                val tupleSize = pageSize - 32
                maxTupleSize = if (tupleSize > MAX_POSTGRES_TOAST_TUPLE_TARGET) {
                    MAX_POSTGRES_TOAST_TUPLE_TARGET
                } else if (tupleSize < MIN_POSTGRES_TOAST_TUPLE_TARGET) {
                    MIN_POSTGRES_TOAST_TUPLE_TARGET
                } else {
                    tupleSize
                }
                var raw = cursor.column("temp_oid")
                if (raw is Int) {
                    tempTableSpaceOid = raw
                    tempTableSpace = temp_spcname
                } else {
                    tempTableSpaceOid = null
                    tempTableSpace = null
                }
                raw = cursor.column("brittle_oid")
                if (raw is Int) {
                    brittleTableSpaceOid = raw
                    brittleTableSpace = brittle_spcname
                } else {
                    brittleTableSpaceOid = null
                    brittleTableSpace = null
                }
                raw = cursor.column("ephemeral_oid")
                if (raw is Int) {
                    ephemeralTableSpaceOid = raw
                    ephemeralTableSpace = ephemeral_spcname
                } else {
                    ephemeralTableSpaceOid = null
                    ephemeralTableSpace = null
                }
                gzipExtension = cursor.column("gzip_oid") is Int
                // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
                val v: String = cursor["version"]
                val start = v.indexOf(' ') + 1
                val end = v.indexOf(' ', start)
                postgresVersion = NakshaVersion.of(v.substring(start, end))

                admin_schema_oid = cursor.column("admin_oid") as Int?
                has_naksha_version = cursor.column("has_naksha_version") as Boolean?
                has_naksha_storage_id = cursor.column("has_naksha_storage_id") as Boolean?
                has_naksha_storage_number = cursor.column("has_naksha_storage_number") as Boolean?
            }

            // This only creates the logical structure, no database access is yet done!
            // Beware: We need to do this here, because `PgCollection` back-refers to `maxTupleSize` !
            transactions = PgNakshaTransactions(this)
            dictionaries = PgNakshaDictionaries(this)
            maps = PgNakshaMaps(this)

            if (admin_schema_oid == null) {
                if (!doCreate) throw forbidden("Creation of admin-map needed, but forbidden by config")
                logger.info("Install Naksha admin-map in version $psql_version for storage $id / $number")
                schemaOid = createAdminMap(conn, config, id, number, psql_version)
            } else {
                schemaOid = admin_schema_oid
                if (has_naksha_version != true) {
                    throw illegalState("The storage '$id' does have an admin-map, but it is broken, because function `naksha_version` is missing")
                }
                if (has_naksha_storage_id != true) {
                    throw illegalState("The storage '$id' does have an admin-map, but it is broken, because function `naksha_storage_id` is missing")
                }
                if (has_naksha_storage_number != true) {
                    throw illegalState("The storage '$id' does have an admin-map, but it is broken, because function `naksha_storage_number` is missing")
                }
                var installed_version: NakshaVersion
                var installed_storage_id: String
                var installed_storage_number: Int64
                conn.execute("SELECT \"${ADMIN_MAP}\".naksha_version() AS v, \"${ADMIN_MAP}\".naksha_storage_id() AS id, \"${ADMIN_MAP}\".naksha_storage_number() AS n").fetch().use { cursor ->
                    try {
                        val v: Int64 = cursor["v"]
                        installed_version = NakshaVersion(v)
                        installed_storage_id = cursor["id"]
                        installed_storage_number = cursor["n"]
                    } catch (pe: Exception) {
                        throw illegalState("The storage '$id' does have an admin schema, but it is broken, because reading storage version, id, and/or number failed", pe)
                    }
                }
                if (installed_storage_id != id) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "Failed to initialize storage, the storage-id is '$installed_storage_id', but was expected to be '$id'"
                    )
                }
                if (installed_storage_number != number) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "Failed to initialize the storage, the storage-number is '$installed_storage_number', but was expected to be '$number'"
                    )
                }
                if (installed_version != psql_version) {
                    logger.info("The admin-map of '$id' is in version $installed_version, this library uses version $psql_version")
                    if (doOverride) {
                        logger.warn("Forcefully upgrade storage '$id' admin-map (current=$installed_version, new=$psql_version)")
                        upgradeAdminMap(conn, config, id, number, psql_version, admin_schema_oid, installed_version)
                    } else {
                        if (installed_version > psql_version) {
                            throw illegalState("The storage '$id' is in a newer version ($installed_version) than this library ($psql_version), access denied (otherwise we risk damaging the storage)")
                        }
                        if (installed_version < minAdminVersion) {
                            if (!doUpgrade) {
                                throw illegalState("The storage '$id' is in a newer version ($installed_version) that this library ($psql_version), access denied (there is a risk damaging the storage)")
                            }
                            logger.info("Upgrade Naksha admin-map from $installed_version to $psql_version for storage $id")
                            upgradeAdminMap(conn, config, id, number, psql_version, admin_schema_oid, installed_version)
                        } else if (doUpgrade){
                            logger.info("Upgrade Naksha admin-map from $installed_version to $psql_version for storage $id")
                            upgradeAdminMap(conn, config, id, number, psql_version, admin_schema_oid, installed_version)
                        } else {
                            logger.info("In storage '$id' admin-map is in version $installed_version, this library is version $psql_version, but we should not upgrade the storage, and are okay working with the older version")
                        }
                    }
                } else {
                    logger.info("The admin-map of '$id' is up-to-date: $psql_version")
                }
            }
            logger.info("Load OID of '$NAKSHA_TXN_SEQ' from admin schema (schema-oid=$schemaOid)")
            val SQL = "SELECT oid FROM pg_class WHERE relnamespace = $schemaOid AND relname = '$NAKSHA_TXN_SEQ'"
            conn.execute(SQL).fetch().use { cursor ->
                txnSequenceOid = cursor["oid"]
                //mapNumberSequenceOid = cursor["map_oid"]
                //colNumberSequenceOid = cursor["col_oid"]
            }
            logger.info("Storage ${config.id} / ${config.number} initialized, txn-seq-oid=$txnSequenceOid, commit")
            conn.commit()
        }
    }

    /**
     * Helper to create the admin schema, install all SQL functions, scripts, and create the internal collections for transactions, maps, and dictionaries.
     *
     * **Note**: At the time of calling this method, [schemaOid] has not been initialized!
     * @param conn the connection to use to perform initialization work.
     * @param config the configuration.
     * @param storageId the storage-id to install.
     * @param storageNumber the storage-number to install.
     * @param psqlVersion the PSQL version to install.
     * @return the admin schema `OID`.
     * @since 3.0.0
     */
    protected abstract fun createAdminMap(
        conn: PgConnection,
        config: PgConfig,
        storageId: String,
        storageNumber: Int64,
        psqlVersion: NakshaVersion
    ): Int

    /**
     * Helper to upgrade the admin schema, upgrade all SQL functions, scripts, and upgrade the internal collections in the admin-map.
     *
     * **Note**: At the time of calling this method, [schemaOid] has not been initialized!
     * @param conn the connection to use to perform initialization work.
     * @param config the configuration.
     * @param storageId the storage-id to install.
     * @param storageNumber the storage-number to install.
     * @param psqlVersion the PSQL version to install.
     * @param schemaOid the admin schema `OID` of the current schema.
     * @param installedVersion the PSQL version that is currently installed that should be upgraded, if any (maybe only the schema exists).
     * @since 3.0.0
     */
    protected abstract fun upgradeAdminMap(
        conn: PgConnection,
        config: PgConfig,
        storageId: String,
        storageNumber: Int64,
        psqlVersion: NakshaVersion,
        schemaOid: Int,
        installedVersion: NakshaVersion?
    )

    /**
     * Returns the current transaction-number, so the last used one.
     *
     * **Beware**: The returned transaction number can be located long in the past, days or month ago.
     * @param conn the connection to use to access the database.
     * @return the next _(unused)_ transaction-number.
     * @since 3.0.0
     */
    fun getTxn(conn: PgConnection): Int64 {
        val QUERY = "SELECT currval($1) as txn"
        val cursor = conn.execute(QUERY, arrayOf(txnSequenceOid)).fetch()
        cursor.use {
            val txn: Int64 = cursor["txn"]
            return txn
        }
    }

    /**
     * Allocate a new transaction-number and return the details of it.
     * @param conn the connection to use to access the database.
     * @return the allocated transaction-number.
     * @since 3.0.0
     */
    fun newTxn(conn: PgConnection): PgTxn {
        val QUERY = "SELECT nextval($1) as txn, (extract(epoch from transaction_timestamp())*1000)::int8 as time"
        val cursor = conn.execute(QUERY, arrayOf(txnSequenceOid)).fetch()
        cursor.use {
            var txn: Int64 = cursor["txn"]
            val txts: Int64 = cursor["time"]
            var version = Version(txn)
            val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
            val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
            if (version.year != txDate.year || version.month != txDate.monthNumber || version.day != txDate.dayOfMonth) {
                logger.info("Transaction counter is in wrong day")
                logger.info("Acquire advisory lock")
                conn.execute("SELECT pg_advisory_lock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                try {
                    val c2 = conn.execute("SELECT nextval($1) as txn", arrayOf(txnSequenceOid)).fetch()
                    c2.use {
                        txn = c2["txn"]
                        version = Version(txn)
                    }
                    if (version.year != txDate.year || version.month != txDate.monthNumber || version.day != txDate.dayOfMonth) {
                        logger.info("Transaction counter is still at wrong day, rollover to next day")
                        // Rollover, we update sequence of the day.
                        version = Version.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, Int64(1))
                        txn = version.txn
                        conn.execute("SELECT setval($1, $2)", arrayOf(txnSequenceOid, txn + 1)).close()
                    }
                    logger.info("Release advisory lock")
                    conn.execute("SELECT pg_advisory_unlock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                } catch (e: Throwable) {
                    logger.error("Fatal exception while holding an advisory lock, terminating connection: {}", e)
                    // This must not happen, to release the advisory lock, we need to terminate the connection!
                    conn.terminate()
                    throw NakshaException(
                        EXCEPTION,
                        "Failed to increment 'txn', exception while holding advisory lock, terminating connection"
                    )
                }
            }
            // Note: We know, that we only get a new transaction number before we start a transaction.
            //       Doing a commit here is necessary to avoid that we get a lock to the txn sequence!
            //       Even while sequences are normally not locked, it can happen under circumstances.
            conn.commit()
            return PgTxn(txn, txts, version)
        }
    }

    /**
     * Returns the current map-number, so the last used one.
     * @param conn the connection to use to access the database.
     * @return the current _(last used)_ map-number.
     * @since 3.0.0
     */
//    fun getMapNumber(conn: PgConnection): Int {
//        val QUERY = "SELECT currval($1) as mapnum"
//        val cursor = conn.execute(QUERY, arrayOf(mapNumberSequenceOid)).fetch()
//        cursor.use {
//            val number: Int = cursor["mapnum"]
//            return number
//        }
//    }

    /**
     * Allocate a new map-number.
     * @param conn the connection to use to access the database.
     * @return the allocated map-number.
     * @since 3.0.0
     */
//    fun newMapNumber(conn: PgConnection): Int {
//        val QUERY = "SELECT nextval($1) as mapnum"
//        val cursor = conn.execute(QUERY, arrayOf(mapNumberSequenceOid)).fetch()
//        cursor.use {
//            val number: Int = cursor["mapnum"]
//            return number
//        }
//    }

    // TODO: Implement the methods, then make them open, so we can override them for the JVM implementation
    //       We only want to cache in JVM, not within the database!

    protected val mapCache = AtomicMap<Int, PgMap>()
    protected val mapNumberById = AtomicMap<String, Int>()

    protected fun storeMap(map: PgMap) {
        mapNumberById[map.id] = map.number
        // TODO: Improve this, we should keep the PgMap that has the higher version!
        mapCache[map.number] = map
    }

    protected fun invalidateMap(map: PgMap) {
        mapCache.remove(map.number, map)
        //mapNumberById.remove(map.id, map.number)
    }

    /**
     * Create a new [map][PgMap] using the given connection, and return it.
     *
     * ### Note
     * Does not commit the given connection, therefore the map is not yet persisted, but can be used through the given connection. The method neither creates the corresponding entry in the collection's collection of the admin-map, it only creates the schema and collection-number sequence counter!
     *
     * - Throws [NakshaError.MAP_EXISTS] if such a map exists already.
     * @param conn the connection to use to access the database.
     * @param map the map to create.
     * @return the created map.
     * @since 3.0.0
     */
    fun createPgMap(conn: PgConnection, map: PgMap) {
        if (Naksha.isInternalId(map.id)) throw NakshaException(ILLEGAL_ARGUMENT, "Can't create internal maps: ${map.id}")
        conn.execute("CREATE SCHEMA IF NOT EXISTS ${map.quotedId}").close()
        map.createPgCollection(conn, map.collections) // 0
        invalidateMap(map)
    }

    /**
     * Delete a map.
     *
     * ### Note
     * Does not commit the given connection, therefore the map is not yet physically deleted. The method neither deletes the corresponding entry from the collection's collection of the admin-map, it only drops the schema!
     * @param conn the connection to use to access the database.
     * @param map the map to delete.
     * @since 3.0.0
     */
    fun deletePgMap(conn: PgConnection, map: PgMap) {
        if (Naksha.isInternalId(map.id)) throw NakshaException(ILLEGAL_ARGUMENT, "Can't delete internal maps: ${map.id}")
        conn.execute("DROP SCHEMA ${map.quotedId} CASCADE").close()
        invalidateMap(map)
    }

    /**
     * Returns the existing map with the given identifier; if any.
     * @param conn the connection to use to access the database.
     * @param id the map-id to query.
     * @return the map, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    fun getPgMapById(conn: PgConnection?, id: String): PgMap? {
        if (ADMIN_MAP == id) return this
        val number = mapNumberById[id]
        val existing = if (number != null) mapCache[number] else null
        if (existing != null) return existing
        if (conn == null) return null

        // Read from database
        val outRows = PgColumnRows()
            .withStorageNumber(storage.number)
            .withMapNumber(ADMIN_MAP_NUMBER)
            .withCollectionNumber(MAPS_COL_NUMBER)
            .addColumns(allColumns)
        val SQL = """SELECT ${outRows.names()}
FROM "naksha~admin".${maps.headTable.quotedName}
WHERE id = $1"""
        val plan = conn.prepare(SQL, arrayOf(PgType.STRING.text))
        plan.execute(arrayOf(id)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaMap = Naksha.decodeTuple(tuple).proxy(NakshaMap::class)
        val pgMap = PgMap(storage, nakshaMap)
        storeMap(pgMap)
        return pgMap
    }

    /**
     * Returns the existing map with the given number; if any.
     * @param conn the connection to use to access the database.
     * @param number the map-number to query.
     * @return the map, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    fun getPgMapByNumber(conn: PgConnection?, number: Int): PgMap? {
        if (ADMIN_MAP_NUMBER == number) return this
        val existing = mapCache[number]
        if (existing != null) return existing
        if (conn == null) return null

        // Read from database
        val outRows = PgColumnRows()
            .withStorageNumber(storage.number)
            .withMapNumber(ADMIN_MAP_NUMBER)
            .withCollectionNumber(MAPS_COL_NUMBER)
            .addColumns(allColumns)
        val SQL = """
            SELECT ${outRows.names()}
            FROM "naksha~admin".${maps.headTable.quotedName}
            WHERE naksha_tn_feature_number(tn) = $1
            """.trimIndent()
        val plan = conn.prepare(SQL, arrayOf(PgType.INT64.text))
        conn.execute(getSearchPath())
        plan.execute(arrayOf(number)).fetch().use {
            outRows.addAll(cursor = it)
        }
        if (outRows.size == 0) return null
        val tuple = outRows[0] ?: return null
        Naksha.cache.store(tuple)
        val nakshaMap = Naksha.decodeTuple(tuple).proxy(NakshaMap::class)
        val pgMap = PgMap(storage, nakshaMap)
        storeMap(pgMap)
        return pgMap
    }

    /**
     * Returns a list of all existing maps, excluding the admin-map.
     * @param conn the connection to use to access the database.
     * @return the list of existing maps, _(empty, when no maps exist)_.
     * @since 3.0.0
     */
    fun listPgMaps(conn: PgConnection): PgMapList
        = PgMapList().withAll(mapCache.mapNotNull { it.value })
    // TODO: This only reads the cache, but we need to load from database!

    abstract fun getEncodingFlags(feature: Any?, context: Any?): Flags

    abstract override fun getDictionary(id: String): JbDictionary?

    abstract override fun getEncodingDictionary(feature: Any?, context: Any?): JbDictionary?
}