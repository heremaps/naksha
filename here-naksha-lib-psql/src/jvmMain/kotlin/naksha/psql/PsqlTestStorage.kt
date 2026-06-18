package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.sql.DriverManager
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The class to be used as `className` when running local tests.
 *
 * If the given configuration contains a `master` configuration, it behaves exactly as [PsqlStorage]. However, if this is not the case, it will perform the following steps in order:
 * - If there is an environment variables `NAKSHA_TEST_PSQL_DB_URL` set, it reads the master-URI from this.
 * - If there is a system property `naksha.test.psql.db.url` set _(`-Dnaksha.test.psql.db.url=...`)_, then it reads the master-URI from this.
 * - If neither is available, it will start a docker container, and use this with database `postgres`, user `postgres`, and password `password` _(which is automatically shutdown when the JVM goes down via shutdown hook)_.
 *
 * @since 3.0
 */
@Suppress("unused")
class PsqlTestStorage : PsqlStorage() {
    companion object TestStorage_C {

        /**
         * Tracks whether the one-time schema drop has already been performed in this JVM.
         * Subsequent calls to [initStorage] (e.g. from [Naksha.setupStorage] in upgrade tests)
         * skip the destructive drop so they don't wipe schemas used by other tests.
         */
        private val schemaDropDone = AtomicBoolean(false)

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

        internal const val POSTGRES_IMAGE_URI = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r5"
        internal val dockerContainerInfo = AtomicReference<PsqlTestDockerContainerInfo?>()

        /**
         * Starts a local PostgresQL docker container
         */
        internal fun startDocker(): PgInstanceConfig {
            // If there is container running, use it.
            var containerInfo = dockerContainerInfo.get()
            if (containerInfo != null) return containerInfo.config

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
            containerInfo = PsqlTestDockerContainerInfo(container, instanceConfig, Thread(::shutdownDocker))
            dockerContainerInfo.set(containerInfo)
            Runtime.getRuntime().addShutdownHook(containerInfo.shutdownThread)
            return instanceConfig
        }

        internal fun shutdownDocker() {
            val containerInfo = dockerContainerInfo.get()
            if (containerInfo != null && dockerContainerInfo.compareAndSet(containerInfo, null)) {
                try {
                    // Add a breakpoint here, when you want to query the database after the test.
                    val port = containerInfo.container.getMappedPort(5432)
                    logger.info("Shutdown docker container listening on port {}", port)
                    containerInfo.container.stop()
                } catch (e: Exception) {
                    logger.info("Failed to shutdown docker container", e)
                }
            }
        }
    }

    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        var master = config.getMasterOrNull()
        if (master == null) {
            // - read from environment
            var uri = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            // - read from system (for Storage-API support)
            if (uri == null || uri.isEmpty()) {
                uri = System.getProperty("naksha.test.psql.db.url")
            }
            if (uri != null && uri.isNotEmpty()) master = PgInstanceConfig.fromUri(uri)
            // - otherwise start a docker container
            if (master == null) master = startDocker()
            config.master = master
        }
        config.withCreate(true).withUpgrade(true)
        // For test environments, always recreate naksha~admin from scratch so that any
        // renamed tables (e.g. history partition naming) are recreated with the new naming.
        // Use the original URI string (from env var or docker) to avoid ssl=true being appended by masterUri.
        try {
            val jdbcUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
                ?: System.getProperty("naksha.test.psql.db.url")
                ?: config.masterUri
            // Only perform the destructive schema drop once per JVM lifetime.
            // Subsequent calls (e.g. from UpgradeStorageTest.setupStorage) skip the drop
            // so they don't destroy schemas that other tests are actively using.
            if (schemaDropDone.compareAndSet(false, true)) {
                DriverManager.getConnection(jdbcUrl).use { conn ->
                    // Use an advisory lock (key=12345678) to serialize schema drops across
                    // parallel JVM instances that may run concurrently in Gradle.
                    conn.createStatement().use { stmt ->
                        stmt.execute("SET lock_timeout = '120s'")
                        stmt.execute("SELECT pg_advisory_lock(12345678)")
                    }
                    try {
                    // Find and drop ALL user-created schemas so that stale table naming
                    // (e.g. old history partition names from prior code versions) is fully removed.
                    // Keep only PostgreSQL/PostGIS/extension system schemas.
                    val systemSchemas = setOf(
                        "public", "pg_catalog", "information_schema",
                        "topology", "hint_plan", "tiger", "tiger_data", "cron"
                    )
                    val schemas = mutableListOf<String>()
                    conn.createStatement().use { stmt ->
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
                        conn.createStatement().use { stmt ->
                            stmt.execute("""DROP SCHEMA IF EXISTS "$schema" CASCADE""")
                        }
                    } catch (e: Exception) {
                        logger.info("Test setup: skipping schema '{}' ({})", schema, e.message)
                    }
                }
                        logger.info("Test setup: dropped Naksha schemas for clean recreation: $schemas")
                    } finally {
                        conn.createStatement().use { stmt ->
                            stmt.execute("SELECT pg_advisory_unlock(12345678)")
                        }
                    }
                }
            } else {
                logger.info("Test setup: schema drop already done for this JVM, skipping")
            }
        } catch (e: Exception) {
            logger.warn("Test setup: could not drop Naksha schemas: {}", e.message)
        }
        super.initStorage(config, create, upgrade)
    }

    override fun newAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PsqlAdminCatalog {
        return PsqlAdminCatalog(this, config, create, upgrade)
    }
}