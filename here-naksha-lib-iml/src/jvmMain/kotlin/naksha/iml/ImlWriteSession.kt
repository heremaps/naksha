@file:Suppress("OPT_IN_USAGE", "DEPRECATION")
@file:OptIn(naksha.model.v30_experimental::class)

package naksha.iml

import java.sql.Connection
import java.sql.Savepoint
import java.util.WeakHashMap
import naksha.base.Action
import naksha.base.Id
import naksha.base.NakshaError
import naksha.base.NakshaException
import naksha.base.Platform
import naksha.base.Version
import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.ILock
import naksha.model.IWriteSession
import naksha.model.MemberProcessorMap
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaTx
import naksha.model.request.FeatureTuple
import naksha.model.request.Request
import naksha.model.request.Response
import naksha.model.request.SuccessResponse
import naksha.model.request.WriteOp
import naksha.model.request.WriteRequest

/** A sticky PostgreSQL write transaction with private discovery and tuple state until commit. @since 3.0 */
class ImlWriteSession internal constructor(storage: ImlStorage, options: SessionOptions) :
  ImlReadSession(storage, options), IWriteSession {
  override val processors = MemberProcessorMap()
  private val superUser = NakshaContext.currentContext().su
  private var connection: Connection? = null
  private var lockConnection: Connection? = null
  private var tx: NakshaTx? = null
  private var allocatedVersion = 0L
  private var allocatedTime = 0L
  private var sessionLockLost = false
  private var rollbackOnly = false
  private var requestExecuting = false
  private var localMeta: ImlMetadata? = null
  private val locks = ArrayList<Lock>()
  private class Effect(val catalog: String, val collection: String, val id: String, val tuple: naksha.model.Tuple)
  private val modified = LinkedHashMap<String, Effect>()
  internal val allocations = ImlAllocation()
  internal var reconciledTables = 0
    private set
  private class Preparation { @Volatile var ready = false }
  private val preparation = synchronized(preparations) { preparations.getOrPut(storage) { Preparation() } }
  override val transactional: Boolean get() = tx != null
  override val useSharedCache: Boolean get() = tx == null
  override val sessionMetadata: ImlMetadata get() = localMeta ?: storage.metadata

  override var stmtTimeout: Int
    get() = super.stmtTimeout
    @Synchronized set(value) { super.stmtTimeout = value; applyLiveTimeouts() }

  override var lockTimeout: Int
    get() = super.lockTimeout
    @Synchronized set(value) { super.lockTimeout = value; applyLiveTimeouts() }

  override var socketTimeout: Int
    get() = super.socketTimeout
    @Synchronized set(value) { super.socketTimeout = value; applyLiveTimeouts() }

  private fun applyLiveTimeouts() {
    checkSessionLocks()
    for (conn in arrayOf(connection, lockConnection)) {
      if (conn == null) continue
      val local = !conn.autoCommit
      val savepoint = if (local) conn.setSavepoint() else null
      try {
        conn.setNetworkTimeout({ it.run() }, options.socketTimeout)
        conn.prepareStatement("SELECT set_config('statement_timeout', ?, ?), set_config('lock_timeout', ?, ?)").use {
          it.setString(1, "${options.stmtTimeout}ms")
          it.setBoolean(2, local)
          it.setString(3, "${options.lockTimeout}ms")
          it.setBoolean(4, local)
          it.executeQuery().close()
        }
        if (savepoint != null) conn.releaseSavepoint(savepoint)
      } catch (error: Throwable) {
        if (savepoint != null) recoverSavepoint(conn, savepoint, error) else sessionConnectionValid(conn)
        throw error
      }
    }
  }

  private fun checkTransaction() {
    checkSessionLocks()
    if (rollbackOnly) throw NakshaException(NakshaError.ILLEGAL_STATE, "IML transaction requires rollback")
    val transaction = tx ?: return
    val valid = try { transaction.version.number == allocatedVersion && transaction.getRaw("time") == allocatedTime } catch (_: Exception) { false }
    if (!valid) {
      rollbackOnly = true
      throw illegalArg("IML transaction version and time must not be modified")
    }
  }

  private fun sessionConnectionValid(conn: Connection): Boolean {
    val valid = try { !conn.isClosed && conn.isValid(1) } catch (_: Exception) { false }
    if (!valid) {
      for (lock in locks) if (lock.conn === conn && !lock.closed) { lock.closed = true; sessionLockLost = true }
      locks.removeAll { it.conn === conn }
      if (lockConnection === conn) lockConnection = null
      if (sessionLockLost && tx != null) rollbackOnly = true
      try { conn.close() } catch (_: Exception) { }
    }
    return valid
  }

  private fun checkSessionLocks() {
    val conn = lockConnection
    if (conn != null) sessionConnectionValid(conn)
    if (sessionLockLost) throw NakshaException(NakshaError.ILLEGAL_STATE, "IML session lock connection was lost; close this session")
  }

  private fun recoverSavepoint(conn: Connection, savepoint: Savepoint, error: Throwable) {
    try { conn.rollback(savepoint); conn.releaseSavepoint(savepoint) } catch (cleanup: Throwable) {
      if (conn === connection) rollbackOnly = true
      else {
        lockConnection = null
        sessionLockLost = true
        for (lock in locks) if (lock.conn === conn) lock.closed = true
        locks.removeAll { it.conn === conn }
        try { conn.close() } catch (closeError: Throwable) { cleanup.addSuppressed(closeError) }
      }
      error.addSuppressed(cleanup)
    }
  }

  @Synchronized
  internal override fun <T> withConnection(block: (Connection) -> T): T {
    checkOpen()
    checkTransaction()
    val active = connection
    if (active == null) return super.withConnection(block)
    val savepoint = active.setSavepoint()
    try {
      val result = block(active)
      active.releaseSavepoint(savepoint)
      return result
    } catch (error: Throwable) {
      recoverSavepoint(active, savepoint, error)
      throw error
    }
  }

  @Synchronized
  override fun useTransaction(): NakshaTx {
    storage.checkRunning()
    checkOpen()
    checkTransaction()
    val current = tx
    if (current != null) return current
    prepareWriter()
    val conn = storage.openConnection(options, false)
    try {
      val control = ImlMetadata.quote(storage.config.metadataSchema)
      // Serialize allocation with all writer mutations, including absent IDs and administrative DDL.
      advisory(conn, "pg_advisory_xact_lock", "iml-writer:${storage.config.metadataSchema}")
      val metadata = ImlMetadata(conn, storage.config, storage.number)
      val sequence = conn.prepareStatement("SELECT nextval(?::regclass)").use {
        it.setString(1, "$control.iml_version_seq")
        it.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
      }
      val transaction = NakshaTx().apply {
        setRaw("id", Version.manual(sequence, Action.VERSION).toString())
        setRaw("time", Platform.currentMillis())
      }
      connection = conn
      localMeta = metadata
      tx = transaction
      allocatedVersion = transaction.version.number
      allocatedTime = transaction.time
      return transaction
    } catch (error: Throwable) {
      try { conn.rollback() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
      try { conn.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
      throw error
    }
  }

  private fun prepareWriter() {
    if (preparation.ready) return
    // Use a dedicated preparation monitor, never the storage monitor used by shutdown.
    synchronized(preparation) {
      if (preparation.ready) return
      // Creation and reconciliation must survive the first user rollback. Subsequent writes must cooperate with the adapter's allocator; externally advanced target versions remain conflicts.
      storage.openConnection(options, false).use { bootstrap ->
        try {
          val control = ImlMetadata.quote(storage.config.metadataSchema)
          advisory(bootstrap, "pg_advisory_xact_lock", "iml-writer:${storage.config.metadataSchema}")
          bootstrap.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS $control.iml_catalogs (id text PRIMARY KEY, feature jsonb NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS $control.iml_transactions (source_version bigint PRIMARY KEY, feature jsonb NOT NULL)")
            it.execute("CREATE SEQUENCE IF NOT EXISTS $control.iml_version_seq MINVALUE 1 MAXVALUE 8796093022207")
          }
          val metadata = ImlMetadata(bootstrap, storage.config, storage.number)
          var maximum = 0L
          val tables = HashSet<String>()
          for (layer in metadata.allLayers.values) {
            val table = layer.sqlTable
            if (!tables.add(table)) continue
            bootstrap.createStatement().use {
              it.executeQuery("SELECT COALESCE(max(version), 0) FROM $table").use { rows -> rows.next(); maximum = maxOf(maximum, rows.getLong(1)) }
            }
            reconciledTables++
          }
          bootstrap.createStatement().use {
            it.executeQuery("SELECT COALESCE(max(source_version), 0) FROM $control.iml_transactions").use { rows -> rows.next(); maximum = maxOf(maximum, rows.getLong(1)) }
          }
          val sequence = bootstrap.createStatement().use {
            it.executeQuery("SELECT last_value FROM $control.iml_version_seq").use { rows -> rows.next(); rows.getLong(1) }
          }
          if (maximum >= sequence) {
            if (maximum >= 8796093022207L) throw illegalArg("IML source version space exhausted")
            bootstrap.prepareStatement("SELECT setval(?::regclass, ?, true)").use {
              it.setString(1, "$control.iml_version_seq")
              it.setLong(2, maximum)
              it.executeQuery().close()
            }
          }
          bootstrap.commit()
          preparation.ready = true
        } catch (error: Throwable) {
          try { bootstrap.rollback() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
          throw error
        }
      }
    }
  }

  @Synchronized
  override fun getTransaction(): NakshaTx? { checkOpen(); return tx }

  @Synchronized
  override fun executeWrite(request: WriteRequest): Response {
    check(!requestExecuting) { "IML write request is already executing" }
    requestExecuting = true
    try {
      storage.checkRunning()
      checkOpen()
      checkTransaction()
      if (options.excludeFn != null || options.excludePaths != null) throw unsupportedOp("IML does not support write hash exclusions")
      for (write in request.writes) {
        val op = write ?: throw illegalArg("Null IML write")
        op.validate()
        if (op.op !in listOf(WriteOp.CREATE, WriteOp.UPDATE, WriteOp.UPSERT, WriteOp.DELETE, WriteOp.PURGE)) throw unsupportedOp("Unsupported IML write operation")
        if (op.catalogId == null || op.collectionId == null) throw illegalArg("IML writes require explicit catalog and collection IDs")
        if (op.isDictionaryModification() || (op.catalogId == Id.ADMIN_CATALOG_TEXT && !op.isMapModification())) throw unsupportedOp("IML does not support this administrative write")
        if ((op.isMapModification() || op.isCollectionModification()) && !superUser) throw NakshaException(NakshaError.FORBIDDEN, "IML administrative writes require super-user authorization")
      }
      if (request.writes.isEmpty()) return SuccessResponse()
      val transaction = useTransaction()
      val conn = checkNotNull(connection)
      val savepoint = conn.setSavepoint()
      val previousMetadata = localMeta
      val previousModified = LinkedHashMap(modified)
      val previousAllocations = allocations.checkpoint()
      val result = ArrayList<FeatureTuple>()
      try {
        for (write in request.writes) {
          checkTransaction()
          val op = checkNotNull(write)
          val mutation = ImlMutation(this, conn, sessionMetadata, allocatedTime, allocatedVersion ushr 2)
          val tuple = mutation.execute(op)
          checkTransaction()
          if (mutation.metadataChanged) {
            localMeta = ImlMetadata(conn, storage.config, storage.number)
            allocations.clear()
          }
          if (tuple != null) {
            modified["${op.catalogId}\u0000${op.collectionId}\u0000${op.id}"] = Effect(checkNotNull(op.catalogId), checkNotNull(op.collectionId), op.id, tuple)
            if (request.returnResults) {
              var entry: FeatureTuple? = ImlFeatureTuple(tuple.tupleNumber).apply { ImlTuple.attach(this, tuple); source = storage }
              for (index in request.resultFilters.indices.reversed()) entry = request.resultFilters[index]?.filter(entry ?: break)
              if (entry != null) result.add(entry)
            }
          }
        }
        updateStatistics(transaction)
        conn.releaseSavepoint(savepoint)
        return SuccessResponse().withFeatureTupleList(result)
      } catch (error: Throwable) {
        recoverSavepoint(conn, savepoint, error)
        localMeta = previousMetadata
        modified.clear()
        modified.putAll(previousModified)
        allocations.restore(previousAllocations)
        updateStatistics(transaction)
        throw error
      }
    } finally {
      requestExecuting = false
    }
  }

  override fun execute(request: Request): Response = super<IWriteSession>.execute(request)

  private fun updateStatistics(transaction: NakshaTx) {
    transaction.maps.clear()
    transaction.featuresModified = modified.size
    transaction.featuresBytes = 0
    for (effect in modified.values) {
      val tuple = effect.tuple
      transaction.featuresBytes = Math.addExact(transaction.featuresBytes, tuple.featureBytes.size)
      if (effect.catalog == Id.ADMIN_CATALOG_TEXT && effect.collection == Id.CATALOGS_COL_TEXT) {
        transaction.useCatalog(effect.id, Id.textToInt(effect.id), tuple.tupleNumber.action)
      } else {
        val catalog = transaction.useCatalog(effect.catalog, Id.textToInt(effect.catalog))
        if (effect.collection == Id.COLLECTIONS_COL_TEXT) catalog.useCollection(effect.id, Id.textToInt(effect.id), tuple.tupleNumber.action)
        else catalog.useCollection(effect.collection, Id.textToInt(effect.collection)).add(tuple.tupleNumber)
      }
    }
  }

  @Synchronized
  override fun commit() {
    check(!requestExecuting) { "Cannot commit during an IML write request" }
    storage.checkRunning()
    checkOpen()
    val conn = connection ?: return
    val transaction = checkNotNull(tx)
    val generation = allocatedVersion ushr 2
    var failure: Throwable? = null
    try {
      val metadata: ImlMetadata
      try {
        checkTransaction()
        updateStatistics(transaction)
        conn.prepareStatement("INSERT INTO ${ImlMetadata.quote(storage.config.metadataSchema)}.iml_transactions VALUES (?, ?::jsonb)").use {
          it.setLong(1, generation)
          it.setString(2, Platform.toJSON(transaction))
          it.executeUpdate()
        }
        metadata = sessionMetadata
        conn.commit()
      } catch (error: Throwable) {
        try { conn.rollback() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
        throw error
      }
      // Publication errors must not trigger rollback after a successful database commit.
      storage.publishMetadata(metadata, generation)
    } catch (error: Throwable) {
      failure = error
      throw error
    } finally {
      try { finishTransaction(conn) } catch (cleanup: Throwable) {
        val original = failure
        if (original != null) original.addSuppressed(cleanup) else throw cleanup
      }
    }
  }

  @Synchronized
  override fun rollback() {
    check(!requestExecuting) { "Cannot roll back during an IML write request" }
    checkOpen()
    val conn = connection ?: return
    var failure: Throwable? = null
    try { conn.rollback() } catch (error: Throwable) {
      failure = error
      throw error
    } finally {
      try { finishTransaction(conn) } catch (cleanup: Throwable) {
        val original = failure
        if (original != null) original.addSuppressed(cleanup) else throw cleanup
      }
    }
  }

  private fun finishTransaction(conn: Connection) {
    connection = null
    tx = null
    allocatedVersion = 0L
    allocatedTime = 0L
    rollbackOnly = false
    localMeta = null
    modified.clear()
    allocations.clear()
    var failure: Throwable? = null
    // Transaction leases use session-level locks, including one acquisition per duplicate lease.
    for (lock in locks) if (lock.transactionLock && !lock.closed) {
      try { advisory(conn, "pg_advisory_unlock", lock.key) } catch (cleanup: Throwable) {
        val original = failure
        if (original != null) original.addSuppressed(cleanup) else failure = cleanup
      } finally { lock.closed = true }
    }
    locks.removeAll { it.transactionLock }
    try { conn.close() } catch (cleanup: Throwable) {
      val original = failure
      if (original != null) original.addSuppressed(cleanup) else failure = cleanup
    }
    failure?.let { throw it }
  }

  @Synchronized
  override fun acquireSessionLock(lockId: String): ILock {
    checkOpen()
    checkSessionLocks()
    if (tx != null) throw illegalArg("IML session locks must be acquired before starting a transaction")
    var conn = lockConnection
    if (conn == null) {
      conn = storage.openConnection(options, false)
      try {
        conn.prepareStatement("SELECT set_config('statement_timeout', ?, false), set_config('lock_timeout', ?, false), set_config('idle_in_transaction_session_timeout', '0', false)").use {
          it.setString(1, "${options.stmtTimeout}ms")
          it.setString(2, "${options.lockTimeout}ms")
          it.executeQuery().close()
        }
        conn.commit()
        conn.autoCommit = true
      } catch (error: Throwable) {
        try { conn.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
        throw error
      }
      lockConnection = conn
    }
    return acquireLock(conn, lockId, false)
  }

  @Synchronized
  override fun acquireTransactionLock(lockId: String): ILock {
    checkOpen()
    checkSessionLocks()
    val key = "iml-lock:${storage.config.metadataSchema}:$lockId"
    for (lock in locks) if (!lock.closed && !lock.transactionLock && lock.key == key) throw illegalArg("IML transaction lock conflicts with a held session lock")
    useTransaction()
    return acquireLock(checkNotNull(connection), lockId, true)
  }

  private fun acquireLock(conn: Connection, id: String, transaction: Boolean): ILock {
    val key = "iml-lock:${storage.config.metadataSchema}:$id"
    val savepoint = if (transaction) conn.setSavepoint() else null
    try {
      if (transaction) {
        conn.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 0))").use {
          it.setString(1, key)
          it.executeQuery().use { rows ->
            check(rows.next())
            if (!rows.getBoolean(1)) throw NakshaException(NakshaError.CONFLICT, "IML transaction lock '$id' is already held")
          }
        }
      } else advisory(conn, "pg_advisory_lock", key)
      if (savepoint != null) conn.releaseSavepoint(savepoint)
    } catch (error: Throwable) {
      if (savepoint != null) recoverSavepoint(conn, savepoint, error) else sessionConnectionValid(conn)
      throw error
    }
    return Lock(conn, key, transaction).also { locks.add(it) }
  }

  private fun advisory(conn: Connection, function: String, key: String) {
    conn.prepareStatement("SELECT $function(hashtextextended(?, 0))").use {
      it.setString(1, key)
      it.executeQuery().close()
    }
  }

  private inner class Lock(val conn: Connection, val key: String, val transactionLock: Boolean) : ILock {
    override val storage: ImlStorage get() = this@ImlWriteSession.storage
    @Volatile var closed = false
    override fun isClosed(): Boolean = synchronized(this@ImlWriteSession) {
      if (!closed && !transactionLock) sessionConnectionValid(conn)
      closed
    }
    override fun close() = synchronized(this@ImlWriteSession) {
      if (!closed && !transactionLock) sessionConnectionValid(conn)
      if (!closed) {
        advisory(conn, "pg_advisory_unlock", key)
        closed = true
        locks.remove(this)
      }
    }
  }

  companion object {
    private val preparations = WeakHashMap<ImlStorage, Preparation>()
  }

  @Synchronized
  override fun close() {
    check(!requestExecuting) { "Cannot close during an IML write request" }
    if (isClosed()) return
    var failure: Throwable? = null
    try { rollback() } catch (error: Throwable) { failure = error }
    try {
      // Await PostgreSQL's unlock acknowledgement rather than racing backend socket-close cleanup.
      for (lock in locks.toList()) {
        try { lock.close() } catch (cleanup: Throwable) {
          val original = failure
          if (original != null) original.addSuppressed(cleanup) else failure = cleanup
        }
      }
    } finally {
      try {
        lockConnection?.close()
      } catch (cleanup: Throwable) {
        val original = failure
        if (original != null) original.addSuppressed(cleanup) else failure = cleanup
      } finally {
        lockConnection = null
        for (lock in locks) lock.closed = true
        locks.clear()
        super.close()
      }
    }
    failure?.let { throw it }
  }
}
