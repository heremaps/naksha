@file:OptIn(ExperimentalJsExport::class)

package naksha.psql

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import naksha.base.Epoch
import naksha.base.Int64
import naksha.base.Platform.PlatformCompanion.logger
import naksha.model.*
import naksha.model.Naksha.NakshaCompanion
import naksha.model.NakshaError.NakshaErrorCompanion.EXCEPTION
import naksha.model.NakshaError.NakshaErrorCompanion.FORBIDDEN
import naksha.model.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.model.NakshaError.NakshaErrorCompanion.STORAGE_ID_MISMATCH
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaMap
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
     */
    config: PgConfig,

    /**
     * If not _null_, overrides [StorageConfig.create].
     */
    create: Boolean?,

    /**
     * If not _null_, overrides [StorageConfig.upgrade].
     */
    upgrade: Boolean?
) : PgMap(storage, Naksha.ADMIN_MAP, Naksha.ADMIN_MAP_NUMBER, 0) {
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
    val mapNumberSequenceOid: Int

    /**
     * The `OID` of the admin-map aka admin schema.
     * @since 3.0.0
     */
    val schemaOid: Int

    /**
     * The transactions' collection.
     * @since 3.0.0
     */
    val transactions: PgNakshaTransactions

    /**
     * The dictionaries' collection.
     * @since 3.0.0
     */
    val dictionaries: PgNakshaDictionaries

    /**
     * The maps' collection.
     * @since 3.0.0
     */
    val maps: PgNakshaMaps

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
        val psql_version = if (config_version != null) NakshaVersion.of(config_version) else NakshaVersion.latest

        // This only creates the logical structure, no database access is yet done!
        transactions = PgNakshaTransactions(this)
        dictionaries = PgNakshaDictionaries(this)
        maps = PgNakshaMaps(this)

        // Switch to admin context.
        val conn = storage.newConnection(Naksha.adminOptions, false)
        conn.use {
            logger.info("Start initStorage of database {}", conn.toUri())
            conn.autoCommit = false

            logger.info("Query basic database information")
            var cursor = conn.execute(
                """
WITH basics AS (SELECT 
current_setting('block_size')::int4 AS bs, 
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$temp_spcname') AS temp_oid,
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$brittle_spcname') AS brittle_oid,
(SELECT oid FROM pg_catalog.pg_tablespace WHERE spcname = '$ephemeral_spcname') AS ephemeral_oid,
(SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = '${Naksha.ADMIN_MAP}') AS admin_oid,
(SELECT oid FROM pg_catalog.pg_extension WHERE extname = 'gzip') AS gzip_oid,
version() AS version
), procs AS (SELECT 
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_version') AS has_naksha_version,
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_id') AS has_naksha_storage_id,
(SELECT true FROM pg_catalog.pg_proc, basics WHERE pronamespace = basics.admin_oid AND proname = 'naksha_storage_number') AS has_naksha_storage_number
)
SELECT basics.*, procs.* FROM basics, procs;
"""
            ).fetch()
            val has_naksha_version: Boolean?
            val has_naksha_storage_id: Boolean?
            val has_naksha_storage_number: Boolean?
            val admin_schema_oid: Int?
            cursor.use {
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

                admin_schema_oid = cursor["admin_oid"]
                has_naksha_version = cursor["has_naksha_version"]
                has_naksha_storage_id = cursor["has_naksha_storage_id"]
                has_naksha_storage_number = cursor["has_naksha_storage_number"]
            }
            // Note: PostgresQL parses the query before it evaluates it, therefore, we must not access a schema that does not exist.
            //       This forces us to execute the version read as a second query, ones we are sure that the schema and function exist.
            var installed_version: NakshaVersion? = null
            var installed_storage_id: String? = null
            var installed_storage_number: Int64? = null
            if (admin_schema_oid != null && has_naksha_version == true && has_naksha_storage_id == true && has_naksha_storage_number == true) {
                cursor =
                    conn.execute("SELECT \"${Naksha.ADMIN_MAP}\".naksha_version() AS v, \"${Naksha.ADMIN_MAP}\".naksha_storage_id() AS id, \"${Naksha.ADMIN_MAP}\".naksha_storage_number() AS n")
                        .fetch()
                val v: Int64 = cursor["v"]
                installed_version = NakshaVersion(v)
                installed_storage_id = cursor["id"]
                installed_storage_number = cursor["n"]
            }
            if (doOverride || installed_version != psql_version || admin_schema_oid == null) {
                if (!NakshaContext.currentContext().su) {
                    throw NakshaException(FORBIDDEN, "Admin privileges required to create or upgrade, please set 'su' flag in context")
                }
                if (installed_storage_id != null && installed_storage_id != id) {
                    throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '$installed_storage_id', but is expected to be '$id'")
                }
                if (installed_storage_number != null && installed_storage_number != number) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "The storage-number is '$installed_storage_number', but is expected to be '$number'"
                    )
                }
                if (admin_schema_oid == null) {
                    if (!doCreate) throw NakshaException(FORBIDDEN, "Creation of admin-map needed, but forbidden")
                    logger.info("Install Naksha admin schema in version $psql_version for storage $id / $number")
                    schemaOid = createAdminMap(config, id, number, psql_version)
                } else {
                    if (!doUpgrade) throw NakshaException(FORBIDDEN, "Upgrade of admin-map needed, but forbidden")
                    logger.info("Upgrade Naksha admin schema from $installed_version to $psql_version for storage $id / $number")
                    upgradeAdminMap(config, id, number, psql_version, admin_schema_oid, installed_version)
                    schemaOid = admin_schema_oid
                }
                logger.info("Installation done, commit changes")
                conn.commit()
            } else {
                if (installed_storage_id != id) {
                    throw NakshaException(STORAGE_ID_MISMATCH, "The storage-id is '$installed_storage_id', but is expected to be '$id'")
                }
                if (installed_storage_number != number) {
                    throw NakshaException(
                        STORAGE_ID_MISMATCH,
                        "The storage-number is '$installed_storage_number', but is expected to be '$number'"
                    )
                }
                schemaOid = admin_schema_oid
            }
            logger.info("Load OID of sequence counters from admin schema (oid=$schemaOid)")
            cursor = conn.execute(
                """SELECT 
(SELECT oid FROM pg_class WHERE relnamespace = $schemaOid AND relname = '$NAKSHA_TXN_SEQ') AS txn_oid,
(SELECT oid FROM pg_class WHERE relnamespace = $schemaOid AND relname = '$NAKSHA_MAP_SEQ') AS map_oid,
(SELECT oid FROM pg_class WHERE relnamespace = $schemaOid AND relname = '$NAKSHA_COL_SEQ') AS col_oid
"""
            ).fetch()
            cursor.use {
                txnSequenceOid = cursor["txn_oid"]
                mapNumberSequenceOid = cursor["map_oid"]
                colNumberSequenceOid = cursor["col_oid"]
            }
            logger.info("Storage ${config.id} / ${config.number} initialized, txn-seq-oid=$txnSequenceOid, map-seq-oid=$mapNumberSequenceOid")
        }
    }

    /**
     * Helper to create the admin schema, install all SQL functions, scripts, and create the internal collections for transactions, maps, and dictionaries.
     *
     * **Note**: At the time of calling this method, [schemaOid] has not been initialized!
     * @param config the configuration.
     * @param storageId the storage-id to install.
     * @param storageNumber the storage-number to install.
     * @param psqlVersion the PSQL version to install.
     * @return the admin schema `OID`.
     * @since 3.0.0
     */
    protected abstract fun createAdminMap(config: PgConfig, storageId: String, storageNumber: Int64, psqlVersion: NakshaVersion): Int

    /**
     * Helper to upgrade the admin schema, upgrade all SQL functions, scripts, and upgrade the internal collections in the admin-map.
     *
     * **Note**: At the time of calling this method, [schemaOid] has not been initialized!
     * @param config the configuration.
     * @param storageId the storage-id to install.
     * @param storageNumber the storage-number to install.
     * @param psqlVersion the PSQL version to install.
     * @param schemaOid the admin schema `OID` of the current schema.
     * @param installedVersion the PSQL version that is currently installed that should be upgraded, if any (maybe only the schema exists).
     * @since 3.0.0
     */
    protected abstract fun upgradeAdminMap(
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
            if (version.year() != txDate.year || version.month() != txDate.monthNumber || version.day() != txDate.dayOfMonth) {
                logger.info("Transaction counter is in wrong day, acquire advisory lock")
                conn.execute("SELECT pg_advisory_lock($1)", arrayOf(PgUtil.TXN_LOCK_ID)).close()
                try {
                    val c2 = conn.execute("SELECT nextval($1) as txn", arrayOf(txnSequenceOid)).fetch()
                    c2.use {
                        txn = c2["txn"]
                        version = Version(txn)
                    }
                    if (version.year() != txDate.year || version.month() != txDate.monthNumber || version.day() != txDate.dayOfMonth) {
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
            return PgTxn(txn, txts, version)
        }
    }

    /**
     * Returns the current map-number, so the last used one.
     * @param conn the connection to use to access the database.
     * @return the current _(last used)_ map-number.
     * @since 3.0.0
     */
    fun getMapNumber(conn: PgConnection): Int {
        val QUERY = "SELECT currval($1) as mapnum"
        val cursor = conn.execute(QUERY, arrayOf(mapNumberSequenceOid)).fetch()
        cursor.use {
            val mapNumber: Int = cursor["mapnum"]
            return mapNumber
        }
    }

    /**
     * Allocate a new map-number.
     * @param conn the connection to use to access the database.
     * @return the allocated map-number.
     * @since 3.0.0
     */
    fun newMapNumber(conn: PgConnection): Int {
        val QUERY = "SELECT nextval($1) as mapnum"
        val cursor = conn.execute(QUERY, arrayOf(mapNumberSequenceOid)).fetch()
        cursor.use {
            val mapNumber: Int = cursor["mapnum"]
            return mapNumber
        }
    }

    // TODO: Implement the methods, then make them open, so we can override them for the JVM implementation
    //       We only want to cache in JVM, not within the database!

    /**
     * Create a new [map][PgMap] using the given connection, and return it.
     *
     * **Note**: Does not commit the given connection, therefore the map is not yet persisted, but can be used through the given connection.
     *
     * - Throws [NakshaError.MAP_EXISTS] if such a map exists already.
     * @param conn the connection to use to access the database.
     * @param map the map to create.
     * @return the created map.
     * @since 3.0.0
     */
    abstract fun createMap(conn: PgConnection, map: NakshaMap): PgMap

    /**
     * Delete a map.
     * @param conn the connection to use to access the database.
     * @param map the map to delete.
     * @since 3.0.0
     */
    abstract fun deleteMap(conn: PgConnection, map: PgMap)

    /**
     * Returns the existing map with the given identifier; if any.
     * @param conn the connection to use to access the database.
     * @param id the map-id to query.
     * @return the map, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    abstract fun getMapById(conn: PgConnection, id: String): PgMap?

    /**
     * Returns the existing map with the given number; if any.
     * @param conn the connection to use to access the database.
     * @param number the map-number to query.
     * @return the map, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    abstract fun getMapByNumber(conn: PgConnection, number: Int): PgMap?

    /**
     * Returns a list of all existing maps, excluding the admin-map.
     * @param conn the connection to use to access the database.
     * @return the list of existing maps, _(empty, when no maps exist)_.
     * @since 3.0.0
     */
    abstract fun listMaps(conn: PgConnection): PgMapList

    /**
     * Create a new [collection][PgCollection] using the given connection, and return it.
     *
     * **Note**: Does not commit the given connection, therefore the collection is not yet persisted, but can be used through the given connection.
     *
     * - Throws [NakshaError.MAP_NOT_FOUND] if the given map does not exist _(anymore)_.
     * - Throws [NakshaError.COLLECTION_EXISTS] if such a collection exists already in the given map.
     * @param conn the connection to use to access the database.
     * @param map the map in which to create the collection.
     * @param collection the collection to create.
     * @return the created map.
     * @since 3.0.0
     */
    fun createNakshaCollection(conn: PgConnection, map: PgMap, collection: NakshaCollection): PgCollection {
        val c = PgCollection(map, collection)
        createPgCollection(conn, c)
        return c
    }

    /**
     * Create a new [collection][PgCollection] using the given connection, and return it.
     *
     * **Note**: Does not commit the given connection, therefore the collection is not yet persisted, but can be used through the given connection.
     *
     * - Throws [NakshaError.MAP_NOT_FOUND] if the given map does not exist _(anymore)_.
     * - Throws [NakshaError.COLLECTION_EXISTS] if such a collection exists already in the given map.
     * @param conn the connection to use to access the database.
     * @param collection the collection to create.
     * @return the created map.
     * @since 3.0.0
     */
    open fun createPgCollection(conn: PgConnection, collection: PgCollection) {
        val indices: List<PgIndex> = mutableListOf()
        for (index in collection.nakshaCollection.indices) {
            // TODO: Fill indices!
        }
        val NOW = Epoch()

        if (collection is PgNakshaTransactions) {
            val txn = PgTransactions(collection)
            txn.create(conn)
            txn.createYear(conn, NOW.year)
            txn.createYear(conn, NOW.year + 1)
            txn.createIndex(conn, PgIndex.tn_pkey)
            txn.createIndex(conn, PgIndex.txn_unique)
            for (index in indices) if (index != PgIndex.tn_pkey && index != PgIndex.txn_unique) txn.createIndex(conn, index)

            // We can have a meta table for transactions, but no history or deleted!
            if (collection.meta != null) {
                val meta = PgMeta(txn)
                meta.create(conn)
                meta.createIndex(conn, PgIndex.tn_pkey)
                meta.createIndex(conn, PgIndex.id_unique)
                for (index in indices) if (index != PgIndex.tn_pkey && index != PgIndex.id_unique) meta.createIndex(conn, index)
            }
            return
        }

        val head = collection.head
        head.create(conn)
        head.createIndex(conn, PgIndex.tn_pkey)
        head.createIndex(conn, PgIndex.id_unique)
        for (index in indices) if (index != PgIndex.tn_pkey && index != PgIndex.id_unique) head.createIndex(conn, index)

        val deleted = collection.deleted
        if (deleted != null) {
            deleted.create(conn)
            deleted.createIndex(conn, PgIndex.tn_pkey)
            deleted.createIndex(conn, PgIndex.id_unique)
            for (index in indices) if (index != PgIndex.tn_pkey && index != PgIndex.id_unique) deleted.createIndex(conn, index)
        }

        val meta = collection.meta
        if (meta != null) {
            meta.create(conn)
            meta.createIndex(conn, PgIndex.tn_pkey)
            meta.createIndex(conn, PgIndex.id_unique)
            for (index in indices) if (index != PgIndex.tn_pkey && index != PgIndex.id_unique) meta.createIndex(conn, index)
        }

        val history = collection.history
        if (history != null) {
            history.create(conn)
            history.createYear(conn, NOW.year)
            history.createYear(conn, NOW.year + 1)
            history.createIndex(conn, PgIndex.tn_pkey)
            //history.createIndex(conn, PgIndex.id_txn_uid_unique)
            for (index in indices) {
                if (index != PgIndex.tn_pkey
                    //&& index != PgIndex.id_txn_uid_unique
                    // We do not need this index, because it would only duplicate the stronger unique one!
                    && index != PgIndex.id_txn_uid) history.createIndex(conn, index)
            }
        }
    }

    /**
     * Refresh the cached information of this collection, mainly updates the history tables.
     * - Throws [NakshaError.COLLECTION_NOT_FOUND], if the collection has been deleted.
     * @param conn the connection to query the database; if _null_, a new data connection is acquired, used, and released.
     * @since 3.0.0
     */
    open fun refreshPgCollection(conn: PgConnection, collection: PgCollection): PgCollection {
        // TODO: Fix me!
        val cursor = PgRelation.select(conn, collection.map.id, id)
        cursor.use {
            //
            // NOTE: We ignore all unknown relations, that allows users to add some own indices and relations!
            //
            var headRelation: PgRelation? = null
            val headIndices: MutableList<PgIndex> = mutableListOf()
            val headPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            val headYears: MutableMap<Int, PgRelation> = mutableMapOf()
            var deletedRelation: PgRelation? = null
            val deletedIndices: MutableList<PgIndex> = mutableListOf()
            val deletedPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            var historyRelation: PgRelation? = null
            val historyIndices: MutableList<PgIndex> = mutableListOf()
            val historyYears: MutableMap<Int, PgRelation> = mutableMapOf()
            val historyPartitions: MutableMap<Int, PgRelation> = mutableMapOf()
            var metaRelation: PgRelation? = null
            val metaIndices: MutableList<PgIndex> = mutableListOf()
            while (cursor.next()) {
                val rel = PgRelation(cursor)
                if (id == Naksha.TRANSACTIONS_COL) {
                    // We know that the transaction table does only have a HEAD.
                    // We further know, that head is split yearly!
                    if (rel.isAnyHeadRelation()) {
                        if (rel.isHeadRootRelation()) {
                            headRelation = rel
                        } else if (rel.isTxnYearRelation()) {
                            val year = rel.year()
                            if (year > 0) headYears[year] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in headIndices) headIndices.add(index)
                        }
                    }
                } else {
                    if (rel.isAnyHeadRelation()) {
                        if (rel.isHeadRootRelation()) {
                            headRelation = rel
                        } else if (rel.isTable()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) headPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in headIndices) headIndices.add(index)
                        }
                    }
                    if (rel.isAnyDeleteRelation()) {
                        if (rel.isDeleteRootRelation()) {
                            deletedRelation = rel
                        } else if (rel.isTable()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) deletedPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in deletedIndices) deletedIndices.add(index)
                        }
                    }
                    if (rel.isAnyHistoryRelation()) {
                        if (rel.isHistoryRootRelation()) {
                            historyRelation = rel
                        } else if (rel.isHistoryYearRelation()) {
                            val year = rel.year()
                            if (year > 0) historyYears[year] = rel
                        } else if (rel.isHistoryPartition()) {
                            val i = rel.partitionNumber()
                            if (i >= 0) historyPartitions[i] = rel
                        } else if (rel.isIndex()) {
                            val index = PgIndex.of(rel.name)
                            if (index != null && index !in historyIndices) historyIndices.add(index)
                        }
                    }
                }
                if (rel.isAnyMetaRelation()) {
                    if (rel.isMetaRootRelation()) {
                        metaRelation = rel
                    } else if (rel.isIndex()) {
                        val index = PgIndex.of(rel.name)
                        if (index != null && index !in metaIndices) metaIndices.add(index)
                    }
                }
            }

            if (headRelation != null) {
                if (headRelation.isPartition()) {
                    val parts = headPartitions.size
                    if (parts == 0 && headYears.isNotEmpty()) {
                        val txn = PgTransactions(this as PgNakshaTransactions)
                        for (entry in historyYears) txn.years[entry.key] = PgTransactionsYear(txn, entry.key)
                        head = txn
                    } else {
                        if (parts < 2 || parts > 256) {
                            throw NakshaException(
                                ILLEGAL_STATE,
                                "Invalid amount of HEAD partitions found, must be 2..256, but is ${headPartitions.size}"
                            )
                        }
                        collection.head = PgHead(collection, headRelation.storageClass, parts)
                    }
                } else {
                    collection.head = PgHead(collection, headRelation.storageClass, 0)
                }
                for (index in headIndices) collection.head.addIndex(index)
            }
            if (historyRelation != null) {
                val history = PgHistory(collection.head)
                collection.history = history
                for (entry in historyYears) history.years[entry.key] = PgHistoryYear(history, entry.key)
            }
            if (deletedRelation != null) {
                val deleted = PgDeleted(collection.head)
                collection.deleted = deleted
                for (index in deletedIndices) deleted.addIndex(index)
            }
            if (metaRelation != null) {
                val meta = PgMeta(collection.head)
                collection.meta = meta
                for (index in metaIndices) meta.addIndex(index)
            }
        }
        return collection
    }

    /**
     * Deletes a collection.
     * @param conn the connection to use to access the database.
     * @param collection the collection to delete.
     * @since 3.0.0
     */
    open fun deletePgCollection(conn: PgConnection, collection: PgCollection) {
        var SQL = "DROP TABLE IF EXISTS ${collection.head.quotedName} CASCADE;"
        val history = collection.history
        if (history != null) SQL += "DROP TABLE IF EXISTS ${history.quotedName} CASCADE;"
        val deleted = collection.deleted
        if (deleted != null) SQL += "DROP TABLE IF EXISTS ${deleted.quotedName} CASCADE;"
        val meta = collections.meta
        if (meta != null) SQL += "DROP TABLE IF EXISTS ${meta.quotedName} CASCADE;"
        logger.info("Drop collection {}: {}", collection.id, SQL)
        conn.execute(SQL).close()
    }

    /**
     * Returns the existing collection with the given identifier; if any.
     * @param conn the connection to use to access the database.
     * @param map the map in which to search for the collection.
     * @param id the collection-id to query.
     * @return the collection, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    abstract fun getPgCollectionById(conn: PgConnection, map: PgMap, id: String): PgCollection?

    /**
     * Returns the existing collection with the given number; if any.
     * @param conn the connection to use to access the database.
     * @param map the map in which to search for the collection.
     * @param number the collection-number to query.
     * @return the collection, if it exists; _null_ otherwise.
     * @since 3.0.0
     */
    abstract fun getPgCollectionByNumber(conn: PgConnection, map: PgMap, number: Int): PgCollection?

    /**
     * Returns a list of all existing collections in the map, excluding the collections' collection.
     * @param conn the connection to use to access the database.
     * @param map the map in which to search for the collection.
     * @return the list of existing collections, _(empty, when no collections exist)_.
     * @since 3.0.0
     */
    abstract fun listPgCollections(conn: PgConnection, map: PgMap): PgCollectionList
}


/*

    /**
     * All cached maps.
     * @since 3.0.0
     */
    internal val maps: AtomicMap<String, PgMap> = Platform.newAtomicMap()

    /**
     * A map between unique map-number and a map-identifier.
     *
     * We know that every map-number is always, permanently and immutably, bound to the same map-id, therefore this cache can be maintained next to the primary atomic id to object map.
     * @since 3.0.0
     */
    internal val mapNumberToId: AtomicMap<Int, String> = Platform.newAtomicMap()

*/
