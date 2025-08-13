package naksha.psql

import naksha.base.Platform
import naksha.base.Platform.Platform_C.logger
import naksha.base.Platform.Platform_C.md5
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

/**
 * The class to be used as `className` when running local tests.
 *
 * If the given configuration contains a `master` configuration, it behaves exactly as [PsqlStorage]. However, if this is not the case, it will perform the following steps in order:
 * - If there is an environment variables with the `id` set, it reads the master-URI from this.
 * - If there is a system property with the `id` is set _(e.g. `-Dnaksha_test_db=...`)_, then it reads the master-URI from this.
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

        private val lock = ReentrantLock()
        private const val POSTGRES_IMAGE_URI = "ghcr.io/naksha-oss/naksha-postgres:v16.2-r4"
        private val dockerContainerInfo = ConcurrentHashMap<String, PsqlTestDockerContainerInfo>()

        /**
         * Starts a local PostgresQL docker container.
         * @param id The docker container id.
         */
        internal fun startDocker(id: String): PgInstanceConfig {
            lock.lock()
            try {
                // If there is container running, use it.
                var containerInfo = dockerContainerInfo[id]
                if (containerInfo != null) return containerInfo.config

                // Otherwise, start a docker container.
                val db = PgInstanceConfig.DEFAULT_DB
                val user = PgInstanceConfig.DEFAULT_USER
                val password = PgInstanceConfig.DEFAULT_PASSWORD
                val container = GenericContainer(POSTGRES_IMAGE_URI)
                val idHashBytes = md5(id)
                val mappedPort = min(65535, max(2000, (Platform.newDataView(idHashBytes).getInt16(0).toInt())))
                container.portBindings = listOf("$mappedPort:5432") // host : container
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
                containerInfo = PsqlTestDockerContainerInfo(id, port, container, instanceConfig, Thread(::shutdownDocker))
                dockerContainerInfo[id] = containerInfo
                Runtime.getRuntime().addShutdownHook(containerInfo.shutdownThread)
                return instanceConfig
            } finally {
                lock.unlock()
            }
        }

        internal fun shutdownDocker() {
            lock.lock()
            try {
                val keysEnum = dockerContainerInfo.keys()
                while (keysEnum.hasMoreElements()) {
                    val id = keysEnum.nextElement()
                    val containerInfo = dockerContainerInfo[id]
                    if (containerInfo != null) try {
                        // Add a breakpoint here, when you want to query the database after the test.
                        val port = containerInfo.container.getMappedPort(5432)
                        logger.info("Shutdown docker container listening on port {}", port)
                        containerInfo.container.stop()
                    } catch (e: Exception) {
                        logger.info("Failed to shutdown docker container", e)
                    } finally {
                        dockerContainerInfo.remove(id)
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    override fun initStorage(config: PgConfig, create: Boolean?, upgrade: Boolean?) {
        var master = config.getMasterOrNull()
        if (master == null) {
            val id = config.id
            // - read from environment
            var uri = System.getenv(id)
            // - read from system (for Storage-API support)
            if (uri == null || uri.isEmpty()) {
                uri = System.getProperty(id)
            }
            if (uri != null && uri.isNotEmpty()) master = PgInstanceConfig.fromUri(uri)
            // - otherwise start a docker container
            if (master == null) master = startDocker(id)
            config.master = master
        }
        config.create = true
        config.upgrade = true
        super.initStorage(config, create, upgrade)
    }

    override fun newAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PsqlAdminMap {
        return PsqlAdminMap(this, config, create, upgrade)
    }
}