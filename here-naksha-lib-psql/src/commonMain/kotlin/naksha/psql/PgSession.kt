package naksha.psql

import naksha.jbon.*
import kotlinx.datetime.*
import naksha.base.*
import naksha.base.Platform.logger
import naksha.jbon.IDictManager
import naksha.jbon.JbMap
import naksha.jbon.JbMapFeature
import naksha.jbon.XyzVersion
import naksha.model.*
import naksha.model.request.*
import naksha.model.response.*
import naksha.psql.PgStatic.TRANSACTIONS_COL
import naksha.psql.PgStatic.nakshaCollectionConfig
import naksha.psql.read.ReadQueryBuilder
import naksha.psql.write.RowUpdater
import naksha.psql.write.SingleCollectionWriter
import naksha.psql.write.WriteRequestExecutor

/**
 * A session linked to a PostgresQL database.
 *
 * @constructor Create a new session.
 * @param storage the storage to which this session is bound.
 * @param options the default options to use, when opening new database connections.
 */
// @JsExport // <-- when uncommenting this, we get a compiler error!
class PgSession(storage: PgStorage, options: PgOptions) : AbstractSession<Any>(storage, options), PgTx {

    // TODO: Add a NakshaCollectionRow, which should hold the reference to a Row of the collection, plus
    //       the JbNakshaCollection, which is a JBON reader of the collection JBON. We need this, because
    //       for caching we want the data to be immutable, otherwise we can't cache it internally.
    //       We can return the cached row then to the client, when he asks for the collection.
    // Note: We should remove the 'estimatedFeatureCount' and the 'estimatedDeletedCount' from the JBON.
    //       These values are always calculated on-the-fly, and should not be persisted, but the can be
    //       cached at the NakshaCollectionRow, so that the values can be added into the collection feature,
    //       when the client decodes it. This need to be done on-the-fly while decoding the row into a feature!

    /**
     * The cached quoted schema name (double quotes).
     */
    internal val schemaIdent = PgUtil.quoteIdent(options.schema)

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
    var transaction: NakshaTransactionProxy? = null

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

    // TODO: Remove this, creating a new session should be free of cost, we can do this lazy!
    init {
            collectionConfiguration = mutableMapOf()
            collectionConfiguration[NKC_TABLE] = nakshaCollectionConfig
    }

    fun reset() {
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
        transaction = null
    }

    /**
     * Notify the session of the latest schema-oid detected. This should clear caching, when such a change is detected, which can
     * happen for example, when the client drops and re-creates the schema this session is bound to.
     * @param oid The new schema oid.
     */
    internal fun verifyCache(oid: Int) {
        if (usePgConnection().info().schemaOid != oid) {
            clear()
        }
    }

    /**
     * Session local uid counter.
     */
    private var uid = 0

    /**
     * Saves OLD in $hst.
     *
     * Uses the current session, does not commit or rollback the current session.
     */
    internal fun saveInHst(collectionId: String, OLD: Row) {
        if (isHistoryEnabled(collectionId)) {
            // TODO move it outside and run it once
            val collectionIdQuoted = PgUtil.quoteIdent("${collectionId}\$hst")
            val session = usePgConnection()
            val hstInsertPlan = session.prepare("INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($COL_ALL_DOLLAR)", COL_ALL_TYPES)
            hstInsertPlan.use {
                val oldMeta = OLD.meta!!
                hstInsertPlan.execute(
                    arrayOf(
                        oldMeta.txnNext,
                        oldMeta.txn,
                        oldMeta.uid,
                        oldMeta.ptxn,
                        oldMeta.puid,
                        oldMeta.flags,
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
                        OLD.type,
                        oldMeta.fnva1
                    )
                )
            }
        }
    }

    /**
     * Delete the feature from the shadow table.
     *
     * Uses the current session, does not commit or rollback the current session.
     */
    internal fun deleteFromDel(collectionId: String, id: String) {
        val collectionIdQuoted = PgUtil.quoteIdent("${collectionId}\$del")
        usePgConnection().execute("""DELETE FROM $collectionIdQuoted WHERE id = $1""", arrayOf(id))
    }

