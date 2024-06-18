@file:OptIn(ExperimentalJsExport::class)

package naksha.plv8

import kotlinx.datetime.*
import naksha.base.*
import naksha.base.Platform.Companion.logger
import naksha.jbon.IDictManager
import naksha.jbon.JbMap
import naksha.jbon.JbMapFeature
import naksha.jbon.XyzVersion
import naksha.model.*
import naksha.model.request.WriteFeature
import naksha.model.request.WriteRequest
import naksha.model.response.*
import naksha.plv8.Static.SC_TRANSACTIONS
import naksha.plv8.Static.nakshaCollectionConfig
import naksha.plv8.write.RowUpdater
import naksha.plv8.write.SingleCollectionWriter
import naksha.plv8.write.WriteRequestExecutor
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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
    val storage: IStorage,
    var appName: String,
    var streamId: String,
    var appId: String,
    var author: String? = null
) {

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

    internal val rowUpdater = RowUpdater(this)

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

    /**
     * The current transaction number.
     */
    private var _txn: Txn? = null

    /**
     * The epoch milliseconds of when the transaction started (`transaction_timestamp()`).
     */
    private var _txts: Int64? = null

    /**
     * Keeps transaction's counters.
     */
    var transaction: NakshaTransactionProxy = NakshaTransactionProxy()

    /**
     * A cache to remember collections configuration <collectionId, configMap>
     */
    var collectionConfiguration = mutableMapOf<String, NakshaCollectionProxy>()

    /**
     * The last error number as SQLState.
     */
    var errNo: String? = null

    /**
     * The last human-readable error message.
     */
    var errMsg: String? = null

    init {
        sql.execute(
            """
SET SESSION search_path TO $schemaIdent, public, topology;
SET SESSION enable_seqscan = OFF;
"""
        )
        schemaOid = sql.rows(
            sql.execute(
                "SELECT oid FROM pg_namespace WHERE nspname = $1",
                arrayOf(schema)
            )
        )!![0]["oid"] as Int
        collectionConfiguration = mutableMapOf()
        collectionConfiguration.put(NKC_TABLE, nakshaCollectionConfig)
        transaction.id = txn().toGuid(storage.id(), "txn", "txn").toString()
    }

    fun reset(appName: String, streamId: String, appId: String, author: String?) {
        this.author = author
        this.appName = appName
        this.streamId = streamId
        this.appId = appId
        clear()
    }

    fun clear() {
        _txn = null
        uid = 0
        _txts = null
        errNo = null
        errMsg = null
        collectionConfiguration = mutableMapOf()
        collectionConfiguration.put(NKC_TABLE, nakshaCollectionConfig)
        transaction = NakshaTransactionProxy()
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

    /**
     * Session local uid counter.
     */
    private var uid = 0

    /**
     *  Saves OLD in $hst.
     */
    internal fun saveInHst(collectionId: String, OLD: Row) {
        if (isHistoryEnabled(collectionId)) {
            // TODO move it outside and run it once
            val collectionIdQuoted = sql.quoteIdent("${collectionId}\$hst")
            val hstInsertPlan = sql.prepare(
                """INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)""",
                COL_ALL_TYPES
            )
            val oldMeta = OLD.meta!!
            hstInsertPlan.execute(
                arrayOf(
                    oldMeta.txnNext,
                    oldMeta.txn,
                    oldMeta.uid,
                    oldMeta.ptxn,
                    oldMeta.puid,
                    oldMeta.flags,
                    oldMeta.action,
                    oldMeta.version,
                    oldMeta.createdAt,
                    oldMeta.updatedAt,
                    oldMeta.authorTs,
                    oldMeta.author,
                    oldMeta.appId,
                    oldMeta.geoGrid,
                    OLD.id,
                    OLD.tags,
                    OLD.geo,
                    OLD.feature,
                    OLD.geoRef,
                    OLD.type
                )
            )
        }
    }

    internal fun deleteFromDel(collectionId: String, id: String) {
        val collectionIdQuoted = sql.quoteIdent("${collectionId}\$del")
        sql.execute("""DELETE FROM $collectionIdQuoted WHERE id = $1""", arrayOf(id))
    }

    /**
     * Updates xyz namespace and copies feature to $del table.
     */
    internal fun copyToDel(collectionId: String, OLD: Row) {
        val collectionConfig = getCollectionConfig(collectionId)
        val autoPurge: Boolean? = collectionConfig.autoPurge
        if (autoPurge != true) {
            val collectionIdQuoted = sql.quoteIdent("${collectionId}\$del")
            val oldMeta = OLD.meta!!
            sql.execute(
                """INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20)""",
                arrayOf(
                    oldMeta.txnNext,
                    oldMeta.txn,
                    oldMeta.uid,
                    oldMeta.ptxn,
                    oldMeta.puid,
                    oldMeta.flags,
                    oldMeta.action,
                    oldMeta.version,
                    oldMeta.createdAt,
                    oldMeta.updatedAt,
                    oldMeta.authorTs,
                    oldMeta.author,
                    oldMeta.appId,
                    oldMeta.geoGrid,
                    OLD.id,
                    OLD.tags,
                    OLD.geo,
                    OLD.feature,
                    OLD.geoRef,
                    OLD.type
                )
            )
        }
    }

    /**
     * Invoked by the SQL trigger functions. When being used in the JVM, the JVM engine will call
     * this method to simulate triggers.
     * @param data The trigger data, allows the modification of [NkCollectionTrigger.NEW].
     */
    fun triggerBefore(data: PgTrigger) {
        // FIXME
        throw RuntimeException("Forbidden, please use API")
        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
        if (data.TG_OP == TG_OP_INSERT) {
            check(data.NEW != null) { "Missing NEW for INSERT" }
            rowUpdater.xyzInsert(collectionId, data.NEW)
        } else if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            rowUpdater.xyzUpdateHead(collectionId, data.NEW, data.OLD)
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
        throw RuntimeException("Forbidden, please use API")
        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
        if (data.TG_OP == TG_OP_DELETE && data.OLD != null) {
            deleteFromDel(collectionId, data.OLD.id)
            // save current head in hst
            data.OLD.meta?.txnNext = data.OLD.meta?.txn
            saveInHst(collectionId, data.OLD)
            rowUpdater.xyzDel(data.OLD.meta!!)
            copyToDel(collectionId, data.OLD)
            // save del state in hst
            saveInHst(collectionId, data.OLD)
        }
        if (data.TG_OP == TG_OP_UPDATE) {
            check(data.NEW != null) { "Missing NEW for UPDATE" }
            check(data.OLD != null) { "Missing OLD for UPDATE" }
            deleteFromDel(collectionId, data.NEW.id)
            data.OLD.meta?.txnNext = data.NEW.meta?.txn
            saveInHst(collectionId, data.OLD)
        }
        if (data.TG_OP == TG_OP_INSERT) {
            check(data.NEW != null) { "Missing NEW for INSERT" }
            deleteFromDel(collectionId, data.NEW.id)
        }
    }

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
            val row = rows[0]
            // "PostgreSQL 15.5 on aarch64-unknown-linux-gnu, compiled by gcc (GCC) 7.3.1 20180712 (Red Hat 7.3.1-6), 64-bit"
            val versionString: String = row["version"]!! as String
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
    fun txn(): Txn {
        if (_txn == null) {
            val query = """
WITH ns as (SELECT oid FROM pg_namespace WHERE nspname = $1),
     txn_seq as (SELECT cls.oid as oid FROM pg_class cls, ns WHERE cls.relname = 'naksha_txn_seq' and cls.relnamespace = ns.oid)
SELECT nextval(txn_seq.oid) as txn, txn_seq.oid as txn_oid, (extract(epoch from transaction_timestamp())*1000)::int8 as time, ns.oid as ns_oid
FROM ns, txn_seq;"""
            val row = sql.rows(sql.execute(query, arrayOf(schema)))!![0]
            val schemaOid = row["ns_oid"] as Int
            val txnSeqOid = row["txn_oid"] as Int
            val txts = asInt64(row["time"])
            var txn = Txn(asInt64(row["txn"]))
            verifyCache(schemaOid)
            _txts = txts
            _txn = txn
            val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
            val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
            if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                sql.execute("SELECT pg_advisory_lock($1)", arrayOf(Static.TXN_LOCK_ID))
                try {
                    val raw = asInt64(
                        sql.rows(
                            sql.execute(
                                "SELECT nextval($1) as v",
                                arrayOf(txnSeqOid)
                            )
                        )!![0]["v"]
                    )
                    txn = Txn(raw)
                    _txn = txn
                    if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                        // Rollover, we update sequence of the day.
                        txn = Txn.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, Int64(0))
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
    fun txnTs(): Int64 {
        txn()
        return _txts!!
    }

    private fun handleFeatureException(e: Throwable, id: String?): ErrorResponse {
        val err = (e as PlatformMap).proxy(P_JsMap::class)
        // available fields (only on server): sqlerrcode, schema_name, table_name, column_name, datatype_name, constraint_name, detail, hint, context, internalquery, code
        val errCode: String? = (err["sqlerrcode"] ?: err["sqlstate"]) as? String
        return when {
            errCode != null -> {
                ErrorResponse(NakshaError(errCode, e.cause?.message ?: errCode, id))
            }

            else -> {
                if (Static.PRINT_STACK_TRACES)
                    logger.info(e.cause?.message!!)
                ErrorResponse(NakshaError(ERR_FATAL, e.cause?.message ?: "Fatal ${e.stackTraceToString()}", id))
            }
        }
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
                    if (v.isString()) return v.decodeString()
                }
            }
        }
        if (root.selectKey("momType") || root.selectKey("type")) {
            val value = root.value()
            if (value.isString()) return value.decodeString()
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

    fun getCollectionConfig(collectionId: String): NakshaCollectionProxy {
        return if (collectionConfiguration.containsKey(collectionId)) {
            collectionConfiguration[collectionId]!!
        } else {
            val collectionsSearchRows = sql.rows(
                sql.execute(
                    "select $COL_FEATURE, $COL_FLAGS from $NKC_TABLE_ESC where $COL_ID = $1",
                    arrayOf(collectionId)
                )
            )
            if (collectionsSearchRows.isNullOrEmpty()) {
                throw RuntimeException("collection $collectionId does not exist in $NKC_TABLE_ESC")
            }
            val cols = collectionsSearchRows[0]
            val row = Row(
                storage = storage,
                flags = cols[COL_FLAGS]!! as Int,
                feature = cols[COL_FEATURE]!! as ByteArray,
                id = collectionId,
                guid = null
            )
            val nakCollection = row.toMemoryModel()!!.proxy(NakshaCollectionProxy::class)
            collectionConfiguration.put(collectionId, nakCollection)
            nakCollection
        }
    }

    internal fun isHistoryEnabled(collectionId: String): Boolean {
        val isDisabled: Boolean? = getCollectionConfig(collectionId).disableHistory
        return isDisabled != true
    }

    private inner class TransactionAction internal constructor(transaction: NakshaTransactionProxy) {
        private val transactionWriter: SingleCollectionWriter =
            SingleCollectionWriter(SC_TRANSACTIONS, this@NakshaSession, modifyCounters = false)

        fun write() {
            transactionWriter.writeFeatures(
                WriteRequest(
                    arrayOf(WriteFeature(SC_TRANSACTIONS, transaction)),
                    noResults = true
                )
            )
        }
    }

    /**
     * Single threaded all-or-nothing bulk write operation.
     * As result there is row with success or error returned.
     */
    fun write(writeRequest: WriteRequest): Response {
        val executor = WriteRequestExecutor(this, true)
        val transactionAction = TransactionAction(transaction)
        return try {
            transactionAction.write()
            val writeFeaturesResult = executor.write(writeRequest)
            transactionAction.write()
            writeFeaturesResult
        } catch (e: NakshaException) {
            if (Static.PRINT_STACK_TRACES) logger.info(e.stackTraceToString())
            ErrorResponse(NakshaError(e.errNo, e.errMsg))
        } catch (e: Throwable) {
            handleFeatureException(e, null)
        }
    }
}