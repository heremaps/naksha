package naksha.psql

import naksha.base.Platform.PlatformCompanion.logger
import naksha.psql.PgPlatform.PgPlatformCompanion.TEST_URL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A special storage that optionally starts an own docker container.
 */
@Suppress("MemberVisibilityCanBePrivate")
class PsqlTestStorage private constructor(cluster: PsqlCluster) : PsqlStorage(cluster, optionsRef.get()) {

    internal data class DockerContainerInfo(
        val container: GenericContainer<*>,
        val psqlInstance: PsqlInstance,
        val shutdownThread: Thread
    )

    companion object {
        const val DEFAULT_APP_NAME = "naksha.psql.testApp"
        const val DEFAULT_APP_ID = "naksha.psql.testAppId"
        const val DEFAULT_APP_AUTHOR = "naksha.psql.testAuthor"
        const val DEFAULT_SCHEMA = "naksha_psql_test"

        @JvmField
        internal val storage = AtomicReference<PsqlTestStorage?>()

        @JvmField
        internal val DEFAULT_OPTIONS = PgOptions(DEFAULT_APP_NAME, DEFAULT_SCHEMA, DEFAULT_APP_ID, DEFAULT_APP_AUTHOR)

        /**
         * The default [PgOptions], used when creating a new test-storage.
         */
        @JvmField
        val optionsRef = AtomicReference(DEFAULT_OPTIONS)

        internal val dockerContainerInfo = AtomicReference<DockerContainerInfo?>()

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

        @JvmStatic
        internal fun newTestStorage(options: PgOptions = DEFAULT_OPTIONS, params: Map<String, *>? = null): PsqlTestStorage {
            storage.set(null)
            return getTestOrInitStorage(options, params)
        }

        @JvmStatic
        internal fun getTestOrInitStorage(options: PgOptions = DEFAULT_OPTIONS, params: Map<String, *>? = null): PsqlTestStorage {
            var testStorage: PsqlTestStorage? = storage.get()
            while (testStorage == null) {
                optionsRef.set(options)

                // Process parameters and environment variables to modify other defaults.
                var url: String? = null
                if (params != null && params.containsKey(TEST_URL)) {
                    val raw = params[TEST_URL]
                    require(raw is String) { "PgUtil.initTestStorage: params.$TEST_URL must be a string" }
                    url = raw
                }
                if (url == null) url = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
                var psqlInstance: PsqlInstance? = null
                if (url != null) {
                    val urlParams = url.substring(url.indexOf("?"), url.length)
                        .split("&")
                        .map { it.split("=") }
                        .groupBy({ it[0] }, { it[1] })
                    if (urlParams.containsKey("schema")) {
                        val raw = urlParams["schema"]
                        require(raw != null && raw.size == 1) { "URL parameter 'schema' is invalid" }
                        val schema = raw[0]
                        if (schema.isNotEmpty()) optionsRef.set(optionsRef.get().copy(schema = schema))
                    }
                    val app_id: String? = urlParams["appId"]?.get(0) ?: urlParams["app_id"]?.get(0)
                    if (app_id != null) optionsRef.set(optionsRef.get().copy(appId = app_id))
                    val author: String? = urlParams["author"]?.get(0)
                    if (author != null) optionsRef.set(optionsRef.get().copy(author = author))
                    psqlInstance = PsqlInstance.get(url)
                }
                if (psqlInstance == null) {
                    // If there is container running, use it.
                    var containerInfo = dockerContainerInfo.get()
                    if (containerInfo != null) {
                        psqlInstance = containerInfo.psqlInstance
                    } else {
                        // Otherwise, start a docker container.
                        val password = "password"
                        val container = GenericContainer("hcr.data.here.com/naksha/postgres:${architecture()}-latest")
                            .withExposedPorts(5432)
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
                        url = "jdbc:postgresql://localhost:${port}/postgres?user=postgres&password=${password}"
                        psqlInstance = PsqlInstance.get(url)
                        containerInfo = DockerContainerInfo(container, psqlInstance, Thread(::shutdownDocker))
                        dockerContainerInfo.set(containerInfo)
                        Runtime.getRuntime().addShutdownHook(containerInfo.shutdownThread)
                    }
                }
                testStorage = PsqlTestStorage(PsqlCluster(psqlInstance))
                // The initialization is only successful, when there is still no existing storage.
                if (!storage.compareAndSet(null, testStorage)) {
                    testStorage = null
                }
            }
            return testStorage
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

    override fun close() {
        storage.compareAndSet(this, null)
        try {
            super.close()
        } catch (e: Exception) {
            logger.info("Error while trying to close the test storage: {}", e)
        }
    }
}