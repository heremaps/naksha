package naksha.iml

import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.AbstractStorage
import naksha.model.IStreamSession
import naksha.model.IWriteSession
import naksha.model.SessionOptions
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.base.NakshaException
import naksha.base.NakshaError
import naksha.model.Tuple
import naksha.base.TupleNumber
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * PostgreSQL storage for Interactive Map Layers. Read-only by default; mutation requires explicit writeEnabled configuration. Never installs scripts, functions or extensions.
 * @since 3.0
 */
class ImlStorage : AbstractStorage<ImlConfig>() {
  override val configKlass = ImlConfig::class
  private var jdbcUrl = ""
  private var running = false
  private val sessions = ConcurrentHashMap.newKeySet<ImlReadSession>()
  private val connectionPermit = Semaphore(1, true)
  private val publicationLock = Any()
  @Volatile internal var generation = 0L
    private set
  internal val handles = ImlHandles()
  @Volatile internal lateinit var metadata: ImlMetadata
    private set

  @Synchronized
  override fun initStorage(config: ImlConfig, create: Boolean?, upgrade: Boolean?) {
    val url = config.jdbcUrl
    if (!url.startsWith("jdbc:postgresql:") || url.isBlank()) {
      throw illegalArg("ImlConfig.jdbcUrl must be a PostgreSQL JDBC URL")
    }
    val createObjects = create ?: config.create
    if ((createObjects || (upgrade ?: config.upgrade)) && !config.writeEnabled) {
      throw unsupportedOp("IML storage cannot create or upgrade database objects")
    }
    jdbcUrl = url
    val options = SessionOptions(appId = "naksha-iml-discovery", appName = "naksha-iml")
    if (createObjects) {
      if (!NakshaContext.currentContext().su) throw NakshaException(NakshaError.FORBIDDEN, "IML setup requires super-user authorization")
      val connection = DriverManager.getConnection(jdbcUrl)
      connection.use {
        it.autoCommit = false
        try {
          val metadataSchema = ImlMetadata.quote(config.metadataSchema)
          it.createStatement().use { statement ->
            statement.execute("CREATE SCHEMA IF NOT EXISTS ${ImlMetadata.quote(config.schema)}")
            statement.execute("CREATE SCHEMA IF NOT EXISTS $metadataSchema")
            statement.execute("CREATE TABLE IF NOT EXISTS $metadataSchema.space_meta (id text NOT NULL, schem text NOT NULL, h_id text NOT NULL, meta jsonb NOT NULL DEFAULT '{}'::jsonb, PRIMARY KEY(id,schem))")
          }
          it.commit()
        } catch (error: Throwable) {
          try { it.rollback() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
          throw error
        }
      }
    }
    metadata = withConnection(options) { ImlMetadata(it, config, number) }
    // The global cache is keyed by storage ID, not JDBC URL or discovery snapshot.
    Naksha.cache.clear(this)
    running = true
  }

  override fun afterInit() { }

  @Synchronized
  override fun newReadSession(options: SessionOptions?): ImlReadSession {
    useInitialized()
    check(running) { "IML storage is shut down" }
    val session = ImlReadSession(this, options ?: SessionOptions.from(null))
    // Verify connectivity and read-only transaction setup without keeping an idle connection.
    session.withConnection { connection ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SELECT 1").use { result ->
          check(result.next() && result.getInt(1) == 1) { "IML connection validation failed" }
        }
      }
    }
    sessions.add(session)
    return session
  }

  @Synchronized
  override fun newWriteSession(options: SessionOptions?): ImlWriteSession {
    if (!initialized || !config.writeEnabled) throw unsupportedOp("IML storage is read-only; writeEnabled must be explicitly enabled")
    useInitialized()
    check(running) { "IML storage is shut down" }
    return ImlWriteSession(this, options ?: SessionOptions.from(null)).also { sessions.add(it) }
  }

  internal fun publishMetadata(value: ImlMetadata, revision: Long) = synchronized(publicationLock) {
    if (revision <= generation) return@synchronized
    metadata = value
    generation = revision
    handles.clear()
    Naksha.cache.clear(this)
  }

  internal fun cacheTuple(tuple: Tuple, expectedGeneration: Long) = synchronized(publicationLock) {
    if (generation == expectedGeneration) Naksha.cache.store(tuple)
  }

  internal fun cachedTuple(number: TupleNumber): Tuple? = synchronized(publicationLock) { Naksha.cache[number] }

  internal fun discoverySnapshot(): Pair<ImlMetadata, Long> = synchronized(publicationLock) { metadata to generation }

  internal fun checkRunning() {
    check(running) { "IML storage is shut down" }
  }

  internal fun refreshMetadata(options: SessionOptions) {
    val refreshed = withConnection(options) { connection ->
      val table = "${ImlMetadata.quote(config.metadataSchema)}.iml_transactions"
      val exists = connection.prepareStatement("SELECT to_regclass(?)").use {
        it.setString(1, table)
        it.executeQuery().use { rows -> rows.next() && rows.getString(1) != null }
      }
      if (!exists) return@withConnection null
      val revision = connection.createStatement().use {
        it.executeQuery("SELECT COALESCE(max(source_version),0) FROM $table").use { rows -> rows.next(); rows.getLong(1) }
      }
      if (revision <= generation) null else ImlMetadata(connection, config, number) to revision
    }
    if (refreshed != null) publishMetadata(refreshed.first, refreshed.second)
  }

  override fun newStreamSession(options: SessionOptions?): IStreamSession =
    throw unsupportedOp("IML storage does not support streaming")

  internal fun releaseSession(session: ImlReadSession) {
    sessions.remove(session)
  }

  internal fun <T> withConnection(options: SessionOptions, block: (Connection) -> T): T {
    connectionPermit.acquire()
    try {
      openConnection(options, true).use { connection ->
        try {
          return block(connection)
        } finally {
          connection.rollback()
        }
      }
    } finally {
      connectionPermit.release()
    }
  }

  internal fun openConnection(options: SessionOptions, readOnly: Boolean): Connection {
    if (!readOnly && !config.writeEnabled) throw unsupportedOp("IML storage is read-only")
    require(options.idleTxTimeout >= 0) { "IML idle transaction timeout must not be negative" }
    val properties = Properties()
    properties.setProperty("ApplicationName", options.appName)
    properties.setProperty("connectTimeout", ((options.connectTimeout.toLong() + 999) / 1000).toString())
    properties.setProperty("socketTimeout", ((options.socketTimeout.toLong() + 999) / 1000).toString())
    val connection = DriverManager.getConnection(jdbcUrl, properties)
    try {
      connection.isReadOnly = readOnly
      // Discovery revision and descriptor queries must agree even if a peer writer commits between them.
      if (readOnly) connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
      connection.autoCommit = false
      connection.createStatement().use { it.execute(if (readOnly) "SET TRANSACTION READ ONLY" else "SET TRANSACTION READ WRITE") }
      connection.prepareStatement("SELECT set_config('statement_timeout', ?, true), set_config('lock_timeout', ?, true), set_config('idle_in_transaction_session_timeout', ?, true)").use {
        it.setString(1, "${options.stmtTimeout}ms")
        it.setString(2, "${options.lockTimeout}ms")
        it.setString(3, "${options.idleTxTimeout}ms")
        it.executeQuery().close()
      }
      return connection
    } catch (error: Throwable) {
      try { connection.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
      throw error
    }
  }

  @Synchronized
  override fun shutdownStorage(dropCache: Boolean) {
    running = false
    var failure: Throwable? = null
    for (session in sessions) {
      try { session.close() } catch (error: Throwable) {
        if (failure == null) failure = error else failure.addSuppressed(error)
      }
    }
    try { handles.clear(); Naksha.cache.clear(this) } catch (error: Throwable) {
      if (failure == null) failure = error else failure.addSuppressed(error)
    }
    if (failure != null) throw failure
  }
}
