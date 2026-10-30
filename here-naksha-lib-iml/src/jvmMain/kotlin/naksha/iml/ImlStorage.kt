package naksha.iml

import naksha.base.illegalArg
import naksha.base.unsupportedOp
import naksha.model.AbstractStorage
import naksha.model.IStreamSession
import naksha.model.IWriteSession
import naksha.model.SessionOptions
import naksha.model.Naksha
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Read-only PostgreSQL storage for Interactive Map Layers. Never installs database objects, scripts, or extensions.
 * @since 3.0
 */
class ImlStorage : AbstractStorage<ImlConfig>() {
  override val configKlass = ImlConfig::class
  private var jdbcUrl = ""
  private var running = false
  private val sessions = ConcurrentHashMap.newKeySet<ImlReadSession>()
  private val connectionPermit = Semaphore(1, true)
  internal lateinit var metadata: ImlMetadata
    private set

  @Synchronized
  override fun initStorage(config: ImlConfig, create: Boolean?, upgrade: Boolean?) {
    val url = config.jdbcUrl
    if (!url.startsWith("jdbc:postgresql:") || url.isBlank()) {
      throw illegalArg("ImlConfig.jdbcUrl must be a PostgreSQL JDBC URL")
    }
    if ((create ?: config.create) || (upgrade ?: config.upgrade)) {
      throw unsupportedOp("IML storage cannot create or upgrade database objects")
    }
    jdbcUrl = url
    val options = SessionOptions(appId = "naksha-iml-discovery", appName = "naksha-iml")
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

  override fun newWriteSession(options: SessionOptions?): IWriteSession =
    throw unsupportedOp("IML storage is read-only")

  override fun newStreamSession(options: SessionOptions?): IStreamSession =
    throw unsupportedOp("IML storage does not support streaming")

  internal fun releaseSession(session: ImlReadSession) {
    sessions.remove(session)
  }

  internal fun <T> withConnection(options: SessionOptions, block: (Connection) -> T): T {
    connectionPermit.acquire()
    try {
      val properties = Properties()
      properties.setProperty("ApplicationName", options.appName)
      properties.setProperty("connectTimeout", ((options.connectTimeout.toLong() + 999) / 1000).toString())
      properties.setProperty("socketTimeout", ((options.socketTimeout.toLong() + 999) / 1000).toString())
      DriverManager.getConnection(jdbcUrl, properties).use { connection ->
        connection.isReadOnly = true
        connection.autoCommit = false
        try {
          connection.createStatement().use { it.execute("SET TRANSACTION READ ONLY") }
          connection.prepareStatement("SELECT set_config('statement_timeout', ?, true), set_config('lock_timeout', ?, true)").use { statement ->
            statement.setString(1, "${options.stmtTimeout}ms")
            statement.setString(2, "${options.lockTimeout}ms")
            statement.executeQuery().use { it.next() }
          }
          return block(connection)
        } finally {
          connection.rollback()
        }
      }
    } finally {
      connectionPermit.release()
    }
  }

  @Synchronized
  override fun shutdownStorage(dropCache: Boolean) {
    running = false
    for (session in sessions) session.close()
    Naksha.cache.clear(this)
  }
}
