package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

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

        // If prefer and allowed to pull from HCR then "hcr.data.here.com/naksha/postgres:${architecture()}-latest"
        // TODO: use image under company docker hub account "docker.io/heremaps/naksha-postgres:latest"
        internal val POSTGRES_IMAGE_REPO = "hcr.data.here.com/naksha/postgres:${architecture()}-latest"
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
            val container = GenericContainer(POSTGRES_IMAGE_REPO)
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
            val instanceConfig = PgInstanceConfig().withDb(db).withUser(user).withPassword(password)
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

    override fun initAdminMap(config: PgConfig, create: Boolean?, upgrade: Boolean?): PgAdminMap {
        var master = config.getMasterOrNull()
        if (master == null) {
            // - read from environment
            val uri = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
            if (uri != null && uri.isNotEmpty()) master = PgInstanceConfig.fromUri(uri)
            // - otherwise start a docker container
            if (master == null) master = startDocker()
            config.master = master
        }
        return PsqlAdminMap(this, config, create, upgrade)
    }
}