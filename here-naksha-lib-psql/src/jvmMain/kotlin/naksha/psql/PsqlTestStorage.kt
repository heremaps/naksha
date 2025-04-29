package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
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

        internal const val POSTGRES_IMAGE_URI = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r4"
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
        super.initStorage(config, create, upgrade)
    }

    override fun newAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PsqlAdminMap {
        return PsqlAdminMap(this, config, create, upgrade)
    }
}