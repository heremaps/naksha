package naksha.psql

import naksha.base.AtomicMap
import naksha.base.Id
import naksha.base.Platform.PlatformCompanion.FAL
import naksha.base.Platform.PlatformCompanion.logger
import naksha.base.fn.Fx2
import naksha.base.illegalArg
import naksha.base.illegalState
import naksha.base.unsupportedOp
import naksha.model.AbstractStorage
import naksha.model.IStreamSession
import naksha.model.IWriteSession
import naksha.model.Naksha
import naksha.model.NakshaContext
import naksha.model.SessionOptions
import naksha.model.objects.NakshaStorage
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.use

actual open class PgStorage() : AbstractStorage<PgConfig>() {

    actual constructor(config: PgConfig, create: Boolean?, upgrade: Boolean?) : this() {
        initStorage(config, create, upgrade)
    }

    private var _database: PgDatabase? = null
    private var _cluster: PgCluster? = null
    private var _channel: String? = null

    actual override val configKlass: KClass<PgConfig> = PgConfig::class
    actual override val database: PgDatabase
        get() = _database ?: throwUninitialized()

    /**
     * The cluster, set by [initStorage].
     *
     * ### JVM only!
     * @since 3.0.0
     */
    val cluster: PgCluster
        get() = _cluster ?: throwUninitialized()

    /**
     * The name of the notification channel used.
     *
     * - Will throw [UNINITIALIZED][naksha.base.NakshaError.UNINITIALIZED] if read before [initStorage].
     * - Will throw [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE] if change after [initStorage]
     *
     * ### JVM only!
     * @since 3.0
     */
    val channel: String
        get() = _channel ?: throwUninitialized()

    /**
     * When [initStorage] is invoked without that an existing valid master-URI was given, a docker container is started on demand.
     *
     * ### Note
     * Must be set to _true_ before invoking [initStorage]. If that is not possible, please consider simply using [PsqlTestStorage] as class in the config, which automatically enables this option.
     *
     * ### JVM only!
     * @since 3.0
     */
    var runDocker: Boolean = false

    /**
     * Fluent setter for [runDocker].
     * ### JVM only!
     * @param enable the value to set [runDocker] to.
     * @return this.
     * @since 3.0
     */
    fun withDocker(enable: Boolean): PgStorage {
        this.runDocker = enable
        return this
    }

    /**
     * Initializes the storage.
     *
     * If necessary, this method will create the `naksha~admin` schema, install needed SQL scripts, extensions, and do all other initialization works. If the storage is already initialized, the given storage-identifier, and storage-number, must match the existing ones, otherwise an [STORAGE_ID_MISMATCH][naksha.base.NakshaError.STORAGE_ID_MISMATCH] exception is raised.
     *
     * ### Jvm Specifics
     * This is the JVM variant, which has some minor special actions being executed:
     * - If there is a config available in the environment _(TBD)_, reads this and uses it.
     * - If there is an environment variables `NAKSHA_TEST_PSQL_DB_URL` set, reads the master-URI from this.
     * - If there is no enviornment variable, but a system property `naksha.test.psql.db.url` set _(`-Dnaksha.test.psql.db.url=...`)_, then reads the master-URI from this.
     * - Finally, if the config does not contain a master-URI and [runDocker] is _true_, starts a docker container and patches the config to use this docker.
     *
     * Therefore, when [runDocker] is _true_, an empyt configuration can be provided.
     *
     * - Throws [INITIALIZATION_FAILED][naksha.base.NakshaError.INITIALIZATION_FAILED], if the initialization failed.
     *      * - Throws [STORAGE_ID_MISMATCH][naksha.base.NakshaError.STORAGE_ID_MISMATCH], if the existing _storage-id_ and/or _storage-number_ of the data does not match the given ones in the configuration.
     *      * - Throws [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if the storage is already initialized.
     *      * - Throws [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if any configuration entry is invalid.
     * @param config the storage configuration as required.
     * @param create if not `null`, overrides [NakshaStorage.create].
     * @param upgrade if not `null`, overrides [NakshaStorage.upgrade].
     * @since 3.0
     */
    actual override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        lock.acquire().use {
            if (_cluster != null) throw illegalState("${FAL}Storage '$id' is already initialized")

            val _config = getPgConfigFromEnv() ?: config
            getPgInstanceConfigFromEnv(_config)
            if (runDocker && _config.getMasterOrNull() == null) getOrStartDocker(_config)

            if (_config.getMasterOrNull() == null) {
                throw illegalArg("Invalid configuration, neither master is specified nor provided in environment variables")
            }

            // Note: We need to initialize cluster first, so that newConnection and adminConnection calls work.
            //       The PsqlAdminMap will use connections!
            logger.info("Create cluster for storage '$id'")
            val master = PgInstance.get(_config.masterUri)
            val replicas = mutableListOf<PgInstance>()
            for (replicaUri in _config.replicaUris) {
                if (replicaUri == null) continue
                val replica = PgInstance.get(replicaUri)
                if (!replicas.contains(replica)) replicas.add(replica)
            }
            _cluster = PgCluster(master, replicas)
            val db = PgDatabaseImpl(this, this.id)
            db.init(
                doOverride = _config.override ?: false,
                doCreate = create ?: _config.create,
                doUpgrade = upgrade ?: _config.upgrade
            )
            _database = db
        }
    }

    actual override fun afterInit() {
    }

    actual override fun shutdownStorage(dropCache: Boolean) {
    }

    actual override fun newWriteSession(options: SessionOptions?): IWriteSession
        = newSession(options ?: SessionOptions(), readOnly = false)

    actual override fun newReadSession(options: SessionOptions?): IWriteSession
        = newSession(options ?: SessionOptions(), readOnly = true)

    actual override fun newStreamSession(options: SessionOptions?): IStreamSession {
        throw unsupportedOp("newStreamSession")
    }

    actual open fun newSession(options: SessionOptions, readOnly: Boolean): PgSession {
        useInitialized()
        return PgSession(this, options, readOnly)
    }

    actual open fun newConnection(options: SessionOptions, readOnly: Boolean, init: Fx2<PgConnection, String>?): PgConnection {
        return cluster.newConnection(options, readOnly, init)
    }

    /**
     * Drops all schemas, including the `naksha~admin` schema, so that tests can use a fresh database.
     *
     * The method synchronizes on the given name and ensures that the drop is only executed exactly ones for the given name. Only the first call will actually perform the drop, all other calls will just wait until the drop is done.
     *
     * ### Warning
     * This is only available in the JVM implementation!
     * @param name the unique name of the drop action, ensures that the drop is only executed ones.
     * @return _true_ if the drop was executed: _false_ if already done for the given name.
     * @since 3.0
     */
    fun dropStorageOnes(name: String): Boolean {
        while (true) {
            val state = dropCache[name]
            if (state == DROP_DONE) {
                return false
            }
            if (state == DROP_STARTED) {
                Thread.yield()
                continue
            }
            if (state != null) {
                throw illegalState("${FAL}Found state $state, this is invalid")
            }
            if (dropCache.putIfAbsent(name, DROP_STARTED) == null) {
                // Only one thread will succeed.
                try {
                    cluster.master.openConnection(Naksha.useAdminOptions(), readOnly = false, null).use { conn ->
                        // Use an advisory lock (key=12345678) to serialize schema drops across
                        // parallel JVM instances that may run concurrently in Gradle.
                        conn.jdbc.createStatement().use { stmt ->
                            stmt.execute("SET lock_timeout = '120s'")
                            stmt.execute("SELECT pg_advisory_lock(12345678)")
                        }
                        try {
                            // Find and drop ALL user-created schemas so that stale table naming
                            // (e.g. old history partition names from prior code versions) is fully removed.
                            // Keep only PostgreSQL/PostGIS/extension system schemas.
                            val systemSchemas = setOf(
                                "public", "pg_catalog", "information_schema", // default and internal schema
                                "topology", // postgis_topology
                                "hint_plan", // pg_hint_plan
                                "tiger", "tiger_data", // ?
                                "cron", // pg_cron
                                "partman" // pg_partman
                            )
                            val schemas = mutableListOf<String>()
                            conn.jdbc.createStatement().use { stmt ->
                                val rs = stmt.executeQuery(
                                    "SELECT nspname FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' ORDER BY nspname"
                                )
                                while (rs.next()) {
                                    val name = rs.getString(1)
                                    if (name !in systemSchemas) schemas.add(name)
                                }
                            }
                            for (schema in schemas) {
                                try {
                                    conn.jdbc.createStatement().use { stmt ->
                                        stmt.execute("""DROP SCHEMA IF EXISTS "$schema" CASCADE""")
                                    }
                                } catch (e: Exception) {
                                    logger.info("Test setup: skipping schema '{}' ({})", schema, e.message)
                                }
                            }
                            logger.info("Test setup: dropped Naksha schemas for clean recreation: $schemas")
                        } finally {
                            conn.jdbc.createStatement().use { stmt ->
                                stmt.execute("SELECT pg_advisory_unlock(12345678)")
                            }
                        }
                    }
                } finally {
                    dropCache[name] = DROP_DONE
                }
                return true
            }
            // Concurrent updates, repeat.
        }
    }

    actual companion object PgStorageCompanion {
        @JvmStatic
        actual val SKIP_INIT: Fx2<PgConnection, String> = Fx2 { _, _ -> }

        private const val DROP_STARTED = "started"
        private const val DROP_DONE = "done"

        /**
         * Tracks whether the one-time schema drop has already been performed in this JVM. Subsequent calls to [dropStorageOnes] skip the destructive drop so they don't wipe schemas used by other tests.
         * @since 3.0
         */
        private val dropCache = AtomicMap<String, String>()

        private fun architecture(): String {
            val os = System.getProperty("os.name")
            if (os == "Mac OS X") {
                return "arm64"
            }

            val arch = System.getProperty("os.arch")
            return if (arch == "x86_64" || arch == "amd64") {
                "amd64"
            } else {
                "arm64"
            }
        }

        internal const val POSTGRES_IMAGE_URI = "ghcr.io/naksha-oss/naksha-postgres:v18.6"
        internal val dockerContainerInfo = AtomicReference<PgDockerContainerInfo?>()

        /**
         * Tries to extract the **master** instance configuration from the environment for injection into [PgConfig].
         *
         * Tests:
         * - `NAKSHA_TEST_PSQL_DB_URL` — From environment variables.
         * - `naksha.test.psql.db.url` — From JVM properties set via `-Dnaksha.test.psql.db.url=...` when JVM is started.
         *
         * If a config was provided and the method returns a detected instance configuration, then the config was patched; otherwise the config was left alone. This allows to read a default config from resources, then to override it from environment, when set accordingly.
         * @param config if not `null` and an instance was detected, it is set as master into the given configuration, replicas are cleared.
         * @return if the environment configures an instance to be used; `null` otherwise.
         * @since 3.0
         */
        @JvmOverloads
        @JvmStatic
        fun getPgInstanceConfigFromEnv(config: PgConfig? = null): PgInstanceConfig? {
            // - read from environment
            var uri = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            // - read from system (for Storage-API support)
            if (uri == null || uri.isEmpty()) {
                uri = System.getProperty("naksha.test.psql.db.url")
            }
            if (uri != null && uri.isNotEmpty()) {
                try {
                    val instance = PgInstanceConfig.fromUri(uri)
                    if (config != null) {
                        config.master = instance
                        config.replicas.clear()
                    }
                    return instance
                } catch (e: Exception) {
                    logger.warn("The URI for the database in the environment is invalid: $uri", e)
                }
            }
            return null
        }

        /**
         * Tries to find the PostgresQL configuration in the environment.
         * @return if the environment provides a full configures, the configuration found; `null` otherwise.
         * @since 3.0
         */
        @JvmStatic
        fun getPgConfigFromEnv(): PgConfig? {
            // TODO: We want to check home directory for configurations.
            return null
        }

        /**
         * Returns the configuration of a potentially running PostgresQL docker test container.
         * @return the configuration of a running PostgresQL docker test container; `null` if no docker is running.
         * @since 3.0
         */
        fun getDocker(): PgDockerContainerInfo? = dockerContainerInfo.get()

        /**
         * Starts a local PostgresQL docker container and returns the configuration for it.
         *
         * If the docker container is already running, the method just returns the configuration of the running container.
         * @param config if not `null`, the config will be patched to use the docker container.
         * @return the [PgDockerContainerInfo] for the started PostgresQL docker container.
         * @since 3.0
         */
        @JvmOverloads
        @JvmStatic
        fun getOrStartDocker(config: PgConfig? = null): PgDockerContainerInfo {
            var containerInfo: PgDockerContainerInfo? = null
            try {
                // If there is container running, use it.
                containerInfo = dockerContainerInfo.get()
                if (containerInfo != null) return containerInfo

                // Otherwise, start a docker container.
                val db = PgInstanceConfig.DEFAULT_DB
                val user = PgInstanceConfig.DEFAULT_USER
                val password = PgInstanceConfig.DEFAULT_PASSWORD
                val container = GenericContainer(POSTGRES_IMAGE_URI)
                container.portBindings = listOf("15432:5432") // host : container
                container.addEnv("PGPASSWORD", password)
                container.setWaitStrategy(
                    LogMessageWaitStrategy()
                        .withRegEx(".*Future log output will appear in directory.*")
                        .withTimes(2)
                        .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))
                )
                container.start()
                val port = container.getMappedPort(5432)
                logger.info("Docker container listening on port {}", port)
                val instanceConfig = PgInstanceConfig().withDb(db).withUser(user).withPassword(password).withPort(port)
                containerInfo = PgDockerContainerInfo(container, instanceConfig, Thread(::shutdownDocker))
                dockerContainerInfo.set(containerInfo)
                Runtime.getRuntime().addShutdownHook(containerInfo.shutdownThread)
                return containerInfo
            } finally {
                if (config != null && containerInfo != null) {
                    config.master = containerInfo.config
                    config.replicas.clear()
                }
            }
        }

        /**
         * Tries to shut down a currently running docker container.
         * @return _true_ if a container was running and terminated; _false_ if either no container is running or shutdown failed.
         * @since 3.0
         */
        @JvmStatic
        fun shutdownDocker(): Boolean {
            val containerInfo = dockerContainerInfo.get()
            if (containerInfo != null && dockerContainerInfo.compareAndSet(containerInfo, null)) {
                try {
                    // Add a breakpoint here, when you want to query the database after the test.
                    val port = containerInfo.container.getMappedPort(5432)
                    logger.info("Shutdown docker container listening on port {}", port)
                    containerInfo.container.stop()
                    return true
                } catch (e: Exception) {
                    logger.info("Failed to shutdown docker container", e)
                }
            }
            return false
        }
    }
}