    /**
     * Updates xyz namespace and copies feature to $del table.
     *
     * Uses the current session, does not commit or rollback the current session.
     */
    internal fun copyToDel(collectionId: String, OLD: Row) {
        val collectionConfig = getCollectionConfig(collectionId)
        val autoPurge: Boolean? = collectionConfig.autoPurge
        if (autoPurge != true) {
            val collectionIdQuoted = PgUtil.quoteIdent("${collectionId}\$del")
            val oldMeta = OLD.meta!!
            val conn = usePgConnection()
            conn.execute("INSERT INTO $collectionIdQuoted ($COL_ALL) VALUES ($COL_ALL_DOLLAR)",
                arrayOf(
                    oldMeta.txnNext,
                    oldMeta.txn,
                    oldMeta.uid,
                    oldMeta.ptxn,
                    oldMeta.puid,
                    oldMeta.flags,
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
                    OLD.type,
                    oldMeta.fnva1
                )
            ).close()
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
        TODO("Forbidden, please use API")
//        val collectionId = getBaseCollectionId(data.TG_TABLE_NAME)
//        if (data.TG_OP == TG_OP_DELETE && data.OLD != null) {
//            deleteFromDel(collectionId, data.OLD.id)
//            // save current head in hst
//            data.OLD.meta?.txnNext = data.OLD.meta?.txn
//            saveInHst(collectionId, data.OLD)
//            rowUpdater.xyzDel(data.OLD.meta!!)
//            copyToDel(collectionId, data.OLD)
//            // save del state in hst
//            saveInHst(collectionId, data.OLD)
//        }
//        if (data.TG_OP == TG_OP_UPDATE) {
//            check(data.NEW != null) { "Missing NEW for UPDATE" }
//            check(data.OLD != null) { "Missing OLD for UPDATE" }
//            deleteFromDel(collectionId, data.NEW.id)
//            data.OLD.meta?.txnNext = data.NEW.meta?.txn
//            saveInHst(collectionId, data.OLD)
//        }
//        if (data.TG_OP == TG_OP_INSERT) {
//            check(data.NEW != null) { "Missing NEW for INSERT" }
//            deleteFromDel(collectionId, data.NEW.id)
//        }
    }

    /**
     * The cached Postgres version.
     */
    private lateinit var pgVersion: XyzVersion

    internal fun nextUid() = uid++

    /**
     * Returns the current transaction number, if no transaction number is yet generated, generates a new one.
     *
     * Uses the current session, does not commit or rollback the current session.
     * @return The current transaction number.
     */
    override fun txn(): Txn {
        if (_txn == null) {
            val conn = usePgConnection()
            conn.execute(
                """
WITH ns as (SELECT oid FROM pg_namespace WHERE nspname = $1),
     txn_seq as (SELECT cls.oid as oid FROM pg_class cls, ns WHERE cls.relname = 'naksha_txn_seq' and cls.relnamespace = ns.oid)
SELECT nextval(txn_seq.oid) as txn, txn_seq.oid as txn_oid, (extract(epoch from transaction_timestamp())*1000)::int8 as time, ns.oid as ns_oid
FROM ns, txn_seq;
""", arrayOf(options.schema)
            ).fetch().use {
                val schemaOid: Int = it["ns_oid"]
                val txnSeqOid: Int = it["txn_oid"]
                val txts: Int64 = it["time"]
                var txn = Txn(it["txn"])
                verifyCache(schemaOid)
                _txts = txts
                _txn = txn
                val txInstant = Instant.fromEpochMilliseconds(txts.toLong())
                val txDate = txInstant.toLocalDateTime(TimeZone.UTC)
                if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                    conn.execute("SELECT pg_advisory_lock($1)", arrayOf(PgStatic.TXN_LOCK_ID)).close()
                    try {
                        conn.execute("SELECT nextval($1) as v", arrayOf(txnSeqOid)).fetch().use {
                            txn = Txn(it["v"])
                        }
                        _txn = txn
                        if (txn.year() != txDate.year || txn.month() != txDate.monthNumber || txn.day() != txDate.dayOfMonth) {
                            // Rollover, we update sequence of the day.
                            txn = Txn.of(txDate.year, txDate.monthNumber, txDate.dayOfMonth, Int64(0))
                            _txn = txn
                            conn.execute("SELECT setval($1, $2)", arrayOf(txnSeqOid, txn.value + 1)).close()
                        }
                    } finally {
                        conn.execute("SELECT pg_advisory_unlock($1)", arrayOf(PgStatic.TXN_LOCK_ID)).close()
                    }
                }
            }
        }
        return _txn!!
    }

