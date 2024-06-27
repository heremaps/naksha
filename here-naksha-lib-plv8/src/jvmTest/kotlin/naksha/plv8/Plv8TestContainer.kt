import naksha.plv8.PsqlCluster
import naksha.plv8.PsqlInstance
import naksha.plv8.PsqlStorage
import naksha.model.NakshaContext
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Start and shutdown a test container.
 */

class Plv8TestContainer : BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    companion object {
        private val logger = LoggerFactory.getLogger(Plv8TestContainer::class.java)
        lateinit var postgreSQLContainer: GenericContainer<*>
        private var existingUrl: String? = null
        lateinit var url: String
        lateinit var schema: String
        val context = NakshaContext.newInstance("plv8_test", "pvl8_author", true)
        private val _storage = AtomicReference<PsqlStorage?>()
        val storage: PsqlStorage
            get() {
                var s = _storage.get()
                if (s == null) {
                    s = PsqlStorage("test", PsqlCluster(PsqlInstance.get(url)), schema)
                    if (!_storage.compareAndSet(null, s)) {
                        s = _storage.get()
                        check(s != null)
                    }
                }
                return s
            }
        var initialized = false
    }

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

    override fun beforeAll(context: ExtensionContext?) {
        if (initialized) return

        // Run docker locally with env parameter: POSTGRES_PASSWORD=password
        // NAKSHA_TEST_PSQL_DB_URL=jdbc:postgresql://localhost:5400/postgres?user=postgres&password=password&schema=test_schema
        existingUrl = System.getenv("NAKSHA_TEST_PSQL_DB_URL")
        schema = "unit-tests-schema"
        if (existingUrl != null) {
            url = existingUrl!!
            // TODO: Parse the url to extract the schema!
        } else {
            val password = "password"
            postgreSQLContainer = GenericContainer("hcr.data.here.com/naksha-devops/naksha-postgres:${architecture()}-v16.2-r1")
                    .withExposedPorts(5432)
            postgreSQLContainer.addEnv("PGPASSWORD", password)
            postgreSQLContainer.setWaitStrategy(LogMessageWaitStrategy()
                    .withRegEx("Start postgres.*")
                    .withTimes(2)
                    .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)))
            postgreSQLContainer.start()
            Thread.sleep(1000) // give it 1s more before connect

            val port = postgreSQLContainer.getMappedPort(5432)
            logger.info("Database listening on port {}", port)
            url = "jdbc:postgresql://localhost:$port/postgres?user=postgres&password=$password"
        }

        initialized = true
    }

    override fun close() {
        if (existingUrl == null) {
            // Add a breakpoint here, when you want to query the database after the test.
            val port = postgreSQLContainer.getMappedPort(5432)
            logger.info("Database listening on port {}", port)
            postgreSQLContainer.stop()
        }
    }
}