    override fun uid(): Int {
        TODO("Not yet implemented")
    }

    override fun newGuid(): Luid {
        TODO("Not yet implemented")
    }

    /**
     * The start time of the transaction.
     * @return The start time of the transaction.
     */
    fun txnTs(): Int64 {
        txn()
        return _txts!!
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
            val conn = usePgConnection()
            conn.execute(
                "select $COL_FEATURE, $COL_FLAGS from $NKC_TABLE_ESC where $COL_ID = $1",
                arrayOf(collectionId)
            ).fetch().use {
                val row = Row(
                    storage = storage,
                    flags = it[COL_FLAGS],
                    feature = it[COL_FEATURE],
                    id = collectionId,
                    guid = null
                )
                val nakCollection = row.toMemoryModel()!!.proxy(NakshaCollectionProxy::class)
                collectionConfiguration[collectionId] = nakCollection
                nakCollection
            }
        }
    }

    internal fun isHistoryEnabled(collectionId: String): Boolean {
        val isDisabled: Boolean? = getCollectionConfig(collectionId).disableHistory
        return isDisabled != true
    }

    private inner class TransactionAction internal constructor(
        val transaction: NakshaTransactionProxy,
        writeRequest: WriteRequest
    ) {
        private val transactionWriter: SingleCollectionWriter? = if (writeRequest.ops.any { it.collectionId == NKC_TABLE })
            null
        else
            SingleCollectionWriter(TRANSACTIONS_COL, this@PgSession, modifyCounters = false)

        fun write() {
            transactionWriter?.writeFeatures(
                WriteRequest(
                    arrayOf(WriteFeature(TRANSACTIONS_COL, transaction)),
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
        val transactionAction = TransactionAction(transaction(), writeRequest)
        return try {
            transactionAction.write()
            val writeFeaturesResult = executor.write(writeRequest)
            transactionAction.write()
            writeFeaturesResult
        } catch (e: NakshaException) {
            if (PgStatic.PRINT_STACK_TRACES) logger.info(e.stackTraceToString())
            ErrorResponse(NakshaError(e.errNo, e.errMsg))
        } catch (e: Throwable) {
            if (PgStatic.PRINT_STACK_TRACES)
                logger.info(e.cause?.message!!)
            ErrorResponse(NakshaError(ERR_FATAL, e.cause?.message ?: "Fatal ${e.stackTraceToString()}"))
        }
    }

    internal fun transaction(): NakshaTransactionProxy {
        if (transaction == null) {
            transaction = NakshaTransactionProxy()
            transaction!!.id = txn().toGuid(storage.id(), "txn", "txn").toString()
        }
        return transaction!!
    }

    override fun txBeforeStart() {
    }

    override fun txAfterStart(conn: PgConnection) {
    }

    override fun txOnCommit(session: PgConnection) {
    }

    override fun txOnRollback(session: PgConnection) {
    }

    override fun writeFeature(feature: NakshaFeatureProxy): Response {
        TODO("Not yet implemented")
    }

    override fun execute(request: Request): Response {
        when (request) {
            is ReadRequest -> {
                val conn = usePgConnection()
                val (sql, params) = ReadQueryBuilder(conn).build(request)
                val pgResult = conn.execute(sql, params.toTypedArray())
                TODO("Fix me!")
                //val rows = DbRowMapper.toReadRows(pgResult, storage)
                //return SuccessResponse(rows = rows)
            }

            is WriteRequest -> {
                TODO("WriteRequest not yet implemented")
            }

            else -> throw IllegalArgumentException("Unknown request")
        }
    }

    override fun executeParallel(request: Request): Response {
        TODO("Not yet implemented")
    }

    override fun getFeatureById(id: String): ResultRow? {
        TODO("Not yet implemented")
    }

    override fun getFeaturesByIds(ids: List<String>): Map<String, ResultRow> {
        TODO("Not yet implemented")
    }
}
